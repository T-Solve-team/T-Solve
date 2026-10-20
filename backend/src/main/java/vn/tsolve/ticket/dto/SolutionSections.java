package vn.tsolve.ticket.dto;

public record SolutionSections(
        String symptoms, String rootCause, String resolutionSteps, String workaround, String scopeOfApplicability) {
    public boolean isEmpty() {
        return java.util.stream.Stream.of(symptoms, rootCause, resolutionSteps, workaround, scopeOfApplicability)
                .allMatch(value -> value == null || value.isBlank());
    }
}
