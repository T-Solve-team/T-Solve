package vn.tsolve.workspace.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.tsolve.workspace.dto.WorkspaceInfoResponse;
import vn.tsolve.workspace.dto.WorkspaceSetupRequest;
import vn.tsolve.workspace.dto.WorkspaceUpdateRequest;

class WorkspaceOpenApiTest {
    @Test
    @DisplayName("AC-007.1 OpenAPI describes updateWorkspace with the company name only")
    void describesUpdate() throws Exception {
        Schema<?> schema =
                ModelConverters.getInstance().read(WorkspaceUpdateRequest.class).get("WorkspaceUpdateRequest");
        assertThat(schema.getProperties()).containsOnlyKeys("companyName");
        assertThat(schema.getRequired()).containsExactlyInAnyOrder("companyName");
        assertThat(schema.getProperties().get("companyName").getMaxLength()).isEqualTo(100);
        assertThat(WorkspaceController.class
                        .getMethod("update", String.class)
                        .getAnnotation(Operation.class)
                        .operationId())
                .isEqualTo("updateWorkspace");
    }

    @Test
    @DisplayName("AC-005.1 OpenAPI describes the two required setup fields and no service account field")
    void describesSetup() throws Exception {
        Schema<?> schema =
                ModelConverters.getInstance().read(WorkspaceSetupRequest.class).get("WorkspaceSetupRequest");
        assertThat(schema.getProperties()).containsOnlyKeys("companyName", "jiraSiteUrl");
        assertThat(schema.getRequired()).containsExactlyInAnyOrder("companyName", "jiraSiteUrl");
        assertThat(schema.getProperties().get("companyName").getMaxLength()).isEqualTo(100);
        assertThat(schema.getProperties().get("jiraSiteUrl").getMaxLength()).isEqualTo(200);
        assertThat(WorkspaceController.class
                        .getMethod("setup", String.class)
                        .getAnnotation(Operation.class)
                        .operationId())
                .isEqualTo("setupWorkspace");
    }

    @Test
    @DisplayName("AC-006.2 AC-006.6 OpenAPI exposes the company, the site and the Jira App status, and getWorkspace")
    void describesWorkspaceInfo() throws Exception {
        Schema<?> schema =
                ModelConverters.getInstance().read(WorkspaceInfoResponse.class).get("WorkspaceInfo");
        assertThat(schema.getProperties())
                .containsOnlyKeys("companyName", "jiraSiteUrl", "jiraAppConnected", "jiraAppTokenReceivedAt");
        assertThat(schema.getRequired())
                .containsExactlyInAnyOrder("companyName", "jiraSiteUrl", "jiraAppConnected", "jiraAppTokenReceivedAt");
        assertThat(WorkspaceController.class
                        .getMethod("get")
                        .getAnnotation(Operation.class)
                        .operationId())
                .isEqualTo("getWorkspace");
    }
}
