package vn.tsolve.wiki.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import vn.tsolve.common.audit.AuditAction;
import vn.tsolve.common.audit.AuditService;
import vn.tsolve.common.exception.BusinessRuleException;
import vn.tsolve.common.exception.MessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.wiki.dto.CreateWikiFolderRequest;
import vn.tsolve.wiki.dto.UpdateWikiFolderRequest;
import vn.tsolve.wiki.entity.Folder;
import vn.tsolve.wiki.mapper.WikiFolderMapper;
import vn.tsolve.wiki.repository.AccessRepository;
import vn.tsolve.wiki.repository.FolderRepository;
import vn.tsolve.wiki.repository.WikiDocumentRepository;

class WikiFolderServiceImplTest {
    private final FolderRepository folders = Mockito.mock(FolderRepository.class);
    private final WikiDocumentRepository documents = Mockito.mock(WikiDocumentRepository.class);
    private final AccessRepository access = Mockito.mock(AccessRepository.class);
    private final AuditService audit = Mockito.mock(AuditService.class);
    private final WikiFolderServiceImpl service =
            new WikiFolderServiceImpl(folders, documents, access, audit, Mappers.getMapper(WikiFolderMapper.class));
    private final UUID user = UUID.randomUUID();
    private final UUID space = UUID.randomUUID();
    private final UUID id = UUID.randomUUID();
    private Folder folder;

    @BeforeEach
    void setup() {
        folder = folder(id, null, "Guides", 1);
        when(access.lockActiveSpace(space)).thenReturn(Optional.of(42L));
        when(access.managesSpace(user, 42)).thenReturn(true);
        when(access.findFolderSpace(id)).thenReturn(Optional.of(space));
        when(folders.findById(id)).thenReturn(Optional.of(folder));
        when(folders.findSubtree(id)).thenReturn(List.of(folder));
        when(folders.saveAndFlush(any())).thenAnswer(call -> {
            Folder saved = call.getArgument(0);
            if (saved.getId() == null) ReflectionTestUtils.setField(saved, "id", id);
            return saved;
        });
    }

    @Test
    void createsStrippedRootAndOneAuditEntry_AC_003_1_AC_003_11() {
        var result = service.create(user, space, new CreateWikiFolderRequest("\u2003 Guides \n", null));
        assertThat(result.folderId()).isEqualTo(id);
        assertThat(result.name()).isEqualTo("Guides");
        assertThat(result.parentId()).isNull();
        assertThat(result.level()).isEqualTo(1);
        verify(audit).record(AuditAction.FOLDER_CHANGE, "WIKI_FOLDER", id, details("CREATE", "Guides", null));
    }

    @Test
    void createsAtLevelThreeUnderSameSpaceParent_AC_003_1() {
        UUID parent = UUID.randomUUID();
        when(folders.findById(parent)).thenReturn(Optional.of(folder(parent, null, "Parent", 2)));
        var result = service.create(user, space, new CreateWikiFolderRequest("A".repeat(100), parent));
        assertThat(result.parentId()).isEqualTo(parent);
        assertThat(result.level()).isEqualTo(3);
        assertThat(result.name()).hasSize(100);
    }

    @Test
    void renamesAndMovesWholeSubtreeAndRecordsAudit_AC_003_2_AC_003_11() {
        UUID childId = UUID.randomUUID();
        Folder child = folder(childId, id, "Child", 2);
        UUID parent = UUID.randomUUID();
        when(folders.findById(parent)).thenReturn(Optional.of(folder(parent, null, "Destination", 1)));
        when(folders.findSubtree(id)).thenReturn(List.of(folder, child));
        var result = service.update(user, id, new UpdateWikiFolderRequest(" New name ", parent));
        assertThat(result.name()).isEqualTo("New name");
        assertThat(result.parentId()).isEqualTo(parent);
        assertThat(result.level()).isEqualTo(2);
        assertThat(child.getLevel()).isEqualTo((short) 3);
        assertThat(child.getParentId()).isEqualTo(id);
        assertThat(child.getUpdatedAt()).isAfter(Instant.EPOCH);
        verifyNoInteractions(documents);
        verify(audit).record(AuditAction.FOLDER_CHANGE, "WIKI_FOLDER", id, details("UPDATE", "New name", parent));
    }

