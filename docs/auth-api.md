# 인증 API와 제공자 설정

모든 응답은 `ApiResponse`의 `code`, `message`, `data` 구조를 사용한다.
환경변수와 DB 적용 전 준비는 [환경 설정 가이드](environment-setup.md)를 따른다.

## 클라이언트 로그인 흐름

1. `GET /api/v1/auth/google/authorize`(또는 `kakao`, `naver`)를 호출한다. 응답의 `data.authorizationUrl`과
   `data.state`를 받는다. 클라이언트는 state를 해당 로그인 시도에 연결해 보관한다(웹은 sessionStorage 등).
2. 브라우저에서 authorizationUrl을 연다. 제공자가 설정된 **프런트 콜백 URI**로 code와 state를 보낸다.
   프런트는 콜백 state가 자신이 보관한 값과 일치하는지 검사한다. 일치하지 않거나 제공자가 error를 반환하면
   로그인을 중단한다.
3. 프런트가 `POST /api/v1/auth/social-login`에 provider, code, state를 JSON으로 보낸다.
   JSON의 provider는 `GOOGLE`, `KAKAO`, `NAVER`이며 경로는 대소문자를 모두 허용한다.
4. 서버가 state를 한 번만 소비하고 제공자 토큰·프로필을 서버 간 호출로 조회한다.
   구글·카카오는 S256 PKCE를 사용한다. 네이버는 공식 API에 PKCE 규격이 없어 쓰지 않는다.
5. 첫 로그인은 자동 가입한다. 닉네임은 제공자 값의 30자까지, 미제공 시 `우가차차_<임의 8자리>`다.

state는 기본 5분이며 실패하면 authorize부터 다시 시작한다.
공개 인증 API(authorize, social-login, refresh)는 Authorization 헤더를 무시하므로, 만료된 access가 붙어 있어도 호출할 수 있다.

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

수명 필드는 초 단위다. 응답은 제공자 토큰·원본 소셜 ID·이메일을 포함하지 않는다.
같은 이메일이더라도 제공자가 다르면 별도 계정이다.

## 갱신과 로그아웃

`POST /api/v1/auth/refresh`에 `{"refreshToken":"<app refresh JWT>"}`를 보낸다.
응답의 data는 위 tokens 객체와 같은 형식이다. 이전 refresh는 삭제되고 새 refresh로 바뀐다.
같은 refresh로 동시에 요청하면 하나만 성공하므로, 클라이언트는 갱신 요청을 하나로 합치고 응답의 refresh로 즉시 교체한다.
기기마다 refresh가 따로 유지된다.

`POST /api/v1/auth/logout`은 `Authorization: Bearer <app access JWT>`와 refreshToken 본문이 필요하다.
두 토큰의 사용자가 같아야 하며, 해당 기기의 refresh를 삭제한다. 응답은 SUCCESS/data=null이다.
**이미 발급된 access는 만료 시각(기본 30분)까지 유효하다.** 클라이언트는 이 기기에서 보관한 두 토큰을 제거한다.

인증된 컨트롤러는 `@AuthenticationPrincipal Long userId`로 사용자 ID를 받는다. `/api/**`는 인증을 요구한다.

## 오류

| HTTP | code | 의미 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | 요청 본문/ENUM/길이/필수 값 오류 |
| 400 | AUTH_PROVIDER_UNSUPPORTED | 미지원 또는 설정되지 않은 제공자 |
| 400 | AUTH_SOCIAL_LOGIN_FAILED | 소셜 로그인 실패(state 없음·만료·재사용, 인가 코드 거절, 제공자 장애·잘못된 응답). 로그인 재시작 |
| 401 | UNAUTHORIZED | 인증 헤더 없음 |
| 401 | AUTH_TOKEN_EXPIRED | JWT 만료. refresh로 갱신한다 |
| 401 | AUTH_TOKEN_INVALID | 그 외 JWT 오류, 이미 쓰였거나 로그아웃된 refresh. 다시 로그인한다 |
| 403 | FORBIDDEN | 권한 거부 |

## Redis

Redis **6.2 이상**이 필요하다(GETDEL). 키는 `AuthRedisKeys`에서 관리한다.

- `oogachaka:auth:state:<state>` → PKCE verifier(네이버는 빈 값); 기본 TTL 5분.
- `oogachaka:auth:refresh:<userId>:<jti>` → userId; TTL은 refresh 수명(기본 14일).

## 제공자 콘솔 설정

redirect URI는 서버 API가 아니라 프런트 콜백 주소이며, 콘솔 등록값과 글자 하나까지 일치해야 한다.
client-id는 `application.properties`에 평문으로, client secret은 Jasypt `ENC(...)`로 적는다.
client-id를 비워 둔 제공자는 사용하지 않는 것으로 본다. redirect URI만 환경변수로 바꿀 수 있다.

- **구글**: OAuth client를 웹 애플리케이션으로 만들고 승인된 리디렉션 URI에 `GOOGLE_REDIRECT_URI` 값을 등록한다.
- **카카오**: Redirect URI에 `KAKAO_REDIRECT_URI` 값을 등록한다. client-id에는 REST API 키를 쓴다.
  콘솔에서 client secret을 껐다면 secret은 비워 둔다. 동의항목은 닉네임만 사용한다(이메일은 비즈 앱 권한이 없어 받지 않는다).
  카카오 PKCE는 OIDC Discovery 메타데이터만 근거라 실제 로그인으로 확인해야 한다.
- **네이버**: Callback URL에 `NAVER_REDIRECT_URI` 값을 등록하고 별명·이메일 동의항목을 설정한다.
  아직 앱을 발급받지 않아 client-id를 비워 두었다.
