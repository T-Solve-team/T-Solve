package vn.tsolve.project.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.tsolve.project.entity.Project;
import vn.tsolve.project.entity.ProjectStatus;
import vn.tsolve.project.repository.ProjectRepository;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    @Mock
    private ProjectRepository projectRepository;

    @Test
    void findsTheActiveProjectOfAJiraKey_AC_003_1() {
        Project project = mock(Project.class);
        given(project.getId()).willReturn(4L);
        given(projectRepository.findByJiraProjectKeyAndStatus("ITSUP", ProjectStatus.ACTIVE))
                .willReturn(Optional.of(project));

        assertThat(service().findActiveIdByJiraProjectKey("ITSUP")).contains(4L);
    }

    @Test
    void findsNothingWhenNoActiveProjectHasTheKey_AC_003_8() {
        given(projectRepository.findByJiraProjectKeyAndStatus("OTHER", ProjectStatus.ACTIVE))
                .willReturn(Optional.empty());

        assertThat(service().findActiveIdByJiraProjectKey("OTHER")).isEmpty();
    }

    private ProjectServiceImpl service() {
        return new ProjectServiceImpl(projectRepository);
    }
}
