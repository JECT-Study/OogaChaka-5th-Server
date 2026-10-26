# DB 스키마 및 ENUM 관리

## Flyway

Spring Boot Flyway starter와 MySQL 지원 모듈을 사용한다.
Flyway는 애플리케이션 시작 시 설정된 데이터소스에 마이그레이션을 적용한다.
이후 Hibernate가 `spring.jpa.hibernate.ddl-auto=validate`로 엔티티 매핑을 검증한다.
`spring.sql.init.mode=never`로 별도의 schema.sql/data.sql 초기화는 사용하지 않는다.
DB 연결 정보는 환경에 맞게 별도로 설정한다.

마이그레이션 위치: `src/main/resources/db/migration/`

파일명: `V{버전}__{변경내용}.sql`

- `V1__init_schema.sql`, `V2__add_task_status.sql`처럼 작성한다.
- 버전은 순차 증가하며, 변경내용은 영문 소문자와 언더스코어를 사용한다.
- 버전 구분자 `V`는 대문자이고, 버전과 설명 사이는 언더스코어 두 개다.
- 초기 설계는 로컬 DB에서 실험하고, 확정된 초기 스키마를 V1에 작성한다.
- 현재는 폴더만 준비했다. 빈 V1을 먼저 적용하지 않는다.
- 공용 테스트 DB에 적용된 파일은 수정하거나 삭제하지 않는다.
- 이후 변경은 새로운 버전의 SQL로 추가한다. 기존 파일의 체크섬 검증을 유지한다.
- 병합 전에 다른 팀원의 마이그레이션과 버전 충돌 여부를 확인한다.
- 공용 테스트 DB에는 팀 공용 개발 브랜치에 병합된 마이그레이션만 적용한다.
- 로컬 실험용 애플리케이션은 로컬 DB에 연결한다. 공용 DB 연결 정보는 공용 배포 환경에서 관리한다.
- 공용 DB에 직접 ALTER TABLE 등을 실행하지 않는다.

병합된 마이그레이션만 공용 DB에 적용하는 규칙은 배포 파이프라인과 DB 접근 권한으로
운영해야 한다. Flyway 설정 자체가 Git 브랜치를 확인하지는 않는다.

## ENUM

ENUM 타입은 PascalCase, 상수는 UPPER_SNAKE_CASE로 작성한다.
상태·역할·유형처럼 정해진 값만 허용하는 데이터에 사용한다.

```text
com.ject.oogachaka
├── user/enums                # 사용자 도메인 ENUM
├── task/enums                # task 도메인 구현 시 추가
└── global/enums              # 실제로 공유하는 ENUM이 생길 때 추가
```

기존 구조에 맞춰 공통 패키지는 `global`을 사용한다.
`entity/enums` 대신 각 도메인 바로 아래 `enums`에 둔다.
`ErrorCode`, `SuccessCode`는 기존의 `global.response.code`에서 관리한다.

영속 필드는 `@Enumerated(EnumType.STRING)`으로 저장한다. ORDINAL은 사용하지 않는다.
MySQL에서 Hibernate의 네이티브 ENUM 매핑 대신 VARCHAR를 사용하도록 JDBC 타입도 명시한다.
다음은 실제 엔티티를 작성할 때 사용하는 예시다.

```java
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Enumerated(EnumType.STRING)
@JdbcTypeCode(SqlTypes.VARCHAR)
@Column(nullable = false, length = 20)
private TaskStatus status;
```

Flyway SQL의 해당 컬럼도 `status VARCHAR(20) NOT NULL`처럼 타입과 길이를 맞춘다.
길이는 허용하는 ENUM 이름을 모두 저장할 수 있도록 정한다.

- 기존 상수의 이름을 임의로 변경하거나 삭제하지 않는다.
- 상수 추가 시 해당 값을 사용하는 비즈니스 로직을 함께 확인한다.
- 이름 변경·삭제 시 DB 데이터, API 응답, 관련 로직에 미치는 영향을 검토한다.
- DB 데이터 변경이나 컬럼 길이 변경이 필요하면 새 Flyway 마이그레이션을 작성한다.

참고: [Spring Boot DB 초기화](https://docs.spring.io/spring-boot/how-to/data-initialization.html),
[Hibernate ENUM 매핑](https://docs.hibernate.org/orm/7.3/introduction/html_single/).
