package vn.tsolve.wiki.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.FieldMessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.wiki.dto.GrantWikiPermissionsRequest;
import vn.tsolve.wiki.dto.WikiPermissionCandidates;
import vn.tsolve.wiki.dto.WikiSpaceSettings;
import vn.tsolve.wiki.repository.AccessRepository;
import vn.tsolve.wiki.repository.WikiPermissionRepository;

@ExtendWith(MockitoExtension.class)
class WikiPermissionServiceImplTest {
    @Mock
    private WikiPermissionRepository permissions;

    @Mock
    private AccessRepository access;

    @Mock
    private AuditService audit;

    private WikiPermissionServiceImpl service;
    private final UUID manager = UUID.randomUUID();
    private final UUID space = UUID.randomUUID();
    private final UUID department = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private final UUID permission = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new WikiPermissionServiceImpl(permissions, access, audit);
    }

    private void managed() {
        when(permissions.findActiveSpace(space)).thenReturn(Optional.of(1L));
        when(access.managesSpace(manager, 1L)).thenReturn(true);
    }

    @Test
    void readsSettingsOnlyAfterManagerCheck_AC_006_1() {
        managed();
        var settings = new WikiSpaceSettings(
                space,
                "Wiki",
                "Support",
                "Manager",
                new WikiSpaceSettings.OwnDepartment(department, "Support", true),
                List.of(),
                List.of());
        when(permissions.getSettings(1L)).thenReturn(settings);
        assertThat(service.getSettings(manager, space)).isEqualTo(settings);
    }

    @Test
    void readsCandidatesOnlyAfterManagerCheck_AC_006_2() {
        managed();
        var candidates = new WikiPermissionCandidates(List.of(), List.of());
        when(permissions.listCandidates(1L)).thenReturn(candidates);
        assertThat(service.listCandidates(manager, space)).isEqualTo(candidates);
    }

    @Test
    void storesDistinctIdsAndAuditsOnlyNewPermissions_AC_006_3_AC_006_9() {
        managed();
        when(permissions.findActiveDepartment(1L, department)).thenReturn(Optional.of(2L));
        when(permissions.isActiveUser(user)).thenReturn(true);
        when(permissions.insertDepartment(eq(1L), eq(2L), eq(manager), any())).thenReturn(1);
        when(permissions.insertUser(eq(1L), eq(user), eq(manager), any())).thenReturn(0);
        service.grant(
                manager, space, new GrantWikiPermissionsRequest(List.of(department, department), List.of(user, user)));
        verify(permissions).insertDepartment(eq(1L), eq(2L), eq(manager), any());
        verify(permissions).insertUser(eq(1L), eq(user), eq(manager), any());
        verify(audit)
                .record(
                        AuditAction.PERMISSION_CHANGE,
                        "WIKI_SPACE",
                        space,
                        Map.of("operation", "GRANT", "granteeType", "DEPARTMENT", "granteeId", department));
        verify(audit, never())
                .record(
                        eq(AuditAction.PERMISSION_CHANGE),
                        any(),
                        any(),
                        eq(Map.of("operation", "GRANT", "granteeType", "USER", "granteeId", user)));
    }

    @Test
    void rejectsEmptySelectionOnGrantees_AC_006_4() {
        managed();
        assertThatThrownBy(() -> service.grant(manager, space, new GrantWikiPermissionsRequest(List.of(), List.of())))
                .isInstanceOfSatisfying(FieldMessageCodeException.class, error -> {
                    assertThat(error.getStatus().value()).isEqualTo(400);
                    assertThat(error.getCode()).isEqualTo("MSG01");
                    assertThat(error.getField()).isEqualTo("grantees");
                    assertThat(error.getParams()).containsEntry("field_name", "grantees");
                });
        verifyNoWrites();
    }

    @Test
    void validatesEntireBatchBeforeAnyInsert_AC_006_5() {
        managed();
        when(permissions.findActiveDepartment(1L, department)).thenReturn(Optional.of(2L));
        when(permissions.isActiveUser(user)).thenReturn(false);
        assertThatThrownBy(() -> service.grant(
                        manager, space, new GrantWikiPermissionsRequest(List.of(department), List.of(user))))
                .isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode())
                        .isEqualTo("MSG04"));
        verifyNoWrites();
    }

    @Test
    void rejectsUnknownInactiveOrOwnDepartment_AC_006_5() {
        managed();
        when(permissions.findActiveDepartment(1L, department)).thenReturn(Optional.empty());
        assertThatThrownBy(() ->
                        service.grant(manager, space, new GrantWikiPermissionsRequest(List.of(department), List.of())))
                .isInstanceOf(NotFoundException.class);
        verifyNoWrites();
    }

    @Test
    void revokesAndAuditsExactGrantee_AC_006_6_AC_006_9() {
        managed();
        when(permissions.findPermission(permission))
                .thenReturn(Optional.of(new WikiPermissionRepository.Permission(space, "USER", user)));
        when(permissions.deletePermission(permission)).thenReturn(1);
        service.revoke(manager, permission);
        verify(audit)
                .record(
                        AuditAction.PERMISSION_CHANGE,
                        "WIKI_SPACE",
                        space,
                        Map.of("operation", "REVOKE", "granteeType", "USER", "granteeId", user));
    }

    @Test
    void hidesUnknownPermission_AC_006_7() {
        when(permissions.findPermission(permission)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.revoke(manager, permission)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(access, audit);
    }

    @Test
    void hidesPermissionOutsideManagedSpaces_AC_006_7() {
        when(permissions.findPermission(permission))
                .thenReturn(Optional.of(new WikiPermissionRepository.Permission(space, "DEPARTMENT", department)));
        when(permissions.findActiveSpace(space)).thenReturn(Optional.of(1L));
        when(access.managesSpace(manager, 1L)).thenReturn(false);
        assertThatThrownBy(() -> service.revoke(manager, permission)).isInstanceOf(NotFoundException.class);
        verify(permissions, never()).deletePermission(any());
        verifyNoInteractions(audit);
    }

    @Test
    void doesNotAuditPermissionAlreadyRevokedByAnotherRequest_AC_006_7_AC_006_9() {
        managed();
        when(permissions.findPermission(permission))
                .thenReturn(Optional.of(new WikiPermissionRepository.Permission(space, "USER", user)));
        when(permissions.deletePermission(permission)).thenReturn(0);
        assertThatThrownBy(() -> service.revoke(manager, permission)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SETTINGS", "CANDIDATES", "GRANT"})
    void deniesCurrentNonManager_AC_006_8(String operation) {
        when(permissions.findActiveSpace(space)).thenReturn(Optional.of(1L));
        when(access.managesSpace(manager, 1L)).thenReturn(false);
        assertThatThrownBy(() -> call(operation)).isInstanceOf(AccessDeniedException.class);
        verify(permissions, never()).getSettings(anyLong());
        verify(permissions, never()).listCandidates(anyLong());
        verifyNoWrites();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SETTINGS", "CANDIDATES", "GRANT"})
    void rejectsUnknownHiddenOrDeletedDepartmentSpace_AC_006_8(String operation) {
        when(permissions.findActiveSpace(space)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> call(operation)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(access);
        verifyNoWrites();
    }

    private void call(String operation) {
        switch (operation) {
            case "SETTINGS" -> service.getSettings(manager, space);
            case "CANDIDATES" -> service.listCandidates(manager, space);
            case "GRANT" ->
                service.grant(manager, space, new GrantWikiPermissionsRequest(List.of(department), List.of()));
            default -> throw new IllegalArgumentException(operation);
        }
    }

    private void verifyNoWrites() {
        verify(permissions, never()).insertDepartment(anyLong(), anyLong(), any(), any());
        verify(permissions, never()).insertUser(anyLong(), any(), any(), any());
        verifyNoInteractions(audit);
    }
}
