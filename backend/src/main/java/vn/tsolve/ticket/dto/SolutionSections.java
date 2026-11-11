package vn.tsolve.ticket.dto;

/**
 * The five Solution sections of the Solution Form (GBR-33), as the Jira App sends them; Workaround is the only
 * optional one. A missing section counts as empty.
 */
public record SolutionSections(
        String symptoms, String rootCause, String resolutionSteps, String workaround, String scopeOfApplicability) {

    public SolutionSections {
        symptoms = orEmpty(symptoms);
        rootCause = orEmpty(rootCause);
        resolutionSteps = orEmpty(resolutionSteps);
        workaround = orEmpty(workaround);
        scopeOfApplicability = orEmpty(scopeOfApplicability);
    }

    /** All five sections empty: the form was closed without a Solution (AC-006.2). */
    public boolean isEmpty() {
        return symptoms.isBlank()
                && rootCause.isBlank()
                && resolutionSteps.isBlank()
                && workaround.isBlank()
                && scopeOfApplicability.isBlank();
    }

    /** The length of the five sections together, in code points, so an emoji counts once (MSG02). */
    public int length() {
        return count(symptoms)
                + count(rootCause)
                + count(resolutionSteps)
                + count(workaround)
                + count(scopeOfApplicability);
    }

    private static int count(String text) {
        return text.codePointCount(0, text.length());
    }

    private static String orEmpty(String text) {
        return text == null ? "" : text;
    }
}
