package com.ject.oogachaka.mission.event;

/**
 * 자녀가 미션 완료 인증을 요청했을 때 발행한다.
 * 미션 서비스가 완료 요청을 저장하는 트랜잭션 안에서 발행해야 커밋 후 부모에게 푸시가 나간다.
 */
public record MissionCompletionRequestedEvent(Long missionId, Long childUserId, String missionTitle) {
}
