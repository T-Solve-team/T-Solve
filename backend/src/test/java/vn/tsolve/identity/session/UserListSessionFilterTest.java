package vn.tsolve.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.tsolve.common.security.SecurityProblemWriter;
import vn.tsolve.common.tenant.TenantContext;
import vn.tsolve.identity.repository.UserListRepository;
import vn.tsolve.identity.service.impl.UserListAccessServiceImpl;
import vn.tsolve.identity.service.impl.UserListTransaction;

class UserListSessionFilterTest {
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsInvalidSessionBeforeConnectionWithCanonicalEnvelope_AC_004_16_BUG_113_BUG_115() throws Exception {
        var transactions = mock(UserListTransaction.class);
        var context = mock(TenantContext.class);
        when(context.currentWorkspaceId())
                .thenReturn(Optional.of(UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001")));
        var repository = mock(UserListRepository.class);
        var mapper = Jackson2ObjectMapperBuilder.json()
                .build()
                .addMixIn(
                        org.springframework.http.ProblemDetail.class,
                        org.springframework.http.converter.json.ProblemDetailJacksonMixin.class);
        var filter = new UserListSessionFilter(
                transactions, new UserListAccessServiceImpl(context, repository), new SecurityProblemWriter(mapper));
        for (Object principal : new Object[] {null, "untrusted", UserListAccessServiceTest.user()}) {
            SecurityContextHolder.clearContext();
            if (principal != null) {
                SecurityContextHolder.getContext()
                        .setAuthentication(UserListAccessServiceTest.authenticated(principal, UUID.randomUUID()));
            }
            var original = SecurityContextHolder.getContext();
            var response = new MockHttpServletResponse();
            var request = new MockHttpServletRequest("GET", "/api/v1/users");
            request.setQueryString("token=must-not-appear");
            assertThatCode(() -> filter.doFilter(request, response, new MockFilterChain()))
                    .doesNotThrowAnyException();
            assertThat(response.getStatus()).isEqualTo(401);
            var json = mapper.readTree(response.getContentAsString());
            assertThat(json.path("code").asText()).isEqualTo("MSG07");
            assertThat(json.path("detail").asText()).isEqualTo("Your session has expired. Please log in again.");
            assertThat(json.path("params").isObject()).isTrue();
            assertThat(json.path("instance").asText()).isEqualTo("/api/v1/users");
            assertThat(response.getContentAsString()).doesNotContain("must-not-appear");
            assertThat(SecurityContextHolder.getContext()).isSameAs(original);
        }
        verifyNoInteractions(repository);
        org.mockito.Mockito.verify(transactions, org.mockito.Mockito.never()).read(any());
    }
}
