package vn.tsolve.wiki.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import vn.tsolve.wiki.entity.WikiDocument.Visibility;

public record WikiPublishMetadata(
        UUID folderId, @NotNull(message = "MSG01") @Valid List<Document> documents) {
    public record Document(
            @NotBlank(message = "MSG01") @Size(max = 200, message = "MSG02")
            String title,

            @NotNull(message = "MSG01") Visibility visibility) {}
}