    @Test
    void movesToRootAndRetainsDescendantStructure_AC_003_2() {
        UUID parent = UUID.randomUUID();
        folder = folder(id, parent, "Guides", 2);
        Folder child = folder(UUID.randomUUID(), id, "Child", 3);
        when(folders.findById(id)).thenReturn(Optional.of(folder));
        when(folders.findSubtree(id)).thenReturn(List.of(folder, child));
        var result = service.update(user, id, new UpdateWikiFolderRequest("Guides", null));
        assertThat(result.level()).isEqualTo(1);
        assertThat(child.getLevel()).isEqualTo((short) 2);
        assertThat(child.getParentId()).isEqualTo(id);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mapsOnlySiblingNameConstraintToFieldConflict_AC_003_4_AC_003_12_BUG_222(boolean update) {
        var duplicate = new DataIntegrityViolationException(
                "duplicate", new ConstraintViolationException("duplicate", null, "uq_folder_parent_name"));
        doThrow(duplicate).when(folders).saveAndFlush(any());
        assertThatThrownBy(() -> {
                    if (update) service.update(user, id, new UpdateWikiFolderRequest("guides", null));
                    else service.create(user, space, new CreateWikiFolderRequest("guides", null));
                })
                .isInstanceOfSatisfying(BusinessRuleException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("MSG85");
                    assertThat(error.getField()).isEqualTo("name");
                });
        verifyNoInteractions(audit);
    }

    @Test
    void doesNotMislabelUnrelatedDatabaseFailure_AC_003_4_BUG_222() {
        var failure = new DataIntegrityViolationException("other constraint");
        doThrow(failure).when(folders).saveAndFlush(any());
        assertThatThrownBy(() -> service.create(user, space, new CreateWikiFolderRequest("Guides", null)))
                .isSameAs(failure);
        verifyNoInteractions(audit);
    }

    @Test
    void rejectsCreatingLevelFour_AC_003_5() {
        UUID parent = UUID.randomUUID();
        when(folders.findById(parent)).thenReturn(Optional.of(folder(parent, null, "Deep", 3)));
        assertInvalidParent(() -> service.create(user, space, new CreateWikiFolderRequest("New", parent)));
        verify(folders, never()).saveAndFlush(any());
    }

