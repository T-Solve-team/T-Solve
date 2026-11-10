package vn.tsolve.chatbot.rating.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.chatbot.answer.repository.ChatbotAnswerRepository;
import vn.tsolve.chatbot.rating.dto.ChatbotRatingRequest;
import vn.tsolve.chatbot.rating.exception.ChatbotAnswerNotFoundException;
import vn.tsolve.chatbot.rating.exception.InvalidChatbotRatingException;
import vn.tsolve.chatbot.rating.service.ChatbotRatingService;
import vn.tsolve.common.security.TSolveUser;

@Service
public class ChatbotRatingServiceImpl implements ChatbotRatingService {
    private final ChatbotAnswerRepository answers;
    private final Clock clock;

    public ChatbotRatingServiceImpl(ChatbotAnswerRepository answers, Clock clock) {
        this.answers = answers;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void rate(TSolveUser user, String answerId, ChatbotRatingRequest request) {
        if (request == null || request.rating() == null) {
            throw new InvalidChatbotRatingException();
        }
        UUID id = parseAnswerId(answerId);
        if (answers.updateRating(id, user.id(), request.rating(), Instant.now(clock)) == 0) {
            throw new ChatbotAnswerNotFoundException();
        }
    }

    @Override
    @Transactional
    public void remove(TSolveUser user, String answerId) {
        UUID id = parseAnswerId(answerId);
        if (answers.updateRating(id, user.id(), null, null) == 0) {
            throw new ChatbotAnswerNotFoundException();
        }
    }

    private static UUID parseAnswerId(String answerId) {
        try {
            return UUID.fromString(answerId);
        } catch (IllegalArgumentException e) {
            throw new ChatbotAnswerNotFoundException();
        }
    }
}
