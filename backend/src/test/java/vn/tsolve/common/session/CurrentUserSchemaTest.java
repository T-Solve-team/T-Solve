package vn.tsolve.common.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.tsolve.common.session.dto.CurrentUserResponse;

class CurrentUserSchemaTest {
    private final Map<String, Schema> schemas = ModelConverters.getInstance().readAll(CurrentUserResponse.class);

    @Test
    void schemasMatchTheContract_AC_003_6_edge17() {
        // Names: the records are CurrentUserResponse / UserSummaryResponse, the contract names are different.
        assertThat(schemas.keySet())
                .contains("CurrentUser", "UserSummary", "DepartmentRef", "ProjectMembership", "SpaceRef")
                .doesNotContain("CurrentUserResponse", "UserSummaryResponse");

        // Every field is required in the contract; department is required and nullable.
        assertThat(required("CurrentUser"))
                .containsExactlyInAnyOrder(
                        "user", "roles", "department", "departments", "projects", "managedSpaces", "workspaceReady");
        assertThat(required("UserSummary")).containsExactlyInAnyOrder("id", "fullName", "email");
        assertThat(required("ProjectMembership"))
                .containsExactlyInAnyOrder(
                        "projectId", "jiraProjectKey", "departmentId", "departmentName", "roleInProject");
        assertThat(required("DepartmentRef")).containsExactlyInAnyOrder("departmentId", "name");
        assertThat(required("SpaceRef")).containsExactlyInAnyOrder("spaceId", "name");
        // Nullable must survive serialisation: the written schema is what `api:pull` reads.
        JsonNode department = written("CurrentUser").at("/properties/department");
        assertThat(department.at("/allOf/0/$ref").asText()).isEqualTo("#/components/schemas/DepartmentRef");
        assertThat(department.path("nullable").asBoolean())
                .as("written nullable")
                .isTrue();

        // Every id is a UUID string.
        assertUuid("UserSummary", "id");
        assertUuid("DepartmentRef", "departmentId");
        assertUuid("ProjectMembership", "projectId");
        assertUuid("ProjectMembership", "departmentId");
        assertUuid("SpaceRef", "spaceId");

        // Enum values: the converter may inline an enum or register it as its own schema.
        assertThat(enumValues(((ArraySchema) property("CurrentUser", "roles")).getItems(), "Role"))
                .containsExactlyInAnyOrder(
                        "EMPLOYEE", "TICKET_RESOLVER", "PROJECT_MANAGER", "KNOWLEDGE_MANAGER", "ADMIN");
        assertThat(enumValues(property("ProjectMembership", "roleInProject"), "ProjectRole"))
                .containsExactlyInAnyOrder("PROJECT_MANAGER", "MEMBER");
    }

    @Test
    void writtenSchemasMatchTheContract_AC_003_6() {
        assertThat(written("UserSummary").at("/properties/email/format").asText())
                .isEqualTo("email");
        assertThat(schemas.keySet()).contains("Role", "ProjectRole");
        assertThat(written("CurrentUser").at("/properties/roles/items/$ref").asText())
                .isEqualTo("#/components/schemas/Role");
        assertThat(written("ProjectMembership")
                        .at("/properties/roleInProject/$ref")
                        .asText())
                .isEqualTo("#/components/schemas/ProjectRole");
    }

    private JsonNode written(String schemaName) {
        try {
            return Json.mapper().readTree(Json.mapper().writeValueAsString(schemas.get(schemaName)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> required(String schemaName) {
        return schemas.get(schemaName).getRequired();
    }

    private Schema<?> property(String schemaName, String propertyName) {
        return (Schema<?>) schemas.get(schemaName).getProperties().get(propertyName);
    }

    private void assertUuid(String schemaName, String propertyName) {
        var property = property(schemaName, propertyName);
        assertThat(property.getType())
                .as(schemaName + "." + propertyName + " type")
                .isEqualTo("string");
        assertThat(property.getFormat())
                .as(schemaName + "." + propertyName + " format")
                .isEqualTo("uuid");
    }

    private List<String> enumValues(Schema<?> schema, String registeredName) {
        Schema<?> source = schema.getEnum() != null ? schema : schemas.get(registeredName);
        assertThat(source).as("enum schema " + registeredName).isNotNull();
        return source.getEnum().stream().map(Object::toString).toList();
    }
}
