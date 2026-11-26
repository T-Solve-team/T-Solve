package vn.tsolve.chatbot.answer.entity;

import java.util.UUID;

/**
 * One kept source of an answer, stored for debugging only and never returned (AC-014.1, AC-014.3): {@code kind} is
 * {@code TICKET} or {@code WIKI}, {@code id} the ticket's {@code public_id} or the {@code wikiDocumentId}.
 */
public record AnswerSource(int n, String kind, UUID id, double similarity, boolean cited) {}
