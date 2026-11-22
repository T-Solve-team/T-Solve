package vn.tsolve.tracker.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import vn.tsolve.tracker.dto.JiraAccount;
import vn.tsolve.tracker.dto.TrackerCreateField;
import vn.tsolve.tracker.dto.TrackerRole;
import vn.tsolve.tracker.dto.TrackerRoleActors;
import vn.tsolve.tracker.dto.TrackerStatus;
import vn.tsolve.tracker.exception.JiraRejectedException;
import vn.tsolve.tracker.exception.JiraUnavailableException;

/**
 * Jira Cloud REST API v3 as the Workspace's service account: its scoped API token as Bearer through the Atlassian
 * gateway {@code https://api.atlassian.com/ex/jira/{cloudId}} (AC-012.9, KAN-344). Errors keep only the HTTP status:
 * Jira's body may quote the issue, and the token never leaves this class (Non-Functional 4, 9).
 */
@Component
public class JiraClient implements TicketTrackerClient {

    // Jira lists comments oldest first by default; the newest page is enough to find the one T-Solve posted.
    private static final String COMMENTS = "/rest/api/3/issue/{issueKey}/comment?orderBy=-created&maxResults=100";

    private static final int PAGE_SIZE = 200;
    private static final String ISSUE_TYPES =
            "/rest/api/3/issue/createmeta/{projectKey}/issuetypes?startAt={startAt}&maxResults=" + PAGE_SIZE;
    private static final String CREATE_FIELDS = "/rest/api/3/issue/createmeta/{projectKey}/issuetypes/{issueTypeId}"
            + "?startAt={startAt}&maxResults=" + PAGE_SIZE;

    private static final String GATEWAY = "https://api.atlassian.com/ex/jira/";
    // Only a canonical UUID may become part of the gateway path (workspace AC-011.8).
    private static final Pattern CLOUD_ID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final RestClient.Builder builder;
    private final String gateway;

    @Autowired
    public JiraClient(
            @Value("${tsolve.tracker.jira.connect-timeout:5s}") Duration connectTimeout,
            @Value("${tsolve.tracker.jira.read-timeout:10s}") Duration readTimeout) {
        this(connectTimeout, readTimeout, GATEWAY);
    }

