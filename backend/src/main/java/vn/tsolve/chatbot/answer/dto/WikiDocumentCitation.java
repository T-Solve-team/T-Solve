package vn.tsolve.chatbot.answer.dto;

import java.util.UUID;

public record WikiDocumentCitation(int number, UUID wikiDocumentId, String title) implements ChatbotCitation {}
