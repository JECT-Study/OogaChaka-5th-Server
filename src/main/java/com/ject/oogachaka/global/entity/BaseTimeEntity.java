package com.ject.oogachaka.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;

/**
 * 생성일과 수정일을 관리하는 엔티티의 공통 부모 클래스
 */
@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseTimeEntity extends BaseCreatedEntity {

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
