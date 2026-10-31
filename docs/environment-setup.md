# 환경 설정 및 암호화 가이드

Java 17을 사용한다. `application.properties`의 DB `ENC(REPLACE_...)`는 실제 암호문이 아닌
자리표시자이므로 실행 전에 교체해야 한다. 로컬 설정은 커밋하지 않는
`src/main/resources/application-local.properties`에 작성한다.

## 환경변수

| 환경변수 | 형식과 용도 | 필요 시점 |
| --- | --- | --- |
| `JASYPT_ENCRYPTOR_PASSWORD` | 설정 암호화/복호화 비밀번호. 환경별로 분리하며 프로퍼티나 `-D`로 대체할 수 없다. | 모든 애플리케이션 시작, 암호화 도구 |
| `AES_ENCRYPTION_KEY` | Base64로 인코딩한 무작위 32바이트 AES v1 키 | 모든 애플리케이션 시작 |
| `HMAC_SOCIAL_ID_KEY` | Base64 32바이트 소셜 ID 조회 키 | 모든 애플리케이션 시작 |
| `HMAC_DEVICE_TOKEN_KEY` | Base64 32바이트 디바이스 토큰 중복 검사 키 | 모든 애플리케이션 시작 |
| `JWT_SECRET` | 별도 Base64 서명 키. 후속 jjwt 구현에서 검증하며 HS256 이상에 맞는 충분한 길이를 사용한다. | 인증 브랜치 |
| `AES_ACTIVE_VERSION` | 기본 `v1`. 교체 시 새 버전과 키 맵을 함께 설정한다. | 키 교체 |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_DATABASE` | 기본 `localhost`, `6379`, `0` | 인증 브랜치 |
| `REDIS_USERNAME`, `REDIS_PASSWORD`, `REDIS_SSL_ENABLED` | 기본 빈 값/빈 값/`false`. 비밀번호는 `ENC(...)`로 주입한다. | 인증 Redis에 필요할 때 |
| `JWT_ISSUER` | 기본 `oogachaka` | issuer 변경 |
| `GOOGLE_OAUTH_ENABLED`, `KAKAO_OAUTH_ENABLED`, `NAVER_OAUTH_ENABLED` | 기본 `false`. 사용할 제공자만 활성화한다. | 인증 브랜치 |
| `{GOOGLE,KAKAO,NAVER}_CLIENT_ID` | 제공자 콘솔에서 발급한 client ID. 카카오는 REST API 키 | 제공자 활성화 |
| `{GOOGLE,KAKAO,NAVER}_CLIENT_SECRET` | 각 제공자 secret을 암호화한 `ENC(...)`. 카카오에서 secret을 사용하지 않으면 비워 둔다. | 제공자 활성화 |
| `{GOOGLE,KAKAO,NAVER}_REDIRECT_URI` | 제공자 콘솔에 등록한 프런트 콜백 URI와 정확히 일치 | 제공자 활성화 |
| `CORS_ALLOWED_ORIGINS` | 쉼표로 구분한 명시적 origin. 기본 Capacitor `capacitor://localhost`, `http://localhost`, `https://localhost` | 웹/앱 환경에 맞게 설정 |
| `FCM_ENABLED`, `FCM_PROJECT_ID` | 기본 `false`/빈 값. false이면 후속 구현이 Firebase 초기화를 생략한다. | 푸시 브랜치 |
| `FCM_SERVICE_ACCOUNT_PATH` | 저장소 밖의 서비스 계정 JSON 절대경로 | 파일 방식의 FCM 인증 |
| `FCM_SERVICE_ACCOUNT_JSON` | JSON 한 줄 또는 그 값을 암호화한 `ENC(...)` | 내용 주입 방식의 FCM 인증 |
| `GOOGLE_APPLICATION_CREDENTIALS` | 위 두 FCM 설정이 비어 있을 때 Google ADC에서 읽는 외부 JSON 파일 경로 | ADC 인증 |

AES 키와 두 HMAC 키는 디코딩 결과가 모두 서로 달라야 한다. 이전 AES 버전의 키도 HMAC 키와
같으면 시작에 실패한다. 키 누락, 잘못된 Base64, 32바이트가 아닌 값도 시작에 실패한다.
JWT 서명 키와 Jasypt 비밀번호까지 별도로 생성·보관한다. 로컬·테스트·운영의 키를 공유하지 않는다.

