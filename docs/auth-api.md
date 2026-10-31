# 인증 API와 제공자 설정

모든 응답은 `ApiResponse`의 `code`, `message`, `data` 구조를 사용한다.
토큰과 인가 URL/state 응답은 캐시하지 않는다. HTTPS로 호출하고 토큰/인가 코드를 로그에 남기지 않는다.
환경변수와 DB 적용 전 준비는 [환경 설정 가이드](environment-setup.md)를 따른다.

## 클라이언트 로그인 흐름

1. `GET /api/v1/auth/google/authorize`(또는 `kakao`, `naver`)를 호출한다. 응답의 `data.authorizationUrl`과
   `data.state`를 받는다. 클라이언트는 state를 해당 로그인 시도에 연결해 보관한다(웹은 sessionStorage 등).
2. 시스템 브라우저/브라우저에서 authorizationUrl을 연다. 제공자가 설정된 **프런트 콜백 URI**로 code와 state를 보낸다.
   프런트는 콜백 state가 자신이 보관한 값과 일치하는지 검사한다. 일치하지 않거나 제공자가 error를 반환하면
   로그인을 중단한다. 인가 코드를 브라우저 이력/분석 로그에 남기지 않도록 콜백 URL을 정리한다.
3. 프런트가 `POST /api/v1/auth/social-login`에 provider, code, state를 JSON으로 보낸다.
   JSON의 provider는 `GOOGLE`, `KAKAO`, `NAVER`이며 경로는 대소문자를 모두 허용한다.
4. 서버가 Redis GETDEL로 state를 한 번만 소비하고, 제공자 토큰/프로필을 서버 간 호출로 조회한다.
   구글·카카오는 S256 PKCE를 사용하며 verifier는 서버 Redis에만 보관한다. 네이버는 state를 검증하며
   공식 API에 PKCE 규격이 명시되지 않아 PKCE 파라미터를 보내지 않는다.
5. 첫 로그인은 자동 가입한다. 닉네임은 제공자 값의 Unicode 코드 포인트 30자까지,
   미제공 시 `우가차차_<임의 8자리>`다. 역할/가족 가입은 이후 기능이다.

공개 인증 API(GET authorize, POST social-login/refresh)는 Authorization 헤더를 무시한다. 클라이언트의
공통 인터셉터가 만료·무효 access를 붙여도 인가/로그인/갱신을 진행할 수 있다. 이때 access 인증 주체를
만들거나 블랙리스트를 조회하지 않는다. refresh는 본문의 refreshToken을 별도로 검증한다.
logout과 다른 보호 API는 계속 access 검증을 요구한다. 공개 경로와 HTTP 메서드가 정확히 일치하는 경우만
필터를 건너뛰며, 하위 경로나 다른 메서드까지 공개하지 않는다. state는 기본 5분이며 실패/재시도 시
authorize부터 다시 시작한다. 프런트는 로그인 시도를 안전하게 연결해야 하며 서버 state의 존재 검사만으로
클라이언트 콜백 바인딩을 대신할 수 없다.

```http
POST /api/v1/auth/social-login
Content-Type: application/json

{"provider":"GOOGLE","code":"<callback code>","state":"<callback state>"}
```

```json
{
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "userId": 42,
    "isNewUser": true,
    "tokens": {
      "tokenType": "Bearer",
      "accessToken": "<app access JWT>",
      "refreshToken": "<app refresh JWT>",
      "accessTokenExpiresIn": 1800,
      "refreshTokenExpiresIn": 1209600
    }
  }
}
```

수명 필드는 초 단위다. 신규 가입 여부의 JSON 키는 `isNewUser`로 명시적으로 고정한다.
응답은 제공자 토큰·원본 소셜 ID·이메일을 포함하지 않는다.
이메일은 동의한 제공자가 반환한 경우에만 social_account에 평문 저장하며 255자를 넘으면 저장하지 않는다.
제공자 프로필 이미지는 저장하지 않는다. 같은 이메일이더라도 제공자가 다르면 별도 계정이다.

## 갱신과 로그아웃

`POST /api/v1/auth/refresh`에 `{"refreshToken":"<app refresh JWT>"}`를 보낸다.
응답의 data는 위 tokens 객체와 같은 형식이다. 이전 refresh는 원자적으로 삭제되며 새 access/refresh를 저장한다.
동시에 같은 refresh로 요청하면 하나만 성공한다. 클라이언트는 갱신 요청을 하나로 합치고 성공 응답의
refresh를 즉시 교체해야 한다. 성공 응답을 유실한 뒤 이전 refresh를 재시도하면 401이며 재로그인이 필요하다.
다중 기기는 서로 다른 jti로 독립 유지한다. 이미 소비된 refresh의 재사용은 해당 요청을 차단하고
다른 기기의 refresh를 일괄 삭제하지 않는다.

