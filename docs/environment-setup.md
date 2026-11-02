# 환경 설정 및 암호화 가이드

Java 17을 사용한다. 처음 세팅한다면 아래 [한눈에 보기](#한눈에-보기)와
[로컬 실행](#로컬-실행)만 순서대로 따라 하면 된다.

현재 `feature/5-65-jasypt-config`에서 준비할 것은 DB·Flyway 접속정보, AES/HMAC 키,
Jasypt 비밀번호다. JWT·Redis·소셜 로그인·FCM·비동기 푸시는 후속 브랜치용 설정이므로
지금 모든 값을 채울 필요는 없다. Redis 설정을 적는 것만으로 Redis 서버가 실행되지는 않으며,
현재 브랜치에는 Redis 연동 의존성과 사용 코드가 없다.

## 한눈에 보기

비밀값은 **어디에 쓰이느냐**에 따라 넣는 곳이 다르다.

| 비밀값 | 로컬 개발 | 서버·CI | Git에 올라가나 |
| --- | --- | --- | --- |
| 내 PC에서 사용할 DB 접속 정보(앱·Flyway) | `application-local.properties`에 평문 | - | 안 올라감 (`.gitignore`) |
| 키 4개 (AES, HMAC 2개, JWT) | `application-local.properties`에 평문 | 환경변수 | 안 올라감 |
| Jasypt 비밀번호 | `application-local.properties`에 평문 | 환경변수 `JASYPT_ENCRYPTOR_PASSWORD` | 안 올라감 |
| 공용 DB 접속 정보(앱·Flyway), 공용 OAuth secret | `application.properties`에 `ENC(...)` | 동일 | **올라감 (암호문)** |

- **Jasypt**는 Git에 올라가는 `application.properties`의 비밀값을 `ENC(...)` 암호문으로 바꿔 두는
  도구다. 앱이 켜질 때 Jasypt 비밀번호로 `ENC(...)`를 풀어 원래 값으로 쓴다. 비밀번호가 없는 사람은
  저장소를 받아도 `ENC(...)` 안의 값을 알 수 없다.
- Jasypt 비밀번호는 어디서 발급받는 값이 아니라 **우리가 정하는 값**이다. `ENC(...)`를 만들 때와
  풀 때 같은 비밀번호를 써야 하므로 팀이 하나를 정해 비공개로 공유한다.
- 공통 파일의 앱·Flyway DB 계정은 모두 `ENC(...)`로 저장한다. Flyway 전용 환경변수를 따로
  요구하지 않으며, Flyway URL은 `spring.datasource.url`을 참조한다.
- `application.properties`의 DB URL, 앱 계정·비밀번호, Flyway 계정·비밀번호에는 공용 값의
  실제 `ENC(...)` 암호문이 반영되어 있다. 팀원은 이를 복호화할 Jasypt 비밀번호를 비공개로 전달받는다.
  접속정보를 교체할 때는 [공용 값 암호화](#공용-값-암호화-applicationproperties)를 따른다.
- `local` 프로필은 내 PC에서 실행할 때 사용할 설정이라는 뜻이다. DB 서버가 반드시 내 PC에
  있어야 한다는 뜻은 아니며, 공용 DB 연결정보도 로컬 파일에 지정할 수 있다.
- 로컬 파일에는 공통 파일과 다른 값만 적는다. 없는 항목은 공통 값을 사용하므로 두 파일의
  전체 항목 수를 맞출 필요는 없다. 같은 항목을 덮어쓸 때는 설정 이름을 동일하게 쓴다.

## 환경변수 (서버·CI)

서버와 CI는 아래 환경변수로 주입한다. 로컬 개발은 환경변수 대신
`application-local.properties`에 같은 값을 적는다([로컬 실행](#로컬-실행) 참고).

현재 앱 시작에 필요한 것은 Jasypt 비밀번호와 AES/HMAC 키다. JWT 및 외부 서비스 관련 변수는
해당 후속 기능을 사용할 때 준비한다. `${변수:기본값}`은 변수를 등록하지 않으면 기본값을 사용하므로,
일반 설정의 기본값이 환경에 맞는다면 모든 환경변수를 따로 등록할 필요는 없다.

| 환경변수 | 형식과 용도 | 필요 시점 |
| --- | --- | --- |
| `JASYPT_ENCRYPTOR_PASSWORD` | 설정 암호화/복호화 비밀번호. 로컬은 `application-local.properties`의 `jasypt.encryptor.password`로 대신한다. `application.properties`에 적으면 시작이 실패한다. | 모든 애플리케이션 시작, 암호화 도구 |
| `AES_ENCRYPTION_KEY` | Base64로 인코딩한 무작위 32바이트 AES v1 키 | 모든 애플리케이션 시작 |
| `HMAC_SOCIAL_ID_KEY` | Base64 32바이트 소셜 ID 조회 키 | 모든 애플리케이션 시작 |
| `HMAC_DEVICE_TOKEN_KEY` | Base64 32바이트 디바이스 토큰 중복 검사 키 | 모든 애플리케이션 시작 |
| `JWT_SECRET` | 독립된 Base64 서명 키. HS256용 최소 32바이트를 시작 시 검증한다. | 인증 브랜치 |
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
JWT 서명 키와 Jasypt 비밀번호도 각각 별도로 생성·보관한다. 독립된 DB와 인증 환경의 키는 분리한다.
**같은 DB의 데이터를 읽고 쓰는 앱은 같은 AES/HMAC 키를, 같은 공통 암호문을 읽는 앱은 같은
Jasypt 비밀번호를 사용해야 한다**(아래 3단계 참고).

운영의 Jasypt 비밀번호는 공통 properties에 적지 않고 `JASYPT_ENCRYPTOR_PASSWORD`로 주입한다.
앱은 `JasyptConfig`의 암호화 빈을 생성할 때 `JasyptEncryptorFactory.requirePassword()`로 검증한다.
공통 `application.properties`에 비밀번호를 정의했는지 먼저 검사한 뒤 Spring의
`Environment.getProperty("jasypt.encryptor.password")`로 조회한다. Spring의 설정 조회 기능이
대문자·밑줄 형태인 `JASYPT_ENCRYPTOR_PASSWORD`도 찾으므로, 공통 파일에 비밀번호 항목이 없어도
환경변수로 값을 전달할 수 있다. 이 값은 공통 `ENC(...)`를 만들 때 사용한 비밀번호와 같아야 한다.

## 로컬 실행

처음 한 번만 1~3단계를 하고, 이후에는 4단계(실행)만 하면 된다.
**IDE 실행 설정이나 셸에 환경변수를 넣을 필요가 없다.** 로컬 값은 전부 파일 하나에 들어간다.

### 1단계. 로컬 설정 파일 만들기

IntelliJ에서 `src/main/resources/application-local.properties`를 직접 만든다.
기존 파일이 있다면 유지하고 필요한 값을 채운다. 별도의 `.example` 파일은 제공하지 않는다.

아래는 비밀값이 없는 작성 예시다. `REPLACE_...` 부분을 해당 환경의 실제 값으로 바꾼다.
DB 주소의 `13306`은 SSH 터널의 로컬 포트 예시이므로 사용하는 접속 방식에 맞춘다.

```properties
spring.datasource.url=jdbc:mysql://localhost:13306/oogachaka
spring.datasource.username=REPLACE_WITH_APP_DB_USERNAME
spring.datasource.password=REPLACE_WITH_APP_DB_PASSWORD

spring.flyway.url=${spring.datasource.url}
spring.flyway.user=REPLACE_WITH_FLYWAY_DB_USERNAME
spring.flyway.password=REPLACE_WITH_FLYWAY_DB_PASSWORD

app.crypto.aes-keys.v1=REPLACE_WITH_AES_KEY
app.crypto.hmac-social-id-key=REPLACE_WITH_SOCIAL_HMAC_KEY
app.crypto.hmac-device-token-key=REPLACE_WITH_DEVICE_HMAC_KEY
app.jwt.secret=REPLACE_WITH_JWT_KEY
jasypt.encryptor.password=REPLACE_WITH_JASYPT_PASSWORD
```

JWT 키는 후속 인증 기능에서 사용하며 현재 5-65 기동의 필수값은 아니다.

- 이 파일은 `.gitignore`에 등록되어 있어 Git에 올라가지 않는다. 그래서 값을 **평문**으로 적어도 된다.
  확인하려면 `git check-ignore -v src/main/resources/application-local.properties`를 실행한다.
  한 줄이 출력되면 무시 대상이다(출력이 없으면 커밋하지 말고 팀에 알린다).
- local 프로필로 실행하면 Spring이 `application.properties`를 먼저 읽고 이 파일을 그 위에 덮어쓴다.
  같은 키는 이 파일 값이 이기고, 이 파일에 없는 키는 `application.properties` 값을 그대로 쓴다.
- 위 예시 외의 설정은 로컬에서 다른 값을 써야 할 때만 추가한다.

### 2단계. DB 접속 대상과 계정 맞추기

1단계 파일의 `spring.datasource.*`에는 일반 앱 계정을, `spring.flyway.user`와
`spring.flyway.password`에는 마이그레이션용 계정을 평문으로 적는다. 앱 계정은 데이터 조회·변경에,
Flyway 계정은 스키마 변경과 Flyway 이력 관리에 사용한다. URL은 같은 DB를 가리키도록 공유한다.

- SSH 터널로 공용 DB에 연결한다면 터널을 켜고 **로컬 수신 포트**를 URL에 적는다.
  예를 들어 터널이 `13306`에서 열렸다면 `jdbc:mysql://localhost:13306/oogachaka`를 사용한다.
- 공용 DB에 직접 연결한다면 해당 DB의 호스트·포트를 적는다.
- 독립된 로컬 MySQL을 사용한다면 그 서버에 DB를 만들고 해당 주소를 적는다. 로컬 MySQL 설치가
  모든 팀원에게 필수인 것은 아니다.

공통 파일의 운영용 주소는 **운영 앱이 실행되는 환경 기준**이다. 운영 앱과 MySQL이 같은 서버에서
직접 실행된다면 `jdbc:mysql://localhost:3306/oogachaka`를 사용할 수 있다. 내 PC의 SSH 터널 주소와
운영 주소를 구분한다.

`local`로 실행해도 Flyway는 켜져 있다. 공용 DB를 연결 대상으로 정하면 그 DB에 미적용 SQL이
실행되므로 팀과 변경 내용·적용 시점을 맞춘다. 기존 테이블이 있고 Flyway 이력이 없다면 현재
`baseline-on-migrate=true` 설정은 기본 기준 버전 1을 등록하고 V2부터 적용한다. 데이터가 0건이어도
테이블이 있으면 이 경우에 해당한다. 기존 스키마가 V1과 같은지는 baseline이 검사하지 않는다.

### 3단계. 공유받은 키와 Jasypt 비밀번호 넣기

공용 DB와 공통 암호문을 사용할 팀원은 먼저 팀에서 사용하는 키와 Jasypt 비밀번호를 비공개로
전달받아 1단계 파일의 해당 항목에 넣는다. 팀원마다 새 값을 만들면 기존 데이터나 공통 암호문을
읽지 못할 수 있다.

**팀의 최초 키를 정하거나 독립된 환경의 키를 새로 만들 때만** 아래 명령을 사용한다.
저장소 루트의 터미널에서 실행하면 파일에 붙여 넣을 5줄이 출력된다.

```sh
for k in app.crypto.aes-keys.v1 app.crypto.hmac-social-id-key app.crypto.hmac-device-token-key app.jwt.secret jasypt.encryptor.password; do
  echo "$k=$(openssl rand -base64 32)"
done
```

출력 예시(형태만 참고, 이 값을 쓰지 않는다):

```properties
app.crypto.aes-keys.v1=q3N8x1+Yb0kP2c6ZsV9tLmR4wE7hJdA5uFgTnXoKiQ8=
app.crypto.hmac-social-id-key=...
app.crypto.hmac-device-token-key=...
app.jwt.secret=...
jasypt.encryptor.password=...
```

`application-local.properties`의 AES 키, HMAC 키 2개, JWT 키, Jasypt 비밀번호에 해당하는 기존
5개 항목을 출력된 5줄로 교체한다. 이미 공유받은 값을 입력했다면 새로 생성하거나 교체할 필요는 없다.

| 파일에 적는 키 | 용도 | 서버·CI에서 쓰는 환경변수 이름 |
| --- | --- | --- |
| `app.crypto.aes-keys.v1` | DB에 저장하는 디바이스 토큰 원문을 AES-256-GCM으로 암호화/복호화 | `AES_ENCRYPTION_KEY` |
| `app.crypto.hmac-social-id-key` | 소셜 로그인 사용자 ID를 HMAC 해시로 바꿔 조회·중복 검사 | `HMAC_SOCIAL_ID_KEY` |
| `app.crypto.hmac-device-token-key` | 디바이스 토큰을 HMAC 해시로 바꿔 조회·중복 검사(`token_hash` 컬럼) | `HMAC_DEVICE_TOKEN_KEY` |
| `app.jwt.secret` | 로그인 후 발급하는 JWT(access/refresh) 서명 | `JWT_SECRET` |
| `jasypt.encryptor.password` | `application.properties`의 `ENC(...)` 값을 풀 때 쓰는 비밀번호 | `JASYPT_ENCRYPTOR_PASSWORD` |

지켜야 할 규칙:

- 5개는 모두 **서로 다른 값**이어야 한다. AES 키와 HMAC 키 2개가 서로 같으면 앱이 시작되지 않는다.
- 값을 따옴표로 감싸지 않고, 앞뒤에 공백을 넣지 않는다.
- `jasypt.encryptor.password`를 Git에 올라가는 `application.properties`에 적으면 앱이 시작되지 않는다.
  실수로 비밀번호가 커밋되는 것을 막기 위한 검사다.
- **혼자 쓰는 로컬 DB라면** 내가 만든 값을 그대로 쓰면 된다.
- **팀이 같은 DB를 함께 쓴다면** 5개 값을 팀원 모두가 똑같이 써야 한다. 한 명이 한 번 만들어
  비공개로 공유한다(Git, 공개 채널 금지). 값이 다르면 다음 문제가 생긴다.
  - AES 키가 다르면 다른 팀원이 저장한 디바이스 토큰을 복호화하지 못한다.
  - 소셜 HMAC 키가 다르면 같은 소셜 계정의 해시가 달라져, 이미 가입한 사용자가 신규 가입으로 처리된다.
  - Jasypt 비밀번호가 다르면 Git에 올라간 `ENC(...)`를 풀지 못해 시작에 실패한다.
- 한 번 데이터를 쌓은 뒤 AES/HMAC 키를 바꾸면 기존 데이터를 읽거나 찾을 수 없다. 키 교체는 아래
  "AES 교체" 절차를 따른다.

### 4단계. 실행

IntelliJ: `Run > Edit Configurations > OogachakaApplication`에서 JRE를 Java 17로,
**Active profiles를 `local`**로 지정하고 실행한다. 이 두 가지 외에 넣을 것은 없다.

터미널:

```sh
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

시작에 실패하면 로그의 첫 예외 메시지를 아래 표에서 찾는다.

| 메시지 | 원인 | 해결 |
| --- | --- | --- |
| `Jasypt 비밀번호가 없습니다` | local 프로필이 꺼져 있거나, 파일이 없거나, `jasypt.encryptor.password`가 비어 있다 | Active profiles `local` 확인, 1·3단계 확인 |
| `Jasypt 비밀번호를 Git에 올라가는 application.properties에 적으면 안 됩니다` | 공통 파일에 비밀번호를 적었다 | 그 줄을 지우고 `application-local.properties`로 옮긴다 |
| `AES 및 HMAC 키가 모두 필요합니다` | 키 3개 중 하나가 비어 있다 | 3단계 확인 |
| `유효한 Base64 형식이어야 합니다` / `32바이트여야 합니다` | `REPLACE_...`가 남아 있거나 값이 잘렸다 | `openssl rand -base64 32`로 다시 만든다 |
| `AES 키와 용도별 HMAC 키는 서로 다른 키여야 합니다` | 같은 값을 두 번 붙여 넣었다 | 값마다 따로 만든다 |
| `Communications link failure`, `Access denied for user` | DB가 꺼져 있거나 DB 정보가 틀렸다 | 2단계 확인 |
| `Failed to bind properties under 'spring.datasource.url'` 등 `ENC(...)` 값이 들어간 키의 바인딩 실패 | `ENC(...)`를 만든 비밀번호와 지금 쓰는 비밀번호가 다르다. 셸에 `JASYPT_ENCRYPTOR_PASSWORD`가 남아 있으면 파일보다 그 값이 우선한다 | 팀 비밀번호로 맞추고, 셸에 등록된 환경변수가 로컬 파일의 값을 덮어쓰는지 확인한다 |

Gradle이 Java 17을 찾지 못하면 `JAVA_HOME`을 Java 17 경로로 지정한 뒤 실행한다.

앱 시작은 Flyway를 실행한다. V2는 빈 `device_token` 테이블을 전제로 한다. 기존 행이 있으면
NOT NULL/UNIQUE 추가가 실패할 수 있으므로 적용 전에 별도 백필·중복 정리를 준비한다.
소셜 기존 값도 현재 HMAC 키의 소문자 hex 64자리인지 확인한다. 마이그레이션은 자동으로
평문을 HMAC으로 변환하지 않는다. 공용 DB에는 팀에서 적용 시점을 합의한 SQL을 반영한다.

### V2 적용 전 점검

이 절은 **V2가 아직 적용되지 않은 DB에 처음 적용할 때** 확인한다. V2가 이미 적용된 DB에서는
기존 적용 이력을 사용하며, 앱을 실행할 때마다 V2를 다시 적용하지 않는다.

공용 DB는 **아래 점검과 스키마 대조를 마친 뒤에만** V2를 적용한다. 점검 전에는
그 DB를 대상으로 앱을 시작하지 않는다. `baseline-on-migrate=true`는 기존 스키마가 V1과
같은지 검증하지 않으며, V1로 baseline한 다음 V2를 실제 실행한다.
공용 DB의 스키마·데이터·sql_mode는 적용 시점에 대상 환경에서 확인한다.

다음은 MySQL 8용 읽기 전용 점검 SQL이다. 대상 DB를 선택하고 Flyway 연결과 동일한 접속 설정의
세션에서 확인한다. 원문 ID나 토큰은 조회하지 않는다.

```sql
SELECT DATABASE() AS target_database,
       @@SESSION.sql_mode AS session_sql_mode,
       @@GLOBAL.sql_mode AS global_sql_mode;

SHOW CREATE TABLE social_account;
SHOW CREATE TABLE device_token;
SHOW INDEX FROM social_account;
SHOW INDEX FROM device_token;

SELECT MAX(CHAR_LENGTH(provider_user_id_hash)) AS max_hash_length,
       COUNT(*) AS social_account_rows,
       COALESCE(SUM(provider_user_id_hash IS NULL
           OR CHAR_LENGTH(provider_user_id_hash) <> 64), 0) AS invalid_length_rows,
       COALESCE(SUM(NOT REGEXP_LIKE(provider_user_id_hash, '^[0-9a-f]{64}$', 'c')), 0)
           AS invalid_hex_rows
FROM social_account;

SELECT COUNT(*) AS device_token_rows FROM device_token;

SELECT COUNT(*) AS token_hash_column_count
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'device_token'
  AND COLUMN_NAME = 'token_hash';
```

- `SHOW CREATE TABLE`과 인덱스를 V1·V2 SQL과 대조한다. 테이블·컬럼·제약이 예상과 다르거나
  `token_hash_column_count`가 0이 아니면 그대로 적용하지 않는다.
- `max_hash_length`가 64를 넘거나 길이·hex 형식이 잘못된 행이 있으면 적용을 중단한다.
  형식만으로 현재 HMAC 키로 생성된 값인지는 알 수 없으므로 생성 경로와 키 운영도 확인한다.
- 현재 V2는 `device_token_rows=0`을 전제로 한다. 기존 행이 있으면 단계별 컬럼 추가·백필·중복
  정리·NOT NULL/UNIQUE 적용 절차를 별도로 준비한다.
- Flyway가 실행하는 세션의 sql_mode에 `STRICT_TRANS_TABLES` 또는 `STRICT_ALL_TABLES`가
  포함되는지 확인한다. global 값만으로 실제 실행 세션을 보장할 수 없다. non-strict에서 컬럼을
  줄이면 값이 잘릴 수 있고, 문자 집합 변경도 변환에 따른 데이터 손실을 일으킬 수 있다.
  strict에서도 끝 공백 등의 처리에 의존하지 않고 원래 데이터의 길이·문자 종류부터 점검한다.

점검이 실패하면 앱을 재시작하며 시도하지 않고 DB 관리자와 데이터 전환 절차를 준비한다.
적용된 마이그레이션을 수정하거나 schema history만 맞추는 방식으로 우회하지 않는다.
위 SQL은 적용 전 현재 상태를 확인하기 위한 조회 예시다.
참고: [MySQL SQL mode](https://dev.mysql.com/doc/refman/8.4/en/sql-mode.html),
[컬럼 문자 집합 변경](https://dev.mysql.com/doc/refman/8.4/en/charset-column.html).

## 공용 값 암호화 (application.properties)

**공용 DB 정보나 공용 OAuth secret을 Git에 올릴 때만** 한다. 로컬 개발만 할 때는 필요 없다.

현재 공통 DB 암호문을 그대로 사용하는 팀원은 다시 암호화할 필요가 없다. 아래 절차는 공용
접속정보를 새로 등록하거나 교체할 때 사용한다. 여기서 원문은 DB 주소·계정·DB 비밀번호이고,
이를 잠그고 푸는 열쇠는 별도의 Jasypt 비밀번호다.

1. 내 `application-local.properties`의 `jasypt.encryptor.password`가 **팀에서 정한 Jasypt 비밀번호**인지
   확인한다. 암호화 도구는 환경변수 `JASYPT_ENCRYPTOR_PASSWORD`를 먼저 보고, 없으면 이 파일에서
   비밀번호를 읽는다. 앱·DB·Flyway는 시작하지 않는다.
2. 저장소 루트에서 아래를 실행한다. 첫 줄을 실행하면 입력 프롬프트가 뜨고, 암호화할 값을 붙여 넣고
   Enter를 누른다. 입력한 값은 화면과 셸 이력에 남지 않는다.

   ```sh
   read -rs 'JASYPT_INPUT?암호화할 값 한 줄: '
   printf '\n'
   printf '%s\n' "$JASYPT_INPUT" | ./gradlew -q --console=plain jasyptEncrypt
   unset JASYPT_INPUT
   ```

3. `ENC(...)` 형태의 한 줄이 출력된다. 괄호까지 전부 복사해 `application.properties`의 해당 값을 바꾼다.

   ```properties
   spring.datasource.url=ENC(출력된_암호문)
   spring.datasource.username=ENC(출력된_암호문)
   spring.datasource.password=ENC(출력된_암호문)
   spring.flyway.url=${spring.datasource.url}
   spring.flyway.user=ENC(출력된_암호문)
   spring.flyway.password=ENC(출력된_암호문)
   ```

4. 값마다 2~3을 반복한다. DB URL, 앱 계정·비밀번호, Flyway 계정·비밀번호의 **5개 값을 각각 따로**
   암호화한다. 각 항목에는 해당 값을 암호화한 결과를 넣는다. Flyway URL은 앱 URL을 참조하므로
   별도로 암호화하지 않는다.
5. 커밋한다. 팀원은 같은 Jasypt 비밀번호만 있으면 이 값을 그대로 쓸 수 있다.

참고:

- 같은 값을 다시 암호화해도 매번 다른 `ENC(...)`가 나온다. 정상이다(무작위 salt/IV).
- OAuth secret, Redis 비밀번호, 내용으로 주입하는 서비스 계정 JSON도 같은 방식이다. 서버 환경변수의
  값으로 `ENC(...)`를 넣어도 풀린다. 여러 줄 JSON은 한 줄 JSON으로 바꾼 뒤 입력한다.
- AES/HMAC/JWT 키와 Jasypt 비밀번호 자체는 Jasypt로 감싸지 않는다.
- `./gradlew -q jasyptEncrypt -Pvalue=TEST_ONLY_DUMMY_VALUE`처럼 값을 바로 넘길 수도 있지만 셸 이력과
  프로세스 인자에 남으므로 더미 값 확인에만 쓴다.

규격은 `PBEWITHHMACSHA512ANDAES_256`, 키 유도 10,000회, 무작위 salt/IV, Base64다.
`JasyptEncryptorFactory`를 CLI와 `jasyptStringEncryptor` 빈이 공유한다. 알고리즘 설정은 이
팩토리에 고정되어 있다. 기존 다른 규격의 암호문은 이 도구로 다시 암호화한다.

## 인증 및 후속 푸시 설정 계약

인증 구현은 Redis/jjwt 의존성과 `app.jwt`/`app.oauth`/`app.cors` 바인딩을 제공한다.
JWT_SECRET 누락·빈 값·Base64 오류·디코딩 후 32바이트 미만 키는 시작 시 거부한다.
서명 알고리즘은 HS256으로 고정하며 최소 키 길이 기준은
[JWA의 HMAC 규격](https://www.rfc-editor.org/rfc/rfc7518.html#section-3.2)을 따른다.
Redis는 GETDEL을 지원하는 6.2 이상이어야 한다. 활성 OAuth 제공자는 필수 client ID/secret,
절대 redirect URI 및 HTTPS 외부 API 주소를 시작 시 검사한다(카카오 secret은 선택).
구글·카카오 S256 PKCE, 인증 응답 계약, Redis 키/운영 조건 및 제공자별 콘솔 설정은
[인증 API 가이드](auth-api.md)를 참고한다. Firebase 및 푸시 실행기는 후속 푸시 구현 범위다.

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
`CryptoConfig`는 `CryptoProperties`를 Spring 설정 객체로 등록한다. Converter의 생성자 주입에는
현재 Spring ORM의 기본 Hibernate 연동을 사용하며, 별도의 bean container 등록 설정을 추가하지 않는다.

두 해시 필드는 `@Column(length = 64)`만 사용하지 않고 JDBC 타입을 명시한다.
후속 인증/푸시 엔티티는 다음 매핑을 사용한다. 문자 집합과 collation은 Flyway V2가 관리하며,
HMAC은 AttributeConverter를 적용하지 않는다.
[Hibernate의 JdbcTypeCode](https://docs.hibernate.org/orm/7.4/javadocs/org/hibernate/annotations/JdbcTypeCode.html)로
JDBC 타입을 지정한다. 실제 엔티티의 스키마 검증과 조회는 해당 기능을 구현할 때 확인한다.

```java
import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// SocialAccount
@JdbcTypeCode(SqlTypes.CHAR)
@Column(name = "provider_user_id_hash", nullable = false, length = 64)
private String providerUserIdHash;

// DeviceToken
@JdbcTypeCode(SqlTypes.CHAR)
@Column(name = "token_hash", nullable = false, length = 64)
private String tokenHash;
```

AES 저장 규격은 `v1:Base64(IV + 암호문 + 태그)`이며 버전 UTF-8 바이트를 GCM AAD로 인증한다.
원문이 N바이트라면 버전 길이 + 1 + `4 * ceil((12 + N + 16) / 3)` 바이트가 필요하다.
TEXT의 65,535바이트 한계 등 실제 컬럼 한도와 도메인의 입력 길이를 함께 검증한다.

AES 교체 시 기존 `app.crypto.aes-keys.v1`을 유지하고 환경별 로컬 설정에
`app.crypto.aes-keys.v2=${AES_ENCRYPTION_KEY_V2}`를 추가한 뒤 `AES_ACTIVE_VERSION=v2`를 주입한다.
새 값은 v2로 저장되고 기존 값은 접두어의 키로 복호화한다. 재암호화와 백업/복구 확인이 끝나기 전
이전 키를 삭제하지 않는다. HMAC은 원문을 복원할 수 없어 소셜 키 변경 시 계정 식별이 끊길 수 있다.
계정 재연동/데이터 전환을 먼저 계획한다. 디바이스 HMAC 키 변경 시 기존 토큰을 복호화하여
token_hash를 백필하고 전환 중 UNIQUE 검사가 일관되도록 별도 배포 절차를 준비한다.

## 운영·CI 환경변수 주입

GitHub Actions로 배포한다면 저장소의 `Settings > Secrets and variables > Actions`에
Jasypt 비밀번호와 AES/HMAC/JWT 키의 실제 값을 환경별 Secrets로 등록할 수 있다.
공통 파일의 DB·Flyway 계정은 이미 `ENC(...)`로 저장하므로 별도의 DB 계정 환경변수는 요구하지 않는다.

다음은 워크플로 단계에서 Secrets를 환경변수로 꺼내는 부분만 보여주는 예시다.

```yaml
env:
  JASYPT_ENCRYPTOR_PASSWORD: ${{ secrets.JASYPT_ENCRYPTOR_PASSWORD }}
  AES_ENCRYPTION_KEY: ${{ secrets.AES_ENCRYPTION_KEY }}
  HMAC_SOCIAL_ID_KEY: ${{ secrets.HMAC_SOCIAL_ID_KEY }}
  HMAC_DEVICE_TOKEN_KEY: ${{ secrets.HMAC_DEVICE_TOKEN_KEY }}
  JWT_SECRET: ${{ secrets.JWT_SECRET }}
```

Secrets 등록과 위 `env` 설정만으로 운영 서버의 환경변수가 자동 설정되지는 않는다.
배포 워크플로에서 이 값을 실제 앱을 실행하는 JVM·컨테이너의 환경변수로 전달하는 단계를 구성한다.
운영 앱은 전달받은 `JASYPT_ENCRYPTOR_PASSWORD`로 공통 암호문을 복호화한다.
컴파일 자체는 비밀값을 요구하지 않는다.
서비스 계정 파일은 저장소에 올리지 않고 비밀 파일 마운트/ADC 또는 Secret의 내용 주입을 사용한다.
등록·참조 방법은 [GitHub Actions Secrets 공식 문서](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)를 참고한다.

## 검증 범위와 테스트 실행

아래는 작성된 테스트의 범위다. 컴파일·실행 여부와 결과는 각 PR에 별도로 기록한다.
단위 테스트에는 AES 왕복/IV/변조/잘못된 키/버전 교체/null 및 HMAC 결정성/키 분리/입력 규칙,
설정 바인딩/시작 실패 조건, Converter의 암복호화 위임, 암호화 오류의 공통 응답 처리를 작성했다.
키는 `TestCryptoKeys`에서 생성한 테스트 전용 더미 값을 사용한다.

`./gradlew test`는 외부 DB나 별도 서버 없이 단위·MVC 테스트를 실행한다.
Docker가 없는 환경에서는 이 명령만 실행하고 통합 테스트는 미실행으로 기록한다.
인증의 동시 토큰 갱신·가입 롤백은 Docker를 준비한 뒤 `./gradlew integrationTest`로 확인한다.
이 테스트는 격리된 Redis/MySQL과 더미 키를 사용하며 로컬·운영 접속 정보나 Jasypt 비밀번호가 필요하지 않다.
JPA Converter 주입과 암호문 컬럼 용량 검증은 해당 엔티티 구현 시 추가한다.

## Jasypt 선택 근거

`build.gradle`에 지정된 Jasypt starter는 **4.0.4**, Spring Boot는 **4.1.2**다.
starter JAR에는 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`가
있다. 하위 `jasypt-spring-boot` JAR의 후처리기는 이전
`org.springframework.boot.env.EnvironmentPostProcessor` 키로 등록되지만 Boot 4.1.2가 해당 키와
인터페이스를 호환 어댑터로 지원한다. 이동한 SystemEnvironment 후처리기의 순서 상수는
바이트코드에 인라인되어 있었다. 별도 임시 코드로 Boot 4.1.2 컨텍스트에서 실제 `ENC(...)`
복호화를 확인해 starter를 채택했다. 임시 코드는 저장소에 남기지 않았다.
Boot 4.2 이상으로 올릴 때는 구 EnvironmentPostProcessor API의 제거 여부와 starter 최신 버전의
등록 방식/실제 ENC 복호화를 다시 확인한다. 현재 검증 범위는 Boot 4.1.2와 starter 4.0.4다.

근거: [Maven Central starter](https://central.sonatype.com/artifact/com.github.ulisesbocchio/jasypt-spring-boot-starter),
[metadata](https://repo.maven.apache.org/maven2/com/github/ulisesbocchio/jasypt-spring-boot-starter/maven-metadata.xml),
[Jasypt 프로젝트](https://github.com/ulisesbocchio/jasypt-spring-boot),
[Boot 환경 후처리기 API](https://docs.spring.io/spring-boot/4.1/api/java/org/springframework/boot/EnvironmentPostProcessor.html).
