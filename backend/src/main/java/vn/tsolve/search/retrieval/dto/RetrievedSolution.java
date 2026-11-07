package vn.tsolve.search.retrieval.dto;

public record RetrievedSolution(
        String symptoms,
        String rootCause,
        String resolutionSteps,
        String workaround,
        String scopeOfApplicability,
        double similarity) {}
