package vn.tsolve.identity.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.tsolve.common.security.SecurityProblemWriter;
import vn.tsolve.identity.account.AccountRepository;
import vn.tsolve.identity.account.AccountRoleRepository;
import vn.tsolve.identity.signin.AuthHosts;
import vn.tsolve.identity.signin.IdentityClock;
import vn.tsolve.identity.signin.port.TenantScope;
import vn.tsolve.identity.signin.port.WorkspaceLookup;

/** The {@link WebSessionFilter} bean that {@code SecurityConfig} adds to the web chain (Q5: always). */
@Configuration(proxyBeanMethods = false)
class SessionConfig {

    @Bean
    WebSessionFilter webSessionFilter(
            WebSessionStore store,
            SessionCookie cookies,
            AuthHosts hosts,
            WorkspaceLookup workspaces,
            TenantScope scope,
            AccountRepository accounts,
            AccountRoleRepository roles,
            IdentityClock clock,
            ApplicationEventPublisher events,
            ObjectMapper mapper) {
        return new WebSessionFilter(
                store,
                cookies,
                hosts,
                workspaces,
                scope,
                accounts,
                roles,
                clock,
                events,
                new SecurityProblemWriter(mapper));
    }

    @Bean
    AccountRoleRepository accountRoleRepository(JdbcTemplate jdbc) {
        return new AccountRoleRepository(jdbc);
    }

    /** Boot would also add a filter bean to the servlet container, where it would run outside the web chain. */
    @Bean
    FilterRegistrationBean<WebSessionFilter> webSessionFilterRegistration(WebSessionFilter filter) {
        FilterRegistrationBean<WebSessionFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
