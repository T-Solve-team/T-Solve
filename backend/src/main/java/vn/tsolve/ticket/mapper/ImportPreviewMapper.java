package vn.tsolve.ticket.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import vn.tsolve.ticket.dto.ImportPreviewRowResponse;
import vn.tsolve.ticket.dto.ImportPreviewRowSummary;
import vn.tsolve.ticket.entity.ImportPreviewRow;

@Mapper
public interface ImportPreviewMapper {

    int SNIPPET_LENGTH = 200;

    @Mapping(target = "ticketId", source = "values.ticketId")
    @Mapping(target = "title", source = "values.title")
    @Mapping(target = "author", source = "values.author")
    @Mapping(target = "resolvedDate", source = "values.resolvedDate")
    @Mapping(target = "symptoms", source = "values.symptoms", qualifiedByName = "snippet")
    @Mapping(target = "rootCause", source = "values.rootCause", qualifiedByName = "snippet")
    @Mapping(target = "resolutionSteps", source = "values.resolutionSteps", qualifiedByName = "snippet")
    @Mapping(target = "workaround", source = "values.workaround", qualifiedByName = "snippet")
    @Mapping(target = "scope", source = "values.scope", qualifiedByName = "snippet")
    ImportPreviewRowSummary toSummary(ImportPreviewRow row);

    @Mapping(target = "ticketId", source = "values.ticketId")
    @Mapping(target = "title", source = "values.title")
    @Mapping(target = "description", source = "values.description")
    @Mapping(target = "author", source = "values.author")
    @Mapping(target = "resolvedDate", source = "values.resolvedDate")
    @Mapping(target = "symptoms", source = "values.symptoms")
    @Mapping(target = "rootCause", source = "values.rootCause")
    @Mapping(target = "resolutionSteps", source = "values.resolutionSteps")
    @Mapping(target = "workaround", source = "values.workaround")
    @Mapping(target = "scope", source = "values.scope")
    ImportPreviewRowResponse toResponse(ImportPreviewRow row);

    /** The first 200 characters, counted in code points so an emoji is never split. */
    @Named("snippet")
    default String snippet(String text) {
        if (text == null || text.codePointCount(0, text.length()) <= SNIPPET_LENGTH) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, SNIPPET_LENGTH));
    }
}
