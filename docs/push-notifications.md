# 디바이스 등록과 미션 완료 요청 푸시

알림 이력은 저장하지 않는다. 같은 가족의 부모가 등록한 모든 기기에 표시 알림과 딥링크 데이터를
발송한다. 인증은 인증 브랜치의 JWT 필터가 제공하는 `Long userId` principal을 사용한다.
이 브랜치 단독으로는 로그인 API나 미션 완료 인증 제출 API를 제공하지 않는다.

## API

로그인 직후와 앱 재시작/토큰 갱신 때 다음 요청을 보낸다. `token`은 FCM registration token 원문이며
공백 제거·대소문자 변환을 하지 않는다. `devicePlatform`은 `ANDROID`, `IOS`, `WEB` 중 하나다.
토큰은 공백 없는 최대 4,096자로 제한한다. AES-GCM/Base64 확장 후에도 기존 TEXT 용량 안에 들어간다.

```http
PUT /api/v1/push/registrations
Authorization: Bearer <access-token>
Content-Type: application/json

{"token":"<FCM-registration-token>","devicePlatform":"IOS"}
```

동일 토큰은 HMAC `token_hash`로 찾는다. 기존 행이 있으면 소유 사용자/플랫폼/동기화 시각을 갱신하고,
새 토큰이면 생성한다. 같은 기기에서 다른 계정으로 로그인하면 소유권이 이전된다. 반복 요청으로
행이 늘어나지 않는다. 동시 최초 등록은 UNIQUE 제약과 독립된 트랜잭션의 최대 3회 재시도로 처리한다.
계속 충돌하면 HTTP 409 / `PUSH_REGISTRATION_CONFLICT`이므로 클라이언트에서 잠시 뒤 PUT을 다시 보낸다.

로그아웃할 때에는 access token을 폐기하기 **전에** 현재 기기의 등록을 해제한다. 서버의 인증 로그아웃은
기기를 식별할 FCM 토큰을 모르므로 이 API를 별도로 호출한다.

```http
DELETE /api/v1/push/registrations
Authorization: Bearer <access-token>
Content-Type: application/json

{"token":"<FCM-registration-token>"}
```

두 API의 성공 응답은 HTTP 200 / `{"code":"SUCCESS","message":"요청이 성공했습니다.","data":null}`이다.
미등록 토큰의 DELETE도 성공한다. DELETE는 현재 사용자 소유의 토큰만 삭제하므로 계정 전환 전의
오래된 세션이 새 소유자의 등록을 해제할 수 없다. 인증 주체가 없으면 HTTP 401,
입력 누락/잘못된 플랫폼/빈 토큰/길이 초과는 HTTP 400이다. 요청 DTO의 문자열 표현에도 토큰을 넣지 않는다.

토큰 갱신으로 값이 바뀌면 이전 토큰을 기억하고 있을 때 DELETE 후 새 값으로 PUT한다.
이전 값을 모르면 새 값만 등록할 수 있으며, 이전 토큰은 FCM이 무효하다고 응답한 시점에 정리된다.

## 5-44 미션 완료 인증 제출 서비스 연결

미션 도메인 구현 시 `POST /api/v1/missions/{id}/completion-requests`의 쓰기 트랜잭션에서
권한/미션 상태를 검증하고 인증 요청 저장 후 이벤트를 발행한다.

```java
@Transactional
public void requestCompletion(Long missionId, Long childUserId) {
    // 미션 조회, 같은 가족/담당 자녀/요청 가능 상태 검증, 인증 요청 저장
    eventPublisher.publishEvent(new MissionCompletionRequestedEvent(
        missionId, childUserId, mission.getTitle()));
}
```

이 코드는 향후 서비스의 연결 예시이며 현재 실행 가능한 완료 제출 API는 없다.
이벤트의 Javadoc에도 동일한 계약을 남겼다. 롤백되거나 트랜잭션 밖에서 발행된 이벤트는 발송하지 않는다.
리스너는 AFTER_COMMIT 이후 `pushExecutor`에서 동작한다. 가족 조회와 토큰 삭제만 짧은 DB 트랜잭션을
사용하며 Firebase 호출 중에는 DB 트랜잭션을 유지하지 않는다.

