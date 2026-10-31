package com.ject.oogachaka.notification.service;

import java.util.Optional;

/** 가족 도메인이 구현되면 해당 도메인의 조회 서비스로 교체할 수 있는 읽기 전용 포트 */
public interface ParentPushRecipientPort {

    Optional<ParentPushRecipients> findByChildUserId(Long childUserId);
}
