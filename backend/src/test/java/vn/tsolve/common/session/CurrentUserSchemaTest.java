package vn.tsolve.common.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.core.converter.ModelConverters;
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
        assertThat(property("CurrentUser", "department").getNullable()).isTrue();

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