    @Test
    void rejectsMoveWhenGrandchildWouldBecomeLevelFour_AC_003_5() {
        UUID parent = UUID.randomUUID();
        Folder child = folder(UUID.randomUUID(), id, "Child", 2);
        Folder grandchild = folder(UUID.randomUUID(), child.getId(), "Grandchild", 3);
        when(folders.findSubtree(id)).thenReturn(List.of(folder, child, grandchild));
        when(folders.findById(parent)).thenReturn(Optional.of(folder(parent, null, "Destination", 1)));
        assertInvalidParent(() -> service.update(user, id, new UpdateWikiFolderRequest("Guides", parent)));
        assertThat(folder.getParentId()).isNull();
        assertThat(grandchild.getLevel()).isEqualTo((short) 3);
        verify(folders, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsSelfAndDescendantParents_AC_003_6(boolean self) {
        Folder child = folder(UUID.randomUUID(), id, "Child", 2);
        when(folders.findSubtree(id)).thenReturn(List.of(folder, child));
        assertInvalidParent(
                () -> service.update(user, id, new UpdateWikiFolderRequest("Guides", self ? id : child.getId())));
        verify(folders, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsUnknownAndForeignParentsOnCreateAndUpdate_AC_003_7(boolean foreign) {
        UUID parent = UUID.randomUUID();
        if (foreign) {
            Folder other = new Folder(99, null, "Other", (short) 1, user, Instant.EPOCH);
            when(folders.findById(parent)).thenReturn(Optional.of(other));
        }
        assertMissing(() -> service.create(user, space, new CreateWikiFolderRequest("Guides", parent)));
        assertMissing(() -> service.update(user, id, new UpdateWikiFolderRequest("Guides", parent)));
        verifyNoInteractions(audit);
    }

    @Test
    void rejectsUnknownFolderForUpdateAndDelete_AC_003_7() {
        UUID unknown = UUID.randomUUID();
        assertMissing(() -> service.update(user, unknown, new UpdateWikiFolderRequest("Guides", null)));
        assertMissing(() -> service.delete(user, unknown));
        verifyNoInteractions(audit);
    }

    @Test
    void rejectsMissingOrInactiveSpace_AC_003_7() {
        when(access.lockActiveSpace(space)).thenReturn(Optional.empty());
        assertMissing(() -> service.create(user, space, new CreateWikiFolderRequest("Guides", null)));
        assertMissing(() -> service.update(user, id, new UpdateWikiFolderRequest("Guides", null)));
        assertMissing(() -> service.delete(user, id));
        verifyNoInteractions(audit);
    }

    @Test
    void deletesEmptyFolderAndAuditsItsPreviousParent_AC_003_8_AC_003_11() {
        service.delete(user, id);
        verify(folders).delete(folder);
        verify(folders).flush();
        verify(audit).record(AuditAction.FOLDER_CHANGE, "WIKI_FOLDER", id, details("DELETE", "Guides", null));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 2})
    void refusesNonemptyFolderWithExactDirectCounts_AC_003_9(long children) {
        when(folders.countByParentId(id)).thenReturn(children);
        when(documents.countByFolderId(id)).thenReturn(1L);
        assertThatThrownBy(() -> service.delete(user, id))
                .isInstanceOfSatisfying(BusinessRuleException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("MSG87");
                    assertThat(error.getParams())
                            .containsExactlyInAnyOrderEntriesOf(Map.of("folder_count", children, "document_count", 1L));
                });
        verify(folders, never()).delete(any());
        verifyNoInteractions(audit);
    }

    @Test
    void refusesFolderContainingOnlySubfolders_AC_003_9() {
        when(folders.countByParentId(id)).thenReturn(1L);
        assertThatThrownBy(() -> service.delete(user, id)).isInstanceOf(BusinessRuleException.class);
        verify(folders, never()).delete(any());
    }

    @Test
    void rechecksManagementFromDatabaseBeforeAnyMutation_AC_003_10() {
        when(access.managesSpace(user, 42)).thenReturn(false);
        assertThatThrownBy(() -> service.create(user, space, new CreateWikiFolderRequest("Guides", null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.update(user, id, new UpdateWikiFolderRequest("Guides", null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.delete(user, id)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(documents, folders, audit);
    }

    @Test
    void propagatesAuditFailureSoTransactionRollsBack_AC_003_11() {
        doThrow(new IllegalStateException("audit unavailable")).when(audit).record(any(), any(), any(), any());
        assertThatThrownBy(() -> service.create(user, space, new CreateWikiFolderRequest("Guides", null)))
                .isInstanceOf(IllegalStateException.class);
    }

    private Folder folder(UUID folderId, UUID parent, String name, int level) {
        Folder value = new Folder(42, parent, name, (short) level, user, Instant.EPOCH);
        ReflectionTestUtils.setField(value, "id", folderId);
        return value;
    }

    private Map<String, Object> details(String operation, String name, UUID parent) {
        Map<String, Object> values = new HashMap<>();
        values.put("operation", operation);
        values.put("name", name);
        values.put("spaceId", space);
        values.put("parentId", parent);
        return values;
    }

    private void assertInvalidParent(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(MessageCodeException.class, error -> {
            assertThat(error.getCode()).isEqualTo("MSG86");
            assertThat(error.getField()).isEqualTo("parentId");
        });
        verifyNoInteractions(audit);
    }

    private void assertMissing(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode())
                .isEqualTo("MSG04"));
    }
}