    /** For tests: real timeouts with the gateway replaced by a local stub server ({@code <base>/ex/jira/}). */
    public JiraClient(Duration connectTimeout, Duration readTimeout, String gateway) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.builder = RestClient.builder().requestFactory(factory);
        this.gateway = gateway;
    }

    /** For tests: a builder bound to a mock server. */
    public JiraClient(RestClient.Builder builder) {
        this.builder = builder;
        this.gateway = GATEWAY;
    }

    @Override
    public String addComment(JiraAccount account, String issueKey, List<String> paragraphs) {
        Map<String, Object> body = Map.of("body", adf(paragraphs));
        JsonNode created = call(() -> http(account)
                .post()
                .uri("/rest/api/3/issue/{issueKey}/comment", issueKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class));
        // A success without an id would mark the result as commented with nothing to point at (BUG-167).
        return requiredText(created, "id");
    }

    @Override
    public Optional<String> findComment(JiraAccount account, String issueKey, String textPrefix) {
        RestClient http = http(account);
        JsonNode me = call(() -> http.get().uri("/rest/api/3/myself").retrieve().body(JsonNode.class));
        // Without the account id no comment can be recognised as ours, and "not found" would post a second one
        // (BUG-168).
        String accountId = requiredText(me, "accountId");
        JsonNode page = call(() -> http.get().uri(COMMENTS, issueKey).retrieve().body(JsonNode.class));
        if (page == null) {
            throw new JiraUnavailableException(0);
        }
        for (JsonNode comment : page.path("comments")) {
            boolean ours =
                    accountId.equals(comment.path("author").path("accountId").asText());
            if (ours && AdfTextExtractor.toText(comment.path("body")).startsWith(textPrefix)) {
                return Optional.of(comment.path("id").asText());
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> findProject(JiraAccount account, String projectKey) {
        try {
            JsonNode project = call(() -> http(account)
                    .get()
                    .uri("/rest/api/3/project/{projectKey}", projectKey)
                    .retrieve()
                    .body(JsonNode.class));
            return Optional.of(requiredString(project, "key"));
        } catch (JiraRejectedException e) {
            if (e.getStatus() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public List<TrackerStatus> projectStatuses(JiraAccount account, String projectKey) {
        JsonNode issueTypes = call(() -> http(account)
                .get()
                .uri("/rest/api/3/project/{projectKey}/statuses", projectKey)
                .retrieve()
                .body(JsonNode.class));
        if (issueTypes == null || !issueTypes.isArray()) {
            throw new JiraUnavailableException(0);
        }
        List<TrackerStatus> statuses = new ArrayList<>();
        for (JsonNode issueType : issueTypes) {
            JsonNode issueStatuses = issueType.path("statuses");
            if (!issueStatuses.isArray()) {
                throw new JiraUnavailableException(0);
            }
            for (JsonNode status : issueStatuses) {
                String id = requiredString(status, "id");
                String name = requiredString(status, "name");
                boolean done =
                        "done".equals(status.path("statusCategory").path("key").asText());
                statuses.add(new TrackerStatus(id, name, done));
            }
        }
        return List.copyOf(statuses);
    }

    @Override
    public boolean hasProjectPermissions(JiraAccount account, String projectKey, List<String> permissions) {
        // The permission names are T-Solve's constants; only the project key comes from data, so it is a variable.
        JsonNode answer = call(() -> http(account)
                .get()
                .uri(
                        "/rest/api/3/mypermissions?projectKey={projectKey}&permissions="
                                + String.join(",", permissions),
                        projectKey)
                .retrieve()
                .body(JsonNode.class));
        if (answer == null || !answer.path("permissions").isObject()) {
            throw new JiraUnavailableException(0);
        }
        return permissions.stream().allMatch(permission -> answer.path("permissions")
                .path(permission)
                .path("havePermission")
                .booleanValue());
    }

    @Override
    public List<TrackerRole> projectRoles(JiraAccount account, String projectKey) {
        // {"Member": "https://…/rest/api/3/project/10001/role/10015", …}: the id is the last part of each link.
        JsonNode roles = call(() -> http(account)
                .get()
                .uri("/rest/api/3/project/{projectKey}/role", projectKey)
                .retrieve()
                .body(JsonNode.class));
        if (roles == null || !roles.isObject()) {
            throw new JiraUnavailableException(0);
        }
        List<TrackerRole> read = new ArrayList<>();
        roles.fields().forEachRemaining(role -> {
            String link = role.getValue().asText("");
            String id = link.substring(link.lastIndexOf('/') + 1);
            if (id.chars().allMatch(Character::isDigit) && !id.isEmpty()) {
                read.add(new TrackerRole(Long.parseLong(id), role.getKey()));
            }
        });
        return List.copyOf(read);
    }

    @Override
    public TrackerRoleActors roleActors(JiraAccount account, String projectKey, long roleId) {
        JsonNode role = call(() -> http(account)
                .get()
                .uri("/rest/api/3/project/{projectKey}/role/{roleId}", projectKey, roleId)
                .retrieve()
                .body(JsonNode.class));
        if (role == null || !role.path("actors").isArray()) {
            throw new JiraUnavailableException(0);
        }
        List<TrackerRoleActors.Person> people = new ArrayList<>();
        List<String> groups = new ArrayList<>();
        for (JsonNode actor : role.path("actors")) {
            String type = actor.path("type").asText("");
            if ("atlassian-user-role-actor".equals(type)) {
                String accountId = actor.path("actorUser").path("accountId").asText("");
                if (!accountId.isBlank()) {
                    String name = actor.path("displayName").asText("");
                    people.add(new TrackerRoleActors.Person(accountId, name.isBlank() ? accountId : name));
                }
            } else if ("atlassian-group-role-actor".equals(type)) {
                String name = actor.path("actorGroup")
                        .path("name")
                        .asText(actor.path("displayName").asText(""));
                if (!name.isBlank()) {
                    groups.add(name);
                }
            }
        }
        return new TrackerRoleActors(people, groups);
    }

    @Override
    public Optional<List<TrackerCreateField>> createFields(
            JiraAccount account, String projectKey, String issueTypeName) {
        RestClient http = http(account);
        Optional<String> issueTypeId = Optional.empty();
        for (JsonNode type : pages(http, ISSUE_TYPES, "issueTypes", projectKey)) {
            if (issueTypeName.equalsIgnoreCase(type.path("name").asText())
                    && !type.path("subtask").booleanValue()) {
                issueTypeId = Optional.of(requiredText(type, "id"));
                break;
            }
        }
        if (issueTypeId.isEmpty()) {
            return Optional.empty();
        }
        List<TrackerCreateField> fields = new ArrayList<>();
        for (JsonNode field : pages(http, CREATE_FIELDS, "fields", projectKey, issueTypeId.get())) {
            fields.add(new TrackerCreateField(
                    requiredText(field, "fieldId"),
                    field.path("required").booleanValue(),
                    field.path("hasDefaultValue").booleanValue()));
        }
        return Optional.of(List.copyOf(fields));
    }

    // Every item of a startAt/total paged list; {@code uri} ends with the startAt variable, after {@code args}.
    private List<JsonNode> pages(RestClient http, String uri, String listName, Object... args) {
        List<JsonNode> all = new ArrayList<>();
        while (true) {
            Object[] page = Arrays.copyOf(args, args.length + 1);
            page[args.length] = all.size();
            JsonNode answer = call(() -> http.get().uri(uri, page).retrieve().body(JsonNode.class));
            JsonNode items = items(answer, listName);
            items.forEach(all::add);
            if (items.isEmpty() || all.size() >= answer.path("total").asInt(0)) {
                return all;
            }
        }
    }

    // Jira Cloud names the list issueTypes / fields; older answers call it values.
    private static JsonNode items(JsonNode page, String name) {
        JsonNode items = page == null ? null : page.has(name) ? page.get(name) : page.get("values");
        if (items == null || !items.isArray()) {
            throw new JiraUnavailableException(0);
        }
        return items;
    }

    private static String requiredString(JsonNode node, String field) {
        if (node == null || !node.path(field).isTextual()) {
            throw new JiraUnavailableException(0);
        }
        return requiredText(node, field);
    }

    // An answer Jira should never give: treated as unavailable so the job retries rather than acting on it.
    private static String requiredText(JsonNode node, String field) {
        String value = node == null ? "" : node.path(field).asText("");
        if (value.isBlank()) {
            throw new JiraUnavailableException(0);
        }
        return value;
    }

    private RestClient http(JiraAccount account) {
        String cloudId = account.cloudId() == null ? "" : account.cloudId().toLowerCase(Locale.ROOT);
        if (!CLOUD_ID.matcher(cloudId).matches()
                || account.apiToken() == null
                || account.apiToken().isBlank()) {
            throw new JiraUnavailableException(0);
        }
        return builder.clone()
                .baseUrl(gateway + cloudId)
                .defaultHeaders(headers -> headers.setBearerAuth(account.apiToken()))
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 429 || status >= 500) {
                throw new JiraUnavailableException(status);
            }
            throw new JiraRejectedException(status);
        } catch (RestClientException e) {
            // No answer: connection refused, timeout, unreadable body.
            throw new JiraUnavailableException(0);
        } catch (IllegalArgumentException e) {
            // A malformed credential or response header leaves no usable answer.
            throw new JiraUnavailableException(0);
        }
    }

    private static Map<String, Object> adf(List<String> paragraphs) {
        List<Map<String, Object>> content = paragraphs.stream()
                .map(text -> Map.<String, Object>of(
                        "type", "paragraph", "content", List.of(Map.of("type", "text", "text", text))))
                .toList();
        return Map.of("type", "doc", "version", 1, "content", content);
    }
}
