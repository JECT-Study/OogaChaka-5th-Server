package com.ject.oogachaka.user.entity;

import com.ject.oogachaka.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

/** 사용자. 가족 가입 전에는 가족 구성원 ID가 없다. */
// [검토] 필드 값을 읽는 getter를 생성한다. 설정·엔티티를 읽는 호출부를 위해 사용하며 setter는 추가하지 않는다.
@Getter
// [검토] 기존 users 테이블을 JPA 엔티티로 다루어 인증 가입 흐름에서 저장할 수 있게 한다.
@Entity
// [검토] 클래스명 User 대신 실제 SQL 테이블명 users를 명시한다.
@Table(name = "users")
// [검토] 팩토리 내부에서만 빌더를 사용하게 한다. 외부에서 임의 ID·포인트를 넣어 생성하는 경로를 공개하지 않으려는 선택이다.
@Builder(access = AccessLevel.PRIVATE)
// [검토] JPA가 행을 로딩할 기본 생성자는 제공하면서 일반 호출자의 불완전한 객체 생성을 제한한다.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// [검토] Lombok 빌더가 호출할 전체 필드 생성자를 외부에 공개하지 않는다. 현재 로컬에서 정한 접근 수준을 그대로 유지한다.
@AllArgsConstructor(access = AccessLevel.PRIVATE)
// [검토] 사용자 기본 정보와 공통 생성·수정 시각을 저장한다. 역할·가족 가입은 이번 인증 흐름에서 부여하지 않는다.
public class User extends BaseTimeEntity {

    // [검토] 우리 서비스가 사용자를 식별할 내부 기본 키다. 소셜 제공자의 원본 ID와 구분한다.
    @Id
    // [검토] 기존 users.id AUTO_INCREMENT 스키마에 맞춰 DB가 ID를 생성하게 한다.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // [검토] JWT sub와 계정 연결에 사용할 사용자 ID다. 저장 전에는 아직 DB ID가 없어 null일 수 있다.
    private Long id;

    // [검토] 가족 구성원 한 명에 사용자 하나만 연결되는 기존 UNIQUE 메타데이터다. 가입 전 null을 허용한다.
    @Column(unique = true)
    // [검토] 소셜 로그인만 마친 신규 사용자는 가족 구성원이 없으므로 팩토리에서 값을 넣지 않는다.
    private Long familyMemberId;

    // [검토] 서비스에서 보완·자른 닉네임을 기존 필수 VARCHAR(30) 컬럼에 맞춰 저장한다.
    @Column(nullable = false, length = 30)
    // [검토] 프로필 이름이나 기본 이름으로 시작하며, 기존 사용자 로그인 시 자동 갱신하지 않는 현재 정책이다.
    private String nickname;

    // [검토] 포인트가 없는 상태를 null 대신 숫자 0으로 표현하는 기존 스키마에 맞춘다.
    @Column(nullable = false)
    // [검토] 기존 DB가 BIGINT인 포인트다. 이번 가입에서는 적립 계산 없이 0으로 시작한다.
    private long point;

    // [검토] 기존 프로필 이미지 URL 컬럼 길이를 매핑한다. 현재 소셜 로그인은 제공자 이미지를 저장하지 않는다.
    @Column(length = 2048)
    // [검토] 후속 프로필 기능을 위한 기존 컬럼이며 신규 생성 시에는 null로 남긴다.
    private String profileImageUrl;

    // [검토] 인증 서비스가 임의 포인트·가족 ID를 결정하지 않고 기본 사용자만 만들 수 있는 생성 경로다.
    public static User create(String nickname) {
        // [검토] 외부에는 숨긴 빌더로 필요한 가입 기본값만 지정한다. 빌더 자체가 필수 로직은 아니며 현재 엔티티 생성 방식이다.
        return User.builder()
                // [검토] 호출 서비스가 길이와 기본값을 보완한 닉네임을 사용한다.
                .nickname(nickname)
                // [검토] 가입 시 초기 포인트 0을 명시한다. primitive long의 기본값과 같지만 가입 규칙을 읽을 수 있게 표현한 선택이다.
                .point(0)
                // [검토] ID·가족 구성원·프로필 이미지는 지정하지 않은 사용자 객체를 완성하고 DB 저장은 UserService에 맡긴다.
                .build();
    }
}
