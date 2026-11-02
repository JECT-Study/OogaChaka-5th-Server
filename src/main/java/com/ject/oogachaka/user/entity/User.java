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
@Getter
@Entity
@Table(name = "users")
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long familyMemberId;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Column(nullable = false)
    private long point;

    @Column(length = 2048)
    private String profileImageUrl;

    public static User create(String nickname) {
        return User.builder()
                .nickname(nickname)
                .point(0)
                .build();
    }
}
