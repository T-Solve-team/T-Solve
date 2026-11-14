package vn.tsolve.common.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.tsolve.common.security.Role;
import vn.tsolve.common.security.TSolveUser;
import vn.tsolve.common.session.lookup.StubWorkspaceStatusLookup;
import vn.tsolve.common.session.lookup.WorkspaceStatusLookup;
import vn.tsolve.common.session.service.SessionService;
import vn.tsolve.common.session.service.impl.SessionServiceImpl;

/** BUG-205 / KAN-329: the staging stub for AC-003.7; AC-003.4 stays true when the flag is false or unset. */
class StubWorkspaceStatusLookupTest {
    private static final String FLAG = StubWorkspaceStatusLookup.PROPERTY;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SessionServiceImpl.class, StubWorkspaceStatusLookup.class);

    @BeforeEach
    void signIn() {
        var user = new TSolveUser(UUID.randomUUID(), "ada@example.test", "Ada Lovelace", Set.of(Role.ADMIN));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities()));
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void flagTrueRegistersOneLookupAndMeIsWorkspaceReady_BUG_205_AC_003_7() {
        runner.withPropertyValues(FLAG + "=true").run(context -> {
            assertThat(context).hasSingleBean(WorkspaceStatusLookup.class);
            assertThat(context.getBean(SessionService.class).getMe().workspaceReady())
                    .isTrue();
        });
    }

    @Test
    void flagFalseRegistersNoLookupAndMeIsNotReady_BUG_205_AC_003_7_AC_003_4() {
        runner.withPropertyValues(FLAG + "=false").run(context -> {
            assertThat(context).doesNotHaveBean(WorkspaceStatusLookup.class);
            assertThat(context.getBean(SessionService.class).getMe().workspaceReady())
                    .isFalse();
        });
    }

    @Test
    void flagUnsetRegistersNoLookupAndMeIsNotReady_BUG_205_AC_003_7_AC_003_4() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(WorkspaceStatusLookup.class);
            assertThat(context.getBean(SessionService.class).getMe().workspaceReady())
                    .isFalse();
        });
    }
}
