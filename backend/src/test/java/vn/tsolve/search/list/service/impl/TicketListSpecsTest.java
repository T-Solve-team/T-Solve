package vn.tsolve.search.list.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.tsolve.search.scope.entity.SearchTicket;
import vn.tsolve.search.scope.service.VisibleScope.Scope;

class TicketListSpecsTest {
    @Test
    @SuppressWarnings("unchecked")
    void filtersManagedTicketsByUuidProjectId_BUG_140() throws Exception {
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        Scope scope = new Scope(Set.of(), Set.of(projectId), Optional.empty());
        Root<SearchTicket> ticket = mock(Root.class);
        Path<UUID> project = mock(Path.class);
        when(ticket.<UUID>get("projectId")).thenReturn(project);
        when(ticket.get("status")).thenReturn(mock(Path.class));
        when(ticket.get("departmentStatus")).thenReturn(mock(Path.class));

        TicketListSpecs.visible(scope).toPredicate(ticket, null, mock(CriteriaBuilder.class));

        assertThat(scope.managedProjectIds()).containsExactly(projectId);
        assertThat(SearchTicket.class.getMethod("getProjectId").getReturnType()).isEqualTo(UUID.class);
        verify(project).in(Set.of(projectId));
    }
}
