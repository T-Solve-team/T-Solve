package vn.tsolve.tracker.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import vn.tsolve.common.error.BusinessRuleException;

class IssueEventTest {

    private static final Instant NOW = Instant.parse("2026-10-23T10:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(60);

    private static IssueEvent newEvent() {
        return new IssueEvent(
                "cloud-1", "10042", "KAN-42", IssueEventType.ISSUE_CREATED, 1792400000000L, new byte[] {1, 2, 3}, NOW);
    }

    private static IssueEvent claimedEvent() {
        IssueEvent event = newEvent();
        event.claim(NOW);
        return event;
    }

    @Test
    void newEvent_isReceivedAndDueNow_AC_003_1() {
        IssueEvent event = newEvent();

        assertThat(event.getStatus()).isEqualTo(IssueEventStatus.RECEIVED);
        assertThat(event.getAttempts()).isEqualTo((short) 0);
        assertThat(event.getReceivedAt()).isEqualTo(NOW);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(event.getTokenCiphertext()).isNotNull();
        assertThat(event.isCommentPosted()).isFalse();
    }

    @Test
    void claim_movesToProcessing_AC_004_1() {
        IssueEvent event = newEvent();

        event.claim(LATER);

        assertThat(event.getStatus()).isEqualTo(IssueEventStatus.PROCESSING);
        assertThat(event.getClaimedAt()).isEqualTo(LATER);
    }

    @Test
    void claim_rejectsEventAlreadyClaimed_AC_004_1() {
        IssueEvent event = claimedEvent();

        assertThatThrownBy(() -> event.claim(LATER)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void scheduleRetry_returnsToQueueAndCountsAttempt_AC_004_5() {
        IssueEvent event = claimedEvent();

        event.scheduleRetry(LATER);

        assertThat(event.getStatus()).isEqualTo(IssueEventStatus.RECEIVED);
        assertThat(event.getAttempts()).isEqualTo((short) 1);
        assertThat(event.getNextAttemptAt()).isEqualTo(LATER);
        assertThat(event.getClaimedAt()).isNull();
        assertThat(event.getTokenCiphertext()).isNotNull();
    }

    @Test
    void markProcessed_deletesToken_AC_004_8() {
        IssueEvent event = claimedEvent();

        event.markProcessed(LATER);

        assertThat(event.getStatus()).isEqualTo(IssueEventStatus.PROCESSED);
        assertThat(event.getFinishedAt()).isEqualTo(LATER);
        assertThat(event.getTokenCiphertext()).isNull();
    }

    @Test
    void markIgnored_deletesToken_AC_004_8() {
        IssueEvent event = claimedEvent();

        event.markIgnored(LATER);

        assertThat(event.getStatus()).isEqualTo(IssueEventStatus.IGNORED);
        assertThat(event.getFinishedAt()).isEqualTo(LATER);
        assertThat(event.getTokenCiphertext()).isNull();
    }

    @Test
    void markFailed_recordsErrorTypeAndDeletesToken_AC_004_6() {
        IssueEvent event = claimedEvent();

        event.markFailed("JIRA_NOT_FOUND", LATER);

        assertThat(event.getStatus()).isEqualTo(IssueEventStatus.FAILED);
        assertThat(event.getErrorType()).isEqualTo("JIRA_NOT_FOUND");
        assertThat(event.getFinishedAt()).isEqualTo(LATER);
        assertThat(event.getTokenCiphertext()).isNull();
    }

    @Test
    void markProcessed_rejectsEventNotClaimed() {
        IssueEvent event = newEvent();

        assertThatThrownBy(() -> event.markProcessed(LATER)).isInstanceOf(BusinessRuleException.class);
        assertThat(event.getStatus()).isEqualTo(IssueEventStatus.RECEIVED);
        assertThat(event.getTokenCiphertext()).isNotNull();
    }

    @Test
    void markFailed_rejectsFinishedEventWithoutChangingIt() {
        IssueEvent event = claimedEvent();
        event.markProcessed(LATER);

        assertThatThrownBy(() -> event.markFailed("TOKEN_EXPIRED", LATER)).isInstanceOf(BusinessRuleException.class);
        assertThat(event.getStatus()).isEqualTo(IssueEventStatus.PROCESSED);
        assertThat(event.getErrorType()).isNull();
    }

    @Test
    void markCommentPosted_keepsStatusAndToken_AC_005_4() {
        IssueEvent event = claimedEvent();

        event.markCommentPosted();

        assertThat(event.isCommentPosted()).isTrue();
        assertThat(event.getStatus()).isEqualTo(IssueEventStatus.PROCESSING);
        assertThat(event.getTokenCiphertext()).isNotNull();
    }
}
