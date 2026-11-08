# 푸시 알림

앱이 FCM 토큰을 서버에 등록하고, 미션 이벤트가 생기면 서버가 FCM으로 휴대폰 알림을 보낸다.
알림 이력은 저장하지 않는다.

## 디바이스 등록·해제 API

로그인 직후, 앱 시작, FCM 토큰 갱신 때마다 등록을 보낸다. 같은 토큰을 다시 보내도 행이 늘지 않고,
다른 계정으로 로그인하면 소유자만 바뀐다. `devicePlatform`은 `ANDROID`, `IOS`, `WEB` 중 하나이며 대소문자를 구분하지 않는다(`Capacitor.getPlatform()` 값을 그대로 보내도 된다).

```http
PUT /api/v1/push/registrations
Authorization: Bearer <access-token>

{"token":"<FCM registration token>","devicePlatform":"IOS"}
```

로그아웃할 때는 access 토큰을 지우기 **전에** 해제를 보낸다. 서버의 인증 로그아웃은 FCM 토큰을 모르기 때문이다.
본인 소유 토큰만 지우며, 없는 토큰이어도 성공한다.

```http
DELETE /api/v1/push/registrations
Authorization: Bearer <access-token>

{"token":"<FCM registration token>"}
```

토큰 원문은 AES로 암호화해 저장하고, 중복 검사는 HMAC `token_hash`로 한다.

## 보내는 알림

| 알림 | 받는 사람 | 발행 시점 | data |
| --- | --- | --- | --- |
| 미션 완료 인증 요청 | 같은 가족의 부모 전원 | 자녀가 완료 인증을 요청 | `type=MISSION_COMPLETION_REQUESTED`, `missionId` |

앱은 알림을 누르면 `data.type`과 `missionId`로 화면을 연다.

### 미션 서비스 연결 방법

미션 서비스가 완료 요청을 저장하는 트랜잭션 안에서 이벤트를 발행한다. 커밋된 뒤에만 푸시가 나가고,
롤백되면 나가지 않는다. 전송은 별도 스레드(`pushExecutor`)에서 해 요청 응답을 늦추지 않는다.

```java
eventPublisher.publishEvent(new MissionCompletionRequestedEvent(mission.getId(), childUserId, mission.getTitle()));
```

### 새 알림 추가 방법

1. `mission/event`에 이벤트 record를 만든다.
2. `PushNotification`에 문구를 만드는 정적 메서드를 추가하고, `NotificationType`에 값을 추가한다.
3. `MissionPushListener`에 이벤트를 받는 메서드를 추가하고, 받는 사람의 user ID를 구해 `PushService.sendToUsers(userIds, notification)`를 호출한다.
   - 부모 → 자녀(예: 승인·반려)는 이벤트에 담긴 자녀 ID를 그대로 넘기면 된다.
   - 자녀 → 부모는 `FamilyService.listParentUserIds(자녀의 familyMemberId)`로 부모를 찾는다.

## 실패 처리

- FCM이 `UNREGISTERED`(앱 삭제 등)라고 답한 토큰만 삭제한다. 일시 장애나 설정 오류로 실패한 토큰은 남긴다.
- 실패한 푸시는 다시 보내지 않는다.

## 서버 설정

| 환경변수 | 설명 |
| --- | --- |
| `FCM_ENABLED` | 기본 `false`. false면 Firebase 키 없이 앱이 뜨고 푸시는 로그만 남긴다 |
| `FCM_SERVICE_ACCOUNT_JSON` | `true`일 때 Firebase 콘솔에서 받은 서비스 계정 JSON 내용 |

## 앱(Capacitor) 준비

1. Firebase 프로젝트에 Android 패키지 ID와 iOS 번들 ID를 등록하고 각 앱에 Firebase 설정 파일을 넣는다.
2. iOS는 APNs 인증 키(.p8)를 Firebase 콘솔에 올리고 Xcode에서 Push Notifications를 켠다.
3. iOS에서 기본 `@capacitor/push-notifications`가 주는 값은 APNs 토큰이다.
   서버에는 Firebase Messaging 연동으로 얻은 **FCM 토큰**을 보내야 한다.
4. Android 13 이상은 알림 권한을 요청한다.
