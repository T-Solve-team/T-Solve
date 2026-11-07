package vn.tsolve.project.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.tsolve.project.entity.Project;
import vn.tsolve.project.entity.ProjectStatus;
import vn.tsolve.project.repository.ProjectRepository;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    private static final UUID PROJECT = UUID.fromString("5b0e7a1c-3f2d-4e8b-9c11-2a7d4f6e8b01");

    @Mock
    private ProjectRepository projectRepository;

    @Test
    void findsTheActiveProjectOfAJiraKey_AC_003_1() {
        Project project = mock(Project.class);
        given(project.getPublicId()).willReturn(PROJECT);
        given(projectRepository.findByJiraProjectKeyAndStatus("ITSUP", ProjectStatus.ACTIVE))
                .willReturn(Optional.of(project));

        assertThat(service().findActivePublicIdByJiraProjectKey("ITSUP")).contains(PROJECT);
    }

    @Test
    void findsNothingWhenNoActiveProjectHasTheKey_AC_003_8() {
        given(projectRepository.findByJiraProjectKeyAndStatus("OTHER", ProjectStatus.ACTIVE))
                .willReturn(Optional.empty());

        assertThat(service().findActivePublicIdByJiraProjectKey("OTHER")).isEmpty();
    }

    private ProjectServiceImpl service() {
        return new ProjectServiceImpl(projectRepository);
    }
}
