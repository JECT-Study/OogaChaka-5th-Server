package com.ject.oogachaka.mission.event;

/**
 * 자녀의 미션 완료 인증 제출이 저장되었음을 알리는 이벤트.
 *
 * <p>향후 5-44 {@code POST /api/v1/missions/{id}/completion-requests} 처리 서비스에서
 * 사용자/가족/미션 담당 자녀 권한을 검증하고 인증 요청을 저장한 뒤, 같은 쓰기 트랜잭션 안에서
 * {@code ApplicationEventPublisher.publishEvent(new MissionCompletionRequestedEvent(
 * mission.getId(), childUserId, mission.getTitle()))}를 호출한다.
 * 트랜잭션 밖에서 발행하거나 롤백하면 푸시 리스너가 실행되지 않는다.
 * 미션 엔티티나 개인정보 대신 발행 시점의 미션 제목과 식별자만 전달한다.
 * 푸시 표시용 제목 길이와 공백 처리는 커밋 후 알림 생성 시점에 적용한다.
 */
public record MissionCompletionRequestedEvent(Long missionId, Long childUserId, String missionTitle) {

    public MissionCompletionRequestedEvent {
        if (missionId == null || missionId <= 0 || childUserId == null || childUserId <= 0
            || missionTitle == null) {
            throw new IllegalArgumentException("미션 완료 인증 요청 이벤트의 필수 값이 올바르지 않습니다.");
        }
    }
}