`POST /api/v1/auth/logout`은 `Authorization: Bearer <app access JWT>`와 같은 refreshToken 본문이 필요하다.
access/refresh의 사용자 ID가 같아야 한다. 해당 refresh를 삭제하고 현재 access jti를 남은 수명만큼
블랙리스트에 저장한다. 이미 삭제된 같은 사용자 refresh로도 현재 access의 블랙리스트 처리는 가능하다.
서명/종류/만료 검증을 통과한 refresh만 받는다. 다른 기기 토큰은 유지한다. 응답은 SUCCESS/data=null이다.
클라이언트는 이 기기에서 보관한 두 토큰을 제거한다.

갱신 전 발급된 access는 자체 만료 시점까지 유효하고, 로그아웃은 요청에 사용한 현재 access 하나를 차단한다.
계정 전체 로그아웃, 토큰 패밀리 전체 재사용 탐지/취소, 회원탈퇴는 이번 범위가 아니다.

인증된 다른 컨트롤러는 `@AuthenticationPrincipal Long userId`로 사용자 ID를 받는다.
역할/권한 목록은 아직 부여하지 않으며 `/api/**`는 인증을 요구한다. 나머지 경로와 ERROR 디스패치는 허용한다.
CORS preflight는 설정된 origin/method/header 조건에서 인증 없이 처리된다. CORS 거부도 공통 JSON 403이다.
다중 인스턴스의 짧은 시계 차이를 위해 iat의 미래 시각은 최대 5초까지 허용한다. exp 만료와 Redis TTL은
기존 수명대로 검증하며 만료 후 유예는 없다. 운영 서버의 시계 동기화는 별도로 유지한다.

## 오류

| HTTP | code | 의미 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | 요청 본문/ENUM/길이/필수 값 오류 |
| 400 | AUTH_PROVIDER_UNSUPPORTED / AUTH_PROVIDER_DISABLED | 미지원/비활성 제공자 |
| 400 | AUTH_SOCIAL_LOGIN_REJECTED | 인가 코드 교환 거절(429 제외 4xx 또는 OAuth error). 로그인 재시작 |
| 401 | UNAUTHORIZED | 인증 헤더 없음 |
| 401 | AUTH_TOKEN_EXPIRED | JWT 만료 |
| 401 | AUTH_TOKEN_INVALID | JWT 서명/발급자/필수 클레임/종류/사용자 불일치 |
| 401 | AUTH_TOKEN_REVOKED | 로그아웃된 access |
| 401 | AUTH_REFRESH_REUSED | Redis에 없거나 이미 교체/삭제된 refresh |
| 401 | AUTH_STATE_INVALID | Redis state 없음/만료/재사용/제공자 불일치 |
| 403 | FORBIDDEN | 권한 또는 CORS 거부 |
| 502 | AUTH_SOCIAL_LOGIN_FAILED | 제공자 userinfo 거절/잘못된 응답/필수 ID 누락 |
| 503 | AUTH_PROVIDER_UNAVAILABLE | 제공자 타임아웃/통신 장애/429/5xx |
| 503 | AUTH_STORE_UNAVAILABLE | Redis 장애 |

외부 원문 응답/토큰/예외 원인은 반환하거나 기록하지 않는다. Redis 장애 시 인증을 허용하지 않는다.

## Redis 운영 계약

Redis **6.2 이상**이 필요하다(GETDEL). 인증 Redis는 영속화/고가용성을 갖추고 키 조기 축출을 방지한다.
블랙리스트가 유실되면 아직 만료되지 않은 access가 다시 허용될 수 있으므로 Redis 데이터 수명/보관을 관리한다.

- `oogachaka:auth:state:<state>` → `PROVIDER:<verifier>`; 기본 TTL 5분. 네이버 verifier는 빈 값이다.
- `oogachaka:auth:{user:<id>}:refresh:<jti>` → userId 문자열; 기본 TTL 14일(실제 JWT 남은 수명).
- `oogachaka:auth:{user:<id>}:blacklist:<jti>` → `1`; 현재 access의 남은 수명.

키는 AuthRedisKeys에서 관리한다. 사용자별 hash tag로 refresh rotation/logout의 키를 Redis Cluster의
같은 슬롯에 배치한다. refresh rotation과 logout은 Lua로 원자 처리한다. state는 SET NX와 GETDEL을 사용한다.
제공자 토큰/원본 소셜 ID/이메일은 Redis에 저장하지 않는다.

인가 URL 요청에는 아직 애플리케이션 자체 레이트 리밋이 없다. 공개 authorize의 요청량과 state 키 수를
운영에서 관측하고, 배포 인프라가 정해지면 게이트웨이의 요청 제한을 먼저 구성한다. IP별 제한이 필요하면
프록시의 신뢰 범위와 실제 클라이언트 IP 전달 계약을 확정한 뒤 적용한다. 임의의 X-Forwarded-For 값을
신뢰하는 제한은 추가하지 않았다. state TTL을 줄이는 방식으로 요청 제한을 대체하지 않는다.

## 내일 제공자 콘솔에서 할 일

현재 세 제공자는 기본 비활성화다. 아래의 redirect URI는 서버 API가 아니라 실제 프런트 콜백 주소이며,
scheme/host/port/path/끝 슬래시까지 설정값과 일치시킨다. 서버와 앱을 각각 다른 OAuth 클라이언트로
구성한다면 그 클라이언트의 code를 이 API에 임의로 섞지 않는다. 이 구현은 서버가 보관한 client 설정으로만 교환한다.

