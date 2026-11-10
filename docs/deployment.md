# 백엔드 CI/CD 및 배포 가이드

## 배포 흐름

PR → 테스트·JAR 빌드  
dev 반영 → 테스트·빌드 → ARM64 이미지 생성 → ECR 업로드  
→ S3에 배포 파일 업로드 → SSM으로 App EC2 배포 → 기동 확인

PR에서는 실제 배포를 실행하지 않는다.
수동 실행도 dev 브랜치를 선택한 경우에만 배포한다.
SSM 명령 전송 직전에 dev 최신 커밋인지 확인하고, 오래된 실행은 배포를 건너뛴다.
문서만 바뀐 푸시도 CI/CD를 시작해, 앞서 들어온 코드 변경이 최신 커밋에 포함되어 배포되도록 한다.
배포는 한 번에 하나씩 실행하며, 대기 중인 작업은 최대 100개까지 보존한다.

## 사전 준비

- 리전: ap-northeast-2
- App EC2: i-0350f972b242cd953
- EC2가 실행 중이며 SSM 상태가 Online이어야 한다.
- EC2에 AWS CLI, Docker, Docker Compose, curl, flock(util-linux)이 설치되어 있어야 한다.
- GitHub Actions 배포 역할에 ECR 업로드, S3 업로드, SSM 명령·결과 조회 권한이 필요하다.
- EC2 역할에 ECR 다운로드, 배포용 S3 읽기, Parameter Store 조회 권한이 필요하다.
- SecureString 복호화에 사용하는 KMS 키의 접근 권한도 충족해야 한다.

현재 단일 컨테이너 교체 방식이므로 배포 중 짧은 서비스 중단이 발생할 수 있다.

## GitHub Actions 변수

Server 저장소의 Settings → Secrets and variables → Actions → Variables에 등록한다.

| 이름 | 값 |
| --- | --- |
| AWS_ROLE_ARN | arn:aws:iam::727660953850:role/ooga_chaka_github_deploy_app |

AWS 인증은 OIDC를 사용한다.
개인 액세스 키나 앱 비밀값을 워크플로에 직접 작성하지 않는다.

## 실행 설정과 비밀값

현재 실행 설정은 다음 Parameter Store 파라미터에 SecureString으로 저장한다.

`/ooga/app/runtime-gwangmin-20261108-01`

이 경로는 현재 초기 구성에서 사용하는 이름이다.
변경 시 deploy.sh의 PARAMETER도 함께 변경한다.

EC2가 배포할 때 값을 조회하여 다음 파일에 저장한다.

`/opt/ooga/app/.env.runtime`

파일 권한은 관리자만 읽고 쓸 수 있는 600으로 설정한다.
실제 비밀값은 Git·PR·로그에 남기지 않는다.

주요 설정 항목:

- SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD
- SPRING_FLYWAY_URL, SPRING_FLYWAY_USER, SPRING_FLYWAY_PASSWORD
- REDIS_HOST, REDIS_PORT, REDIS_DATABASE, REDIS_USERNAME, REDIS_PASSWORD
- REDIS_SSL_ENABLED
- JASYPT_ENCRYPTOR_PASSWORD
- AES_ENCRYPTION_KEY
- HMAC_SOCIAL_ID_KEY, HMAC_DEVICE_TOKEN_KEY
- JWT_SECRET

활성화하는 기능에 따라 OAuth·FCM·CORS 등의 추가 설정이 필요할 수 있다.
DB와 Redis 주소는 EC2에서 접근 가능한 주소를 사용한다.
컨테이너에서 localhost는 해당 컨테이너 자신을 의미한다.

## 서버 배포 파일

| 경로 | 용도 |
| --- | --- |
| /opt/ooga/app/docker-compose.yml | 앱 실행 구성 |
| /opt/ooga/app/.env | APP_IMAGE 주소 |
| /opt/ooga/app/.env.runtime | 앱 실행 설정·비밀값 |

이미지는 Git 커밋 SHA로 태그하고, EC2에는 이미지 digest를 지정하여 배포한다.
배포 파일 묶음에는 docker-compose.yml과 deploy.sh만 포함한다.
실행 비밀값은 S3 배포 묶음에 포함하지 않는다.

## 기동 확인

현재는 컨테이너 실행 상태와 GET /의 HTTP 401 응답을 연속 3회 확인한다.
기본 Spring Security 인증 설정에 따른 임시 검사이며 실제 API 기능·Redis 연결까지 검증하지 않는다.

전용 헬스 체크 API를 추가하면 deploy.sh의 검사 경로와 성공 코드를 함께 변경한다.

## 실패 처리와 복구

새 설정 검사와 이미지 다운로드를 마친 뒤 기존 컨테이너를 갱신한다.
배포 스크립트가 8분을 넘기면 종료 신호를 보내고, 롤백에 최대 6분을 더 준다.
SSM 실행 제한은 17분이며, GitHub Actions는 명령 전달 시간을 포함해 최대 19분 동안 상태 조회를 시도한다.
배포 작업은 최대 60분이며, SSM 단계 21분과 작업 여유 1분을 확보할 수 있을 때만 배포 명령을 보낸다.
이미 App EC2에서 배포가 진행 중이면 새 실행은 배포 잠금을 얻지 못하고 종료한다.
다른 Compose 프로젝트의 `oogachaka-app`이 있거나 기존 실행 설정이 불완전하면 컨테이너 이름을 잠시 바꿔 보관한다.
보관할 컨테이너가 기본 `app` 프로젝트 소속이면, 새 컨테이너는 `ooga-app-migration` 프로젝트로 시작한다.
새 버전의 기동이 확인되면 보관한 컨테이너를 정리하고, 실패하면 원래 이름과 실행 상태를 복구한다.
기동 확인에 실패하면 이전 Compose·이미지 주소·실행 설정 복구를 시도한다.
복구된 앱의 기동까지 확인한 경우에만 임시 백업을 삭제한다.

복구 실패 시 관리자 전용 임시 폴더를 보존하고 SSM 출력에 백업 경로를 표시한다.
백업에는 비밀값이 있으므로 내용을 외부에 공유하지 않는다.
수동 복구를 완료한 뒤 해당 백업의 보관·삭제 여부를 판단한다.

이미지·설정 복구는 DB 마이그레이션을 되돌리지 않는다.
이전 앱과 호환되지 않는 DB 변경은 별도 복구 절차가 필요하다.

## 상태 확인

App EC2에 SSM으로 접속한 뒤 실행한다.

```bash
sudo docker ps -a --filter 'name=^/oogachaka-app$'
```

```bash
sudo docker logs --tail 60 oogachaka-app
```

로그를 공유할 때 생성된 비밀번호·토큰 등 비밀값을 제거한다.

GitHub Actions가 실패하면 실패 단계와 SSM command ID를 확인한다.
SSM 결과 조회 시간이 초과돼도 서버 명령은 계속 실행 중일 수 있으므로,
재배포 전에 해당 명령의 최종 상태를 확인한다.

## 검증 범위

초기 수동 검증에서 확인한 내용:

- 테스트와 실행용 JAR 빌드
- ARM64 이미지 빌드 및 ECR 업로드
- App EC2 실행과 RDS 연결
- Flyway 검증 및 DB 버전 2 확인
- 외부 HTTPS → Nginx → 백엔드 응답
- App EC2에서 Redis PING 인증 성공

GitHub Actions 전체 자동 배포와 실패 복구는 별도 실행 검증이 필요하다.
중앙 로그 수집은 이 CI/CD 구성에 포함하지 않는다.
