package vn.tsolve.chatbot.source.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import vn.tsolve.common.security.TSolveUser;

public interface WikiSources {
    /** Passages the user may view, nearest to the shared 1024-dimensional bge-m3 vector, most similar first. */
    List<WikiPassage> nearest(TSolveUser user, float[] queryVector, double minSimilarity, int limit);

    /**
     * Passages a member of the Department of the Project (public id) may read, for a question asked in Jira without a
     * T-Solve user (chatbot STORY-012); none for an unknown Project.
     */
    List<WikiPassage> nearestInProjectDepartment(UUID projectId, float[] queryVector, double minSimilarity, int limit);

    /** Given ids the user cannot view: unknown, deleted, unpublished or inaccessible documents. */
    Set<UUID> unavailable(TSolveUser user, Set<UUID> wikiDocumentIds);

    record WikiPassage(UUID wikiDocumentId, String title, String text, double similarity) {}
}
