package vn.tsolve.chatbot.answer.dto;

public record CitedSolution(
        String symptoms, String rootCause, String resolutionSteps, String workaround, String scopeOfApplicability) {}
