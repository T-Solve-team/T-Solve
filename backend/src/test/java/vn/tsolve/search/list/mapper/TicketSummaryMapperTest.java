package vn.tsolve.search.list.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import vn.tsolve.search.scope.entity.SearchTicket;

class TicketSummaryMapperTest {
    @Test
    void mapsOnlyPublicIdsAndJiraKeyWithNullDueDate_AC_006_5_AC_006_6_AC_006_7() {
        SearchTicket ticket = mock(SearchTicket.class);
        UUID publicId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Instant updatedAt = Instant.parse("2026-11-01T00:00:00Z");
        when(ticket.getPublicId()).thenReturn(publicId);
        when(ticket.getSourceTicketId()).thenReturn("ITSUP-1");
        when(ticket.getTitle()).thenReturn("VPN");
        when(ticket.getSource()).thenReturn("CSV");
        when(ticket.getStatus()).thenReturn("PUBLISHED");
        when(ticket.getProjectPublicId()).thenReturn(projectId);
        when(ticket.getProjectName()).thenReturn("ITSUP");
        when(ticket.getDepartmentPublicId()).thenReturn(departmentId);
        when(ticket.getDepartmentName()).thenReturn("IT");
        when(ticket.getUpdatedAt()).thenReturn(updatedAt);
        var row = Mappers.getMapper(TicketSummaryMapper.class).toResponse(ticket);
        assertThat(row.ticketId()).isEqualTo(publicId);
        assertThat(row.sourceTicketId()).isEqualTo("ITSUP-1");
        assertThat(row.project().projectId()).isEqualTo(projectId);
        assertThat(row.project().name()).isEqualTo("ITSUP");
        assertThat(row.department().departmentId()).isEqualTo(departmentId);
        assertThat(row.department().name()).isEqualTo("IT");
        assertThat(row.reviewDueDate()).isNull();
        assertThat(row.updatedAt()).isEqualTo(updatedAt);
    }
}