## 키 생성과 Jasypt 비밀번호

각 키마다 다음 명령을 별도로 실행하여 안전한 개인 저장소나 Secrets에 보관한다.
명령 출력은 실제 키이므로 소스/문서/로그에 붙여 넣지 않는다.

```sh
openssl rand -base64 32
```

AES/HMAC/JWT 키는 환경변수에 원래의 Base64 키를 넣는다. 이 키들을 Jasypt로 감싸지 않는다.
Jasypt는 DB 비밀번호, OAuth secret 등 외부 설정 비밀값을 보호한다.

zsh에서 비밀번호를 이력에 남기지 않고 읽는 예시:

```sh
read -rs 'JASYPT_ENCRYPTOR_PASSWORD?Jasypt 비밀번호: '
export JASYPT_ENCRYPTOR_PASSWORD
printf '\n'
```

환경변수를 파일에 보관한다면 Git에서 제외된 파일만 사용한다. Spring Boot는 `.env`를 자동으로
읽지 않으므로 셸이나 IDE가 실제 프로세스 환경변수로 내보내야 한다.

## 설정값 암호화

암호화 도구는 애플리케이션/DB/Flyway를 시작하지 않는다. 아래에서 요구하는 환경변수는
`JASYPT_ENCRYPTOR_PASSWORD`뿐이다. 이 작업 환경의 Gradle 실행 예시:

```sh
export JAVA_HOME=/Users/baegdohyeon/Library/Java/JavaVirtualMachines/corretto-17.0.11/Contents/Home
read -rs 'JASYPT_INPUT?암호화할 값 한 줄: '
printf '\n'
printf '%s\n' "$JASYPT_INPUT" | ./gradlew -q --console=plain jasyptEncrypt
unset JASYPT_INPUT
```

출력된 `ENC(...)` 전체를 설정값에 붙여 넣는다. URL, username, password는 각각 암호화한다.
OAuth secret과 Redis 비밀번호, 내용으로 주입하는 서비스 계정 JSON도 같은 방식이다.
여러 줄 JSON은 한 줄 JSON으로 변환한 뒤 입력한다. `-Pvalue` 입력은 셸 이력과 프로세스 인자에
남으므로 예제/더미 값 확인에만 사용한다.

```sh
./gradlew -q jasyptEncrypt -Pvalue=TEST_ONLY_DUMMY_VALUE
```

규격은 `PBEWITHHMACSHA512ANDAES_256`, 키 유도 10,000회, 무작위 salt/IV, Base64다.
`JasyptEncryptorFactory`를 CLI와 `jasyptStringEncryptor` 빈이 공유한다. 알고리즘 설정은 이
팩토리에 고정되어 있다. 기존 다른 규격의 암호문은 이 도구로 다시 암호화한다.
암호문을 만드는 환경과 애플리케이션 환경의 비밀번호가 같아야 한다.

## 로컬 실행

```sh
cp src/main/resources/application-local.properties.example src/main/resources/application-local.properties
```

예제 파일의 DB 자리표시자를 **로컬 MySQL** 접속 정보를 암호화한 값으로 교체한다.
제공자를 활성화할 때 해당 제공자의 예제 자리표시자도 모두 교체한다. 사용하지 않는 제공자는
false로 두고 secret 자리표시자 줄을 삭제하거나 빈 값으로 둔다.
필수 환경변수를 셸에 주입한 뒤 실행한다.

```sh
export SPRING_PROFILES_ACTIVE=local
./gradlew bootRun
```

앱 시작은 Flyway를 실행한다. V2는 빈 `device_token` 테이블을 전제로 한다. 기존 행이 있으면
NOT NULL/UNIQUE 추가가 실패할 수 있으므로 적용 전에 별도 백필·중복 정리를 준비한다.
소셜 기존 값도 현재 HMAC 키의 소문자 hex 64자리인지 확인한다. 마이그레이션은 자동으로
평문을 HMAC으로 변환하지 않는다. 미병합 SQL을 공용 DB에 적용하지 않는다.

