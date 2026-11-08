# oogachaka

안녕하세요. 우가차차(우리 같이 차근차근) 백엔드 레포 입니다.  ^^

로컬 실행과 환경변수·설정 암호화 방법은 [환경 설정 가이드](docs/environment-setup.md)를 참고하세요.

## 로컬 개발 DB

로컬 개발은 공용 DB 대신 도커로 띄운 MySQL·Redis를 사용합니다.

```bash
docker compose up -d      # 켜기 (프로젝트 루트에서 실행)
docker compose stop       # 끄기 (데이터 유지)
docker compose down -v    # 데이터까지 삭제하고 초기화
```

**왜 도커 DB를 쓰나요?**

- 앱을 켜면 Flyway가 그 브랜치의 마이그레이션을 연결된 DB에 바로 적용합니다.
  로컬에서 공용 DB(테스트 서버와 같이 쓰는 DB)에 붙으면 아직 병합되지 않은 SQL이 공용 DB에 들어갑니다.
- 도커 DB는 내 PC에만 있어 마이그레이션을 마음대로 실행하고 `down -v`로 처음부터 다시 확인할 수 있습니다.

접속 정보와 `application-local.properties` 설정은 [환경 설정 가이드](docs/environment-setup.md)의 2단계를 참고하세요.
