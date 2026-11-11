package vn.tsolve.identity.session;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.tsolve.identity.TwoTenantDatabases;
import vn.tsolve.identity.session.SessionTestFixtures.MutableClock;
import vn.tsolve.identity.signin.SignInTestFixtures;
import vn.tsolve.identity.signin.port.InMemoryWorkspaceLookup;

/**
 * Shared set-up of the session tests: the whole application, two company databases plus the application's own
 * database, a movable clock and the in-memory Workspaces. A subclass adds its Redis with {@code
 * @DynamicPropertySource}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TwoTenantDatabases.class, SessionTestFixtures.Beans.class})
@TestPropertySource(
        properties = {
            "TSOLVE_LOGIN_ORIGIN=" + SignInTestFixtures.LOGIN_ORIGIN,
            "TSOLVE_ADMIN_ORIGIN=" + SignInTestFixtures.ADMIN_ORIGIN,
            "PLATFORM_OPERATOR_EMAILS=ops@example.test",
            "CORS_ALLOWED_ORIGINS=http://localhost:5173",
            "server.port=0",
            "DB_HOST=unused",
            "DB_PORT=0",
            "DB_NAME=unused",
            "DB_USER=unused",
            "DB_PASSWORD=unused",
            "logging.level.vn.tsolve.identity=DEBUG"
        })
public abstract class AbstractSessionIT {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected StringRedisTemplate redis;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected InMemoryWorkspaceLookup workspaces;

    @BeforeEach
    protected void resetSharedState() {
        clock.reset();
        workspaces.reset();
        try {
            redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        } catch (RuntimeException e) {
            // Redis-down tests have nothing to flush
        }
    }

    protected MockHttpServletResponse perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }
}