수신자 어댑터는 `users.family_member_id → family_member.family_id`로 가족을 찾는다.
발행자 역할이 CHILD이고 같은 가족에서 PARENT인 사용자만 선택한다. JDBC는 사용자 ID와 자녀 닉네임만
읽으며 토큰은 `DeviceToken` JPA 조회와 `EncryptionConverter`를 통해 복호화한다.
`ParentPushRecipientPort`를 향후 가족 도메인의 조회 서비스로 교체할 수 있다.

표시 제목은 `미션 완료 인증 요청`, 본문은 `{자녀 닉네임}님이 '{미션 제목}' 완료 인증을 요청했어요`다.
`data`의 값은 모두 문자열이다.

```json
{"type":"MISSION_COMPLETION_REQUESTED","missionId":"10","childUserId":"3"}
```

Android HIGH priority, APNs alert/priority 10/default sound와 공통 notification payload를 보낸다.
알림 탭 시 type/missionId를 읽어 앱의 미션 인증 검토 화면으로 이동한다. 실제 화면 데이터는 인증된
미션 API로 다시 조회하고 권한을 검증한다.

## Firebase 서버 설정

기본 `FCM_ENABLED=false`에서는 Firebase 초기화/ADC 조회/외부 발송을 하지 않는다.
다른 DB/Jasypt/AES/HMAC 설정은 기존 [환경 설정 가이드](environment-setup.md)가 여전히 필요하다.

실제 발송 시 Firebase 프로젝트를 만들고 Cloud Messaging API 활성화 및 서비스 계정의 발송 권한을
확인한 뒤 `FCM_ENABLED=true`, `FCM_PROJECT_ID=<프로젝트 ID>`를 설정한다.
자격증명은 다음 중 하나를 사용한다. 실제 키는 저장소에 넣지 않는다.

| 설정 | 동작 |
| --- | --- |
| `FCM_SERVICE_ACCOUNT_PATH` | 저장소 밖의 서비스 계정 JSON 파일 경로 |
| `FCM_SERVICE_ACCOUNT_JSON` | 서비스 계정 JSON 내용. 환경변수 또는 Jasypt `ENC(...)`로 주입 |
| 두 값 모두 비어 있음 | Google ADC: `GOOGLE_APPLICATION_CREDENTIALS` 또는 런타임 워크로드 자격증명 |

