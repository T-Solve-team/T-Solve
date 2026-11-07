package vn.tsolve.chatbot.answer.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = WikiDocumentCitation.class, name = "WIKI_DOCUMENT"),
    @JsonSubTypes.Type(value = TicketSolutionCitation.class, name = "TICKET_SOLUTION")
})
public sealed interface ChatbotCitation permits WikiDocumentCitation, TicketSolutionCitation {
    int number();
}