IntelliJ에서는 `Run > Edit Configurations > OogachakaApplication`의 JRE를 Java 17로,
Active profiles를 `local`로 지정한다. `Environment variables` 편집 창에 필수 변수들을 추가한다.
Gradle 실행 구성도 동일하게 환경변수를 넣고 Gradle JVM을 Java 17로 지정한다.
개인 실행 구성은 `.idea` 아래에 보관하고 비밀값을 담은 공유 실행 구성 파일은 커밋하지 않는다.

## 후속 브랜치 설정 계약

현재 브랜치는 Redis/jjwt/Firebase 의존성과 실제 인증·푸시 실행기를 추가하지 않는다.
후속 구현은 `application.properties`의 키를 그대로 `@ConfigurationProperties`로 바인딩한다.

| 설정 접두어 | 계약 |
| --- | --- |
| `app.jwt` | `secret`, `access-token-ttl=30m`, `refresh-token-ttl=14d`, `issuer` |
| `app.oauth` | `state-ttl=5m`(Redis의 일회성 state), `connect-timeout=3s`, `read-timeout=5s` |
| `app.oauth.providers.{google,kakao,naver}` | `enabled`, `client-id`, `client-secret`, `redirect-uri`, `authorization-uri`, `token-uri`, `user-info-uri`, `scopes` |
| `app.cors` | `allowed-origins` 목록, `allow-credentials=true`. 운영 origin과 실제 Capacitor 설정을 일치시킨다. |
| `app.fcm` | `enabled`, `project-id`, `service-account-path`, `service-account-json`. path/json은 동시에 지정하지 않는다. 둘 다 비어 있으면 ADC를 사용한다. |
| `app.async.push` | `core-pool-size=2`, `max-pool-size=4`, `queue-capacity=100`, `thread-name-prefix=push-`, `await-termination=30s` |

