import { Flex, Spin } from 'antd';
import type { ChatTurn } from '../model';
import { ChatAnswer } from './ChatAnswer';
import styles from './Conversation.module.css';

type ConversationThreadProps = {
  turns: readonly ChatTurn[];
  /** The question waiting for its answer, if any. */
  pendingQuestion?: string;
};

/** 12.1 conversation thread: each question on the right, its answer below it; a loading answer while waiting. */
export function ConversationThread({ turns, pendingQuestion }: ConversationThreadProps) {
  return (
    <section aria-label="Conversation" className={styles.thread}>
      <Flex vertical gap="large">
        {turns.map((turn, index) => (
          <Flex key={`${index}-${turn.answer.answerId}`} vertical gap="middle">
            <p className={styles.question}>{turn.question}</p>
            <ChatAnswer answer={turn.answer} />
          </Flex>
        ))}
        {pendingQuestion !== undefined && (
          <Flex vertical gap="middle">
            <p className={styles.question}>{pendingQuestion}</p>
            <div className={styles.answer} aria-busy="true" data-testid="pending-answer">
              <Spin size="small" />
            </div>
          </Flex>
        )}
      </Flex>
    </section>
  );
}
