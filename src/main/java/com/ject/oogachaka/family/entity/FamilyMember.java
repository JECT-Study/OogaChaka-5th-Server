package com.ject.oogachaka.family.entity;

import com.ject.oogachaka.family.enums.FamilyRole;
import com.ject.oogachaka.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 가족 구성원
 */
@Getter
@Entity
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FamilyMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long familyId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private FamilyRole familyRole;

    /** 가족에 처음 연결된 일시 */
    @Column
    private LocalDateTime connectedAt;

    public static FamilyMember create(Long familyId, FamilyRole familyRole) {
        return FamilyMember.builder()
                .familyId(familyId)
                .familyRole(familyRole)
                .build();
    }

    public void connect(LocalDateTime connectedAt) {
        this.connectedAt = connectedAt;
    }
}
