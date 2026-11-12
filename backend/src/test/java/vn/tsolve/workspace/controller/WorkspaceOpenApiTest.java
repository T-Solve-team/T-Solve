package vn.tsolve.workspace.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.tsolve.workspace.dto.WorkspaceInfoResponse;
import vn.tsolve.workspace.dto.WorkspaceSetupRequest;

class WorkspaceOpenApiTest {
    @Test
    @DisplayName("AC-001.1 OpenAPI describes the four required setup fields and write-only token")
    void describesSetup() throws Exception {
        Schema<?> schema =
                ModelConverters.getInstance().read(WorkspaceSetupRequest.class).get("WorkspaceSetupRequest");
        assertThat(schema.getProperties())
                .containsOnlyKeys("companyName", "jiraSiteUrl", "serviceAccountEmail", "serviceAccountToken");
        assertThat(schema.getRequired())
                .containsExactlyInAnyOrder("companyName", "jiraSiteUrl", "serviceAccountEmail", "serviceAccountToken");
        assertThat(schema.getProperties().get("companyName").getMaxLength()).isEqualTo(100);
        assertThat(schema.getProperties().get("jiraSiteUrl").getMaxLength()).isEqualTo(200);
        assertThat(schema.getProperties().get("serviceAccountEmail").getFormat())
                .isEqualTo("email");
        assertThat(schema.getProperties().get("serviceAccountToken").getWriteOnly())
                .isTrue();
        assertThat(WorkspaceController.class
                        .getMethod("setup", String.class)
                        .getAnnotation(Operation.class)
                        .operationId())
                .isEqualTo("setupWorkspace");
    }

    @Test
    @DisplayName("AC-002.2 OpenAPI exposes exactly three required public Workspace fields and getWorkspace")
    void describesWorkspaceInfo() throws Exception {
        Schema<?> schema =
                ModelConverters.getInstance().read(WorkspaceInfoResponse.class).get("WorkspaceInfo");
        assertThat(schema.getProperties()).containsOnlyKeys("companyName", "jiraSiteUrl", "serviceAccountEmail");
        assertThat(schema.getRequired()).containsExactlyInAnyOrder("companyName", "jiraSiteUrl", "serviceAccountEmail");
        assertThat(WorkspaceController.class
                        .getMethod("get")
                        .getAnnotation(Operation.class)
                        .operationId())
                .isEqualTo("getWorkspace");
    }
}
