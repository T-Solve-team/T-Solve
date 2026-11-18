package vn.tsolve.tracker.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.dto.TrackerRole;
import vn.tsolve.tracker.dto.TrackerRoleActors;
import vn.tsolve.tracker.exception.JiraRejectedException;

/** project-integration STORY-011: the Jira project roles, read as the T-Solve Jira App. */
class JiraClientRolesTest {

    private static final String CLOUD_ID = "1324a887-45db-1bf4-1e99-ef0ff456d421";
    private static final JiraAccount ACCOUNT = new JiraAccount("https://acme.atlassian.net", CLOUD_ID, "app-token");
    private static final String BASE = "https://api.atlassian.com/ex/jira/" + CLOUD_ID + "/rest/api/3/project/DP/role";

    private MockRestServiceServer jira;
    private JiraClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        jira = MockRestServiceServer.bindTo(builder).build();
        client = new JiraClient(builder);
    }

    @Test
    void readsTheRoleIdsFromTheRoleLinks_AC_011_1() {
        jira.expect(requestTo(BASE))
                .andExpect(header("Authorization", "Bearer app-token"))
                .andRespond(withSuccess("""
                        {"Member": "https://acme.atlassian.net/rest/api/3/project/10001/role/10015",
                         "Administrator": "https://acme.atlassian.net/rest/api/3/project/10001/role/10014",
                         "Broken": "not-a-role-link"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.projectRoles(ACCOUNT, "DP"))
                .containsExactly(new TrackerRole(10015, "Member"), new TrackerRole(10014, "Administrator"));
        jira.verify();
    }

    @Test
    void readsUserActorsAndGroupNamesOfARole_AC_011_2_AC_011_3() {
        jira.expect(requestTo(BASE + "/10015")).andRespond(withSuccess("""
                        {"name": "Member", "id": 10015, "actors": [
                          {"type": "atlassian-user-role-actor", "displayName": "An", "actorUser": {"accountId": "j-1"}},
                          {"type": "atlassian-user-role-actor", "displayName": "", "actorUser": {"accountId": "j-2"}},
                          {"type": "atlassian-group-role-actor", "displayName": "IT team",
                           "actorGroup": {"name": "it-support-team", "groupId": "g-1"}},
                          {"type": "atlassian-user-role-actor", "displayName": "No id", "actorUser": {}},
                          {"type": "something-else", "displayName": "?"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        TrackerRoleActors actors = client.roleActors(ACCOUNT, "DP", 10015);

        assertThat(actors.people())
                .containsExactly(new TrackerRoleActors.Person("j-1", "An"), new TrackerRoleActors.Person("j-2", "j-2"));
        assertThat(actors.groups()).isEqualTo(List.of("it-support-team"));
        jira.verify();
    }

    @Test
    void reportsARefusedRoleAsARejection_AC_011_4() {
        jira.expect(requestTo(BASE + "/999")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.roleActors(ACCOUNT, "DP", 999)).isInstanceOf(JiraRejectedException.class);
    }
}
