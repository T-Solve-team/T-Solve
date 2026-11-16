package vn.tsolve.wiki.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.wiki.dto.WikiDocumentDetail;
import vn.tsolve.wiki.dto.WikiFolder;
import vn.tsolve.wiki.dto.WikiSpace;
import vn.tsolve.wiki.repository.WikiSearchRepository;
import vn.tsolve.wiki.repository.WikiSearchRepository.SpaceAccess;

class WikiBrowseServiceImplTest {
    private final WikiSearchRepository repository = Mockito.mock(WikiSearchRepository.class);
    private final WikiBrowseServiceImpl service = new WikiBrowseServiceImpl(repository);
    private final UUID user = UUID.randomUUID();
    private final UUID space = UUID.randomUUID();

    @Test
    void returnsSpaceProjections_AC_002_1() {
        var spaces = List.of(new WikiSpace(space, "Wiki", "Support", "KNOWLEDGE_MANAGER"));
        when(repository.listSpaces(user)).thenReturn(spaces);
        assertThat(service.listSpaces(user)).isEqualTo(spaces);
    }

    @Test
    void validatesSpaceBeforeListingFolders_AC_002_3() {
        var folders = List.of(new WikiFolder(UUID.randomUUID(), null, "Guides", 1));
        when(repository.findSpace(user, space)).thenReturn(Optional.of(new SpaceAccess(42, true)));
        when(repository.listFolders(42)).thenReturn(folders);
        assertThat(service.listFolders(user, space)).isEqualTo(folders);
    }

    @Test
    void requestsRootOrValidatedFolder_AC_002_4() {
        UUID folder = UUID.randomUUID();
        when(repository.findSpace(user, space)).thenReturn(Optional.of(new SpaceAccess(42, true)));
        when(repository.isFolderInSpace(42, folder)).thenReturn(true);
        service.listDocuments(user, space, null);
        service.listDocuments(user, space, folder);
        verify(repository).listDocuments(user, 42, null);
        verify(repository).listDocuments(user, 42, folder);
    }

    @Test
    void deniesNonViewableActiveSpace_AC_002_5() {
        when(repository.findSpace(user, space)).thenReturn(Optional.of(new SpaceAccess(42, false)));
        assertThatThrownBy(() -> service.listFolders(user, space)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.listDocuments(user, space, null)).isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).listFolders(42);
        verify(repository, never()).listDocuments(user, 42, null);
    }

    @Test
    void rejectsUnknownSpaceAndForeignFolder_AC_002_6() {
        when(repository.findSpace(user, space)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listFolders(user, space))
                .isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode())
                        .isEqualTo("MSG04"));
        when(repository.findSpace(user, space)).thenReturn(Optional.of(new SpaceAccess(42, true)));
        UUID folder = UUID.randomUUID();
        assertThatThrownBy(() -> service.listDocuments(user, space, folder))
                .isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode())
                        .isEqualTo("MSG04"));
        verify(repository, never()).listDocuments(user, 42, folder);
    }

    @Test
    void returnsVisibleDetail_AC_002_11() {
        UUID id = UUID.randomUUID();
        var detail = new WikiDocumentDetail(
                id,
                "Guide",
                space,
                "Wiki",
                null,
                null,
                "DEPARTMENT",
                "Owner",
                Instant.EPOCH,
                "Text",
                "guide.pdf",
                42,
                false);
        when(repository.findDocument(user, id)).thenReturn(Optional.of(detail));
        assertThat(service.getDocument(user, id)).isEqualTo(detail);
    }

    @Test
    void hidesMissingOrInaccessibleDocuments_AC_002_13() {
        UUID id = UUID.randomUUID();
        when(repository.findDocument(user, id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getDocument(user, id))
                .isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode())
                        .isEqualTo("MSG110"));
    }
}
