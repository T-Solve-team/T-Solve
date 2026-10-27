package vn.tsolve.tracker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import vn.tsolve.tracker.dto.IssueEventPayload;
import vn.tsolve.tracker.entity.IssueEvent;
import vn.tsolve.tracker.entity.IssueEventStatus;
import vn.tsolve.tracker.entity.IssueEventType;
import vn.tsolve.tracker.repository.IssueEventRepository;

@ExtendWith(MockitoExtension.class)
class IssueEventServiceImplTest {

    private static final IssueEventPayload PAYLOAD =
            new IssueEventPayload(new IssueEventPayload.Issue("10001", "ITSUP-1"), "1730000000000", null);

    @Mock
    private IssueEventRepository repository;

    @Mock
    private TokenCipher tokenCipher;

    @Test
    void recordsCreatedEventAsReceivedWithEncryptedToken_AC_003_1() {
        byte[] sealed = {1, 2, 3};
        given(tokenCipher.encrypt("system-token")).willReturn(sealed);

        service().receive("cloud-1", IssueEventType.ISSUE_CREATED, PAYLOAD, "system-token");

        ArgumentCaptor<IssueEvent> saved = ArgumentCaptor.forClass(IssueEvent.class);
        verify(repository).saveAndFlush(saved.capture());
        IssueEvent event = saved.getValue();
        assertThat(event.getStatus()).isEqualTo(IssueEventStatus.RECEIVED);
        assertThat(event.getCloudId()).isEqualTo("cloud-1");
        assertThat(event.getIssueId()).isEqualTo("10001");
        assertThat(event.getIssueKey()).isEqualTo("ITSUP-1");
        assertThat(event.getEventType()).isEqualTo(IssueEventType.ISSUE_CREATED);
        assertThat(event.getEventTimestamp()).isEqualTo(1730000000000L);
        assertThat(event.getTokenCiphertext()).isEqualTo(sealed);
        assertThat(event.getReceivedAt()).isNotNull();
    }

    @Test
    void acceptsEventAlreadyRecorded_AC_003_4() {
        given(repository.saveAndFlush(any())).willThrow(violation(IssueEventServiceImpl.UNIQUE_EVENT));

        assertThatCode(() -> service().receive("cloud-1", IssueEventType.ISSUE_CREATED, PAYLOAD, "t"))
                .doesNotThrowAnyException();
    }

    @Test
    void rethrowsOtherIntegrityErrors_AC_003_1() {
        given(repository.saveAndFlush(any())).willThrow(violation("issue_event_workspace_id_fkey"));

        assertThatThrownBy(() -> service().receive("cloud-1", IssueEventType.ISSUE_CREATED, PAYLOAD, "t"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private IssueEventServiceImpl service() {
        return new IssueEventServiceImpl(repository, tokenCipher);
    }

    private static DataIntegrityViolationException violation(String constraint) {
        return new DataIntegrityViolationException(
                "insert failed", new ConstraintViolationException("insert failed", new SQLException(), constraint));
    }
}