OAuth scope는 앱 콘솔에서 승인된 권한에 맞춰 환경별로 조정한다. 네이버 동의 항목은 콘솔에서
관리하므로 기본 scopes는 비어 있다. 각 제공자 정보는
[Google](https://developers.google.com/identity/openid-connect/openid-connect),
[Kakao](https://developers.kakao.com/docs/en/rest-api/reference),
[Naver](https://developers.naver.com/docs/login/api/api.md)의 엔드포인트를 기준으로 했다.
FCM 인증 선택은 [Firebase Admin 설정](https://firebase.google.com/docs/admin/setup)을 따른다.

공통 API 사용 예시:

```java
String socialHash = hmacService.hash(providerUserId, HmacPurpose.SOCIAL_ID);
String tokenHash = hmacService.hash(token, HmacPurpose.DEVICE_TOKEN);
```

원본 UTF-8 문자열을 그대로 사용한다. trim, 대소문자 변경, Unicode 정규화는 하지 않는다.
null은 HMAC 오류다. 조회/저장에 같은 규칙을 적용하고 이미 해시된 값은 다시 해시하지 않는다.
소셜 조회는 제공자와 해시를 함께 사용한다. 디바이스 토큰 원문은 엔티티 필드에 명시적으로
`@Convert(converter = EncryptionConverter.class)`를 적용하고 Service에서 중복 암호화하지 않는다.
`CryptoConfig`가 Hibernate 7의 `SpringBeanContainer`를 연결하여 Converter의 생성자 주입을 지원한다.

AES 저장 규격은 `v1:Base64(IV + 암호문 + 태그)`이며 버전 UTF-8 바이트를 GCM AAD로 인증한다.
원문이 N바이트라면 버전 길이 + 1 + `4 * ceil((12 + N + 16) / 3)` 바이트가 필요하다.
TEXT의 65,535바이트 한계 등 실제 컬럼 한도와 도메인의 입력 길이를 함께 검증한다.

AES 교체 시 기존 `app.crypto.aes-keys.v1`을 유지하고 환경별 로컬 설정에
`app.crypto.aes-keys.v2=${AES_ENCRYPTION_KEY_V2}`를 추가한 뒤 `AES_ACTIVE_VERSION=v2`를 주입한다.
새 값은 v2로 저장되고 기존 값은 접두어의 키로 복호화한다. 재암호화와 백업/복구 확인이 끝나기 전
이전 키를 삭제하지 않는다. HMAC은 원문을 복원할 수 없어 소셜 키 변경 시 계정 식별이 끊길 수 있다.
계정 재연동/데이터 전환을 먼저 계획한다. 디바이스 HMAC 키 변경 시 기존 토큰을 복호화하여
token_hash를 백필하고 전환 중 UNIQUE 검사가 일관되도록 별도 배포 절차를 준비한다.

## CI 환경변수 주입

GitHub 저장소의 `Settings > Secrets and variables > Actions`에 환경별 Secrets를 등록한다.
아래는 컴파일 단계의 주입 예시다. 이 작업에서는 테스트나 DB 마이그레이션을 실행하지 않는다.

```yaml
- uses: actions/setup-java@v6
  with:
    distribution: corretto
    java-version: '17'
- name: Compile
  env:
    JASYPT_ENCRYPTOR_PASSWORD: ${{ secrets.JASYPT_ENCRYPTOR_PASSWORD }}
    AES_ENCRYPTION_KEY: ${{ secrets.AES_ENCRYPTION_KEY }}
    HMAC_SOCIAL_ID_KEY: ${{ secrets.HMAC_SOCIAL_ID_KEY }}
    HMAC_DEVICE_TOKEN_KEY: ${{ secrets.HMAC_DEVICE_TOKEN_KEY }}
    JWT_SECRET: ${{ secrets.JWT_SECRET }}
  run: ./gradlew compileJava compileTestJava
```

컴파일 자체는 비밀값을 요구하지 않는다. 배포된 JVM/컨테이너에도 같은 방식으로 Secrets를 별도
주입해야 한다. GitHub Actions 단계의 환경변수가 배포 서버에 자동 전달되지는 않는다.
서비스 계정 파일은 저장소에 올리지 않고 비밀 파일 마운트/ADC 또는 Secret의 내용 주입을 사용한다.
JDK 설정 예시는 [setup-java 공식 사용법](https://github.com/actions/setup-java)을 따른다.

## 검증 범위와 테스트 실행 준비

검증은 Java 17에서 `./gradlew compileJava compileTestJava`까지만 수행한다.
단위 테스트에는 AES 왕복/IV/변조/잘못된 키/버전 교체/null 및 HMAC 결정성/키 분리/입력 규칙,
설정 바인딩/시작 실패 조건을 작성했다. MySQL Testcontainers 통합 테스트에는 V1/V2,
Converter 생성자 주입·저장/조회·TEXT 용량, 해시 조회·UNIQUE·ascii_bin 검증을 작성했다.
통합 테스트는 공용 DB를 사용하지 않으며 Docker가 없으면 건너뛴다.

추후 테스트 실행이 허용되면 Docker를 켜고 `JASYPT_ENCRYPTOR_PASSWORD`에 테스트 전용 더미
비밀번호를 환경변수로 넣은 뒤 Gradle `test`를 실행한다. `contextLoads`는 test 프로필의 명시적
더미 키와 Testcontainers DB를 사용한다. 본 작업에서는 테스트를 실행하지 않았다.

## Jasypt 선택 근거

2026-11-01 Maven Central metadata의 최신 starter는 **4.0.4**다.
starter JAR에는 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`가
있다. 하위 `jasypt-spring-boot` JAR의 후처리기는 이전
`org.springframework.boot.env.EnvironmentPostProcessor` 키로 등록되지만 Boot 4.1.2가 해당 키와
인터페이스를 호환 어댑터로 지원한다. 이동한 SystemEnvironment 후처리기의 순서 상수는
바이트코드에 인라인되어 있었다. 별도 임시 코드로 Boot 4.1.2 컨텍스트에서 실제 `ENC(...)`
복호화를 확인해 starter를 채택했다. 임시 코드는 저장소에 남기지 않았다.

근거: [Maven Central starter](https://central.sonatype.com/artifact/com.github.ulisesbocchio/jasypt-spring-boot-starter),
[metadata](https://repo.maven.apache.org/maven2/com/github/ulisesbocchio/jasypt-spring-boot-starter/maven-metadata.xml),
[Jasypt 프로젝트](https://github.com/ulisesbocchio/jasypt-spring-boot),
[Boot 환경 후처리기 API](https://docs.spring.io/spring-boot/4.1/api/java/org/springframework/boot/EnvironmentPostProcessor.html).
