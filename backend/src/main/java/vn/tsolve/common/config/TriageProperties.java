package vn.tsolve.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the AI Solution Suggestion (triage module): the cosine similarity a source needs, from
 * {@code SUGGESTION_MIN_SIMILARITY} (AC-005.11).
 */
@ConfigurationProperties(prefix = "tsolve.triage")
public record TriageProperties(@DefaultValue("0.7") double minSimilarity) {

    public TriageProperties {
        // Written so that NaN fails too; a failed binding stops the application starting.
        if (!(minSimilarity >= 0 && minSimilarity <= 1)) {
            throw new IllegalArgumentException("tsolve.triage.min-similarity must be a number from 0 to 1");
        }
    }
}
