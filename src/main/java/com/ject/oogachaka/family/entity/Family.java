package com.ject.oogachaka.family.entity;

import com.ject.oogachaka.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가족
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Family extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 가족 초대 코드 */
    @Column(length = 64, unique = true)
    private String inviteCode;

    private Family(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public static Family create(String inviteCode) {
        return new Family(inviteCode);
    }
}
