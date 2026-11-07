package vn.tsolve.chatbot.answer.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.tsolve.chatbot.answer.entity.ChatbotAnswer;

public interface ChatbotAnswerRepository extends JpaRepository<ChatbotAnswer, UUID> {}
