package com.ject.oogachaka.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ject.oogachaka.mission.event.MissionCompletionRequestedEvent;
import org.junit.jupiter.api.Test;

class PushNotificationTest {

    @Test
    void longMissionTitleIsAcceptedByTheEventAndShortenedOnlyInThePushPayload() {
        String original = "가".repeat(100);
        var event = new MissionCompletionRequestedEvent(10L, 3L, original);

        var notification = PushNotification.missionCompletionRequested(event, "자녀");

        assertThat(event.missionTitle()).isEqualTo(original);
        assertThat(notification.body()).isEqualTo("자녀님이 '" + "가".repeat(63) + "…' 완료 인증을 요청했어요");
        assertThat(notification.data()).containsEntry("missionId", "10").containsEntry("childUserId", "3");
    }

    @Test
    void exactlySixtyFourCodePointsAreKeptIncludingSupplementaryCharacters() {
        String original = "😀".repeat(64);
        var event = new MissionCompletionRequestedEvent(10L, 3L, original);

        assertThat(PushNotification.missionCompletionRequested(event, "자녀").body())
            .isEqualTo("자녀님이 '" + original + "' 완료 인증을 요청했어요");
    }

    @Test
    void shorteningDoesNotSplitASupplementaryCharacter() {
        var event = new MissionCompletionRequestedEvent(10L, 3L, "😀".repeat(65));

        assertThat(PushNotification.missionCompletionRequested(event, "자녀").body())
            .isEqualTo("자녀님이 '" + "😀".repeat(63) + "…' 완료 인증을 요청했어요");
    }

    @Test
    void blankTitleUsesADisplayFallbackWithoutRejectingTheMissionEvent() {
        var event = new MissionCompletionRequestedEvent(10L, 3L, " \t\n");

        assertThat(PushNotification.missionCompletionRequested(event, "자녀").body())
            .isEqualTo("자녀님이 '미션' 완료 인증을 요청했어요");
        assertThat(event.missionTitle()).isEqualTo(" \t\n");
    }
}
