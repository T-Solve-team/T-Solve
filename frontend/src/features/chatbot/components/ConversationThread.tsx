import { Flex, Spin } from 'antd';
import { useEffect, useRef } from 'react';
import { useUnavailableDocuments } from '../hooks/useUnavailableDocuments';
import type { ChatTurn } from '../model';
import { ChatAnswer } from './ChatAnswer';
import styles from './Conversation.module.css';

type ConversationThreadProps = {
  turns: readonly ChatTurn[];
  /** The question waiting for its answer, if any. */
  pendingQuestion?: string;
  /** A rating request is running (AC-003.2). */
  isRating: boolean;
  onRate: (answerId: string, rating: ChatTurn['rating']) => void;
};

/** 12.1 conversation thread: each question on the right, its answer below it; a loading answer while waiting. */
export function ConversationThread({
  turns,
  pendingQuestion,
  isRating,
  onRate,
}: ConversationThreadProps) {
  const end = useRef<HTMLDivElement>(null);
  // The thread scrolls inside its card: bring the newest question or answer into view.
  useEffect(() => {
    end.current?.scrollIntoView?.({ block: 'end' });
  }, [turns, pendingQuestion]);
  const unavailableIds = useUnavailableDocuments(citedWikiDocumentIds(turns));
  return (
    <section aria-label="Conversation" className={styles.thread}>
      <Flex vertical gap="large">
        {turns.map((turn, index) => (
          <Flex key={`${index}-${turn.answer.answerId}`} vertical gap="middle">
            <p className={styles.question}>{turn.question}</p>
            <ChatAnswer
              answer={turn.answer}
              unavailableIds={unavailableIds}
              rating={turn.rating}
              isRating={isRating}
              onRate={(rating) => onRate(turn.answer.answerId, rating)}
            />
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
      <div ref={end} />
    </section>
  );
}

/** The Wiki documents cited in the conversation, each once and sorted, so the availability query key is stable. */
function citedWikiDocumentIds(turns: readonly ChatTurn[]): string[] {
  const ids = turns.flatMap(({ answer }) =>
    answer.citations.flatMap((c) => (c.type === 'WIKI_DOCUMENT' ? [c.wikiDocumentId] : [])),
  );
  return [...new Set(ids)].sort();
}