### 구글 Cloud Console / Google Auth Platform

- 프로젝트를 선택하고 OAuth 동의 화면에 앱 이름·지원 이메일·승인 도메인·개인정보처리방침 등을 설정한다.
- Audience가 테스트 상태이면 테스트 사용자를 등록한다. 외부 배포 시 게시/필요한 검증 절차를 준비한다.
- OAuth client를 **웹 애플리케이션**으로 생성하고 승인된 리디렉션 URI에 GOOGLE_REDIRECT_URI와 같은 주소를 등록한다.
- openid/profile/email에 필요한 기본 범위만 등록한다. 추가 Google API 권한은 요청하지 않는다.
- GOOGLE_CLIENT_ID, Jasypt로 암호화한 GOOGLE_CLIENT_SECRET, GOOGLE_REDIRECT_URI를 주입하고 GOOGLE_OAUTH_ENABLED=true로 설정한다.
- 브라우저를 통한 code 흐름과 PKCE 교환을 실제 환경에서 확인한다. 서버의 userinfo sub를 식별자로 사용한다.

### 카카오 Developers

- 앱에서 카카오 로그인을 활성화하고 사용할 웹/모바일 플랫폼 정보를 실제 서비스와 맞춘다.
- 카카오 로그인 Redirect URI에 KAKAO_REDIRECT_URI를 등록한다.
- KAKAO_CLIENT_ID에는 **REST API 키**를 사용한다. client secret을 활성화했다면 발급한 값을 ENC(...)로 KAKAO_CLIENT_SECRET에 주입한다.
  콘솔에서 비활성화한 경우만 빈 값으로 둔다.
- 동의항목의 닉네임/프로필 사진/카카오계정 이메일 권한을 확인한다. 기본 scopes는
  profile_nickname,profile_image,account_email이다. 승인되지 않았거나 사용하지 않는 항목은
  `app.oauth.providers.kakao.scopes`를 환경별로 줄인다(이미지 미저장). 이메일 사용 권한/비즈 앱 조건도 확인한다.
- KAKAO_OAUTH_ENABLED=true로 설정한다. OIDC ID 토큰은 사용하지 않고 /v2/user/me의 숫자 id를 사용한다.
- 공식 Discovery가 S256을 명시하여 PKCE를 적용했다. 실제 앱 설정에서 인가·교환을 확인한다.

### 네이버 Developers

- 애플리케이션의 사용 API에 네이버 로그인을 추가하고 실제 서비스 URL 및 NAVER_REDIRECT_URI를 Callback URL로 등록한다.
- 제공 정보에서 별명/이메일 등 필요한 항목과 필수/선택 동의를 설정한다. 이름/성별/생일/연령 등 추가 정보는 요청하지 않는다.
  이 구현은 nickname만 닉네임으로 사용하고 없으면 기본 닉네임을 만든다.
- 개발 상태의 테스트 계정/멤버를 등록하고, 공개 배포 전 로그인 검수·서비스 적용 절차를 진행한다.
- NAVER_CLIENT_ID, ENC(...) 형식의 NAVER_CLIENT_SECRET, NAVER_REDIRECT_URI를 주입하고 NAVER_OAUTH_ENABLED=true로 설정한다.
- scope는 URL로 보내지 않고 콘솔 동의항목으로 관리한다. 반환 state 비교, 토큰 요청 state 전달,
  resultcode=00 및 response.id 조회를 실제 환경에서 확인한다.

## 근거와 검증 범위

- [jjwt 공식 릴리스](https://github.com/jwtk/jjwt/releases)와
  [Maven Central metadata](https://repo.maven.apache.org/maven2/io/jsonwebtoken/jjwt-api/maven-metadata.xml)에서 최신 안정 0.13.0을 확인했다.
- 구글 [서버 OAuth](https://developers.google.com/identity/protocols/oauth2/web-server),
  [Discovery](https://accounts.google.com/.well-known/openid-configuration).
- 카카오 [REST API](https://developers.kakao.com/docs/ko/kakaologin/rest-api),
  [Discovery](https://kauth.kakao.com/.well-known/openid-configuration).
- 네이버 [로그인 API](https://developers.naver.com/docs/login/api/api.md),
  [프로필 API](https://developers.naver.com/docs/login/profile/profile.md).

이번 작업은 컴파일까지만 확인했다. 테스트 실행, Docker/Redis/MySQL/Flyway 실행, 실제 제공자 호출은 하지 않았다.
JUnit/Mockito/MockMvc 및 Testcontainers Redis/MySQL 테스트는 작성되어 있으며, 실행이 허용되면 Docker와
테스트용 Jasypt 환경변수를 준비해 검증한다. MySQL의 실제 User/SocialAccount 매핑 validate는 기존
contextLoads와 SocialAccountRepositoryIntegrationTest에서 확인하도록 작성했다. 후자는 V1/V2 적용,
운영 Repository의 HMAC 조회·제공자별 UNIQUE·사용자 UNIQUE·가입 롤백·동시 최초 가입을 다룬다.