path와 json을 동시에 지정하면 활성화 시 시작에 실패한다. 비활성화 상태는 자격증명을 읽지 않는다.
연결 제한은 3초, 읽기/쓰기 제한은 각각 10초다. `oogachaka-push` 이름의 FirebaseApp을 생성하며
컨텍스트 종료 시 delete한다. [Firebase Admin 설정](https://firebase.google.com/docs/admin/setup)을 따른다.

`app.async.push.*`는 기존 계약을 그대로 사용한다: core=2/max=4/queue=100, thread-name-prefix=push-,
await-termination=30s. 실행기 포화/종료 시에는 경고를 남기고 작업을 생략한다. 실행기 종료는
Firebase/JPA 종료보다 먼저 수행하며 설정된 시간까지 대기한다.

## 전송 실패 처리와 보장 범위

Firebase Admin **9.11.0**을 사용한다. [Maven Central metadata](https://repo.maven.apache.org/maven2/com/google/firebase/firebase-admin/maven-metadata.xml)와
[공식 릴리스](https://github.com/firebase/firebase-admin-java/releases/tag/v9.11.0)로 최신 안정 버전을 확인했다.
실제 JAR/source의 `sendEach(List<Message>)` 계약에 따라 최대 500개씩 나누고 입력 순서대로 결과를 처리한다.
9.11.0에서 `setToken`은 deprecated이지만 이 API는 FCM registration token을 받는 계약이므로 유지했다.
FID를 받는 API로 바꿀 때에는 클라이언트·저장 데이터·전송 계약을 함께 변경해야 한다.

`UNREGISTERED`, 또는 `INVALID_ARGUMENT`이면서 응답이 등록 토큰 오류를 명시한 경우만 삭제한다.
토큰 오류 표시는 공식 예시의 등록 토큰 오류 문구 또는 `message.token` field violation으로 확인한다.
일반 payload 오류/응답 해석 실패/프로젝트 불일치/자격증명 오류/일시 오류는 토큰을 보존한다.
Firebase의 [오류 코드 문서](https://firebase.google.com/docs/cloud-messaging/error-codes)를 기준으로 했다.
삭제 조건에 발송 당시의 ID·소유자·동기화 시각을 포함해 발송 중 재등록한 행은 삭제하지 않는다.

애플리케이션 차원의 재발송 큐는 두지 않는다. 실제 SDK source의 기본 재시도는 HTTP 503에 최대 4회,
최대 간격 60초이며 이를 그대로 사용한다. 최종 실패는 안전한 오류 코드/유형과 내부 ID만 로깅한다.
토큰·닉네임·미션 제목·서비스 계정 내용·공급자 예외 원문은 로그에 넣지 않는다.
프로세스 종료, 큐 포화, 최종 일시 오류 시에는 알림이 유실될 수 있다. 알림 이력/보장 전송은 이번 범위에 없다.
발송 대상을 조회한 직후 계정이 전환되면 이미 진행 중인 외부 전송까지 취소할 수는 없다.

## 사용자와 Capacitor 클라이언트 준비

1. Firebase 프로젝트에 Android package ID와 iOS bundle ID를 등록하고 각 앱의 Firebase 설정 파일을
   적용한다. 서버 서비스 계정은 앱에 넣지 않는다.
2. 서버용 서비스 계정 JSON을 발급하거나 ADC 권한을 구성하고 위 환경변수를 적용한다.
3. Apple Developer에서 APNs 인증 키(.p8), Key ID, Team ID를 준비하고 Firebase 프로젝트의
   iOS Cloud Messaging 설정에 업로드한다. Xcode의 Push Notifications capability와 앱 서명을 설정한다.
   [Firebase iOS 시작 가이드](https://firebase.google.com/docs/cloud-messaging/ios/get-started)를 참고한다.
4. 프로젝트의 Capacitor major와 맞는 push 플러그인을 설치하고 native sync를 수행한다.
   Android는 `google-services.json`, 런타임 알림 권한(Android 13+), 알림 채널/아이콘을 준비한다.
   iOS에서는 기본 `@capacitor/push-notifications`의 registration 값이 **APNs 토큰**이다.
   서버 PUT에는 Firebase Messaging 네이티브 연동/브리지에서 얻은 **FCM registration token**을 보낸다.
   [Capacitor Token 계약](https://capacitorjs.com/docs/apis/push-notifications#token)과
   [Firebase iOS Messaging API](https://firebase.google.com/docs/reference/swift/firebasemessaging/api/reference/Classes/Messaging)를 확인한다.
5. 알림 권한을 받은 후 등록 리스너/토큰 갱신 리스너를 연결하고 로그인 직후·앱 시작·토큰 갱신마다
   인증된 PUT을 보낸다. 로그아웃에서는 서버 DELETE 성공 후 인증 로그아웃/로컬 토큰 폐기를 진행한다.
6. foreground 표시 옵션과 수신 핸들러를 구현하고, 탭 이벤트에서 data.type/missionId로 화면을 연다.
   [Capacitor 표시·수신 API](https://capacitorjs.com/docs/apis/push-notifications)를 따른다.
7. WEB은 native Capacitor 플러그인 외에 Firebase Web Messaging service worker/권한/VAPID 설정이
   별도로 필요하다. 백엔드는 WEB FCM 토큰도 저장·발송한다.
8. 인증 브랜치를 통합하고 5-44 서비스에 이벤트를 연결한 뒤, 실제 부모 기기들/다른 가족/자녀 기기와
   foreground/background 상태에서 수신 여부를 확인한다.

## 검증

이번 작업에서는 다음 컴파일만 수행한다. 테스트·앱 시작·Flyway·Firebase 실발송은 실행하지 않는다.

```sh
JAVA_HOME=/Users/baegdohyeon/Library/Java/JavaVirtualMachines/corretto-17.0.11/Contents/Home ./gradlew compileJava compileTestJava
```

Mockito 단위 테스트와 MockMvc API 테스트, Spring 이벤트/Async 경계 테스트,
MySQL Testcontainers 가족 조회·Converter·동시 upsert 테스트를 작성했다. 테스트 실행이 허용되면
Docker와 테스트용 `JASYPT_ENCRYPTOR_PASSWORD`를 준비한 뒤 같은 JAVA_HOME에서 Gradle test로 검증한다.
테스트 컴파일 성공은 테스트 통과나 실제 기기 수신 검증을 의미하지 않는다.
