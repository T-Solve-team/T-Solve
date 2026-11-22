import { Avatar } from 'antd';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useUnavailableDocuments } from '../hooks/useUnavailableDocuments';
import type { ChatTurn } from '../model';
import { ChatAnswer } from './ChatAnswer';
import styles from './Conversation.module.css';

type ConversationThreadProps = {
  turns: readonly ChatTurn[];
  /** The open conversation's title, shown in the bar above the thread (AC-002.18). */
  title: string;
  askerName: string;
  /** The question waiting for its answer, if any. */
  pendingQuestion?: string;
  /** A rating request is running (AC-003.2). */
  isRating: boolean;
  onRate: (answerId: string, rating: ChatTurn['rating']) => void;
  /** Shown in place of the turns while the open conversation loads or fails to load (AC-002.4, AC-002.8). */
  status?: ReactNode;
  children: ReactNode;
};

/** 12.1 Focus thread: question headings and answers (AC-001.6, AC-001.7), with one composer (AC-001.16). */
export function ConversationThread({
  turns,
  title,
  askerName,
  pendingQuestion,
  isRating,
  onRate,
  status,
  children,
}: ConversationThreadProps) {
  const end = useRef<HTMLDivElement>(null);
  const [highlightedCitationId, setHighlightedCitationId] = useState<string>();
  const latestAnswer = turns.at(-1)?.answer;
  const isEmpty = turns.length === 0 && pendingQuestion === undefined && status === undefined;
  const initials = askerName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((word) => Array.from(word)[0] ?? '')
    .join('')
    .toUpperCase();
  // Bring new questions and answers into view while leaving the scroll position alone when a rating changes.
  useEffect(() => {
    if (latestAnswer || pendingQuestion !== undefined) {
      end.current?.scrollIntoView?.({ block: 'end' });
    }
  }, [latestAnswer, pendingQuestion]);
  const unavailableIds = useUnavailableDocuments(citedWikiDocumentIds(turns));

  return (
    <section
      aria-label="Conversation"
      className={isEmpty ? `${styles.conversation} ${styles.empty}` : styles.conversation}
    >
      <header className={styles.topBar}>
        <h2 className={styles.title}>{title}</h2>
      </header>
      <div className={styles.thread} hidden={isEmpty}>
        <div className={`${styles.column} ${styles.turns}`}>
          {status}
          {status === undefined &&
            turns.map((turn, index) => (
              <article key={`${index}-${turn.answer.answerId}`} className={styles.turn}>
                <QuestionHeading question={turn.question} initials={initials} />
                <ChatAnswer
                  answer={turn.answer}
                  unavailableIds={unavailableIds}
                  rating={turn.rating}
                  isRating={isRating}
                  onRate={(rating) => onRate(turn.answer.answerId, rating)}
                  highlightedCitationId={highlightedCitationId}
                  onHighlightCitation={setHighlightedCitationId}
                />
              </article>
            ))}
          {pendingQuestion !== undefined && (
            <article className={styles.turn}>
              <QuestionHeading question={pendingQuestion} initials={initials} />
              <div className={styles.skeleton} aria-busy="true" data-testid="pending-answer">
                <span />
                <span />
                <span />
              </div>
            </article>
          )}
        </div>
        <div ref={end} />
      </div>
      <div className={`${styles.column} ${styles.composer}`}>
        {isEmpty && (
          <header className={styles.intro}>
            <h3>Ask the Knowledge Chatbot</h3>
            <p>
              Ask in your own words. Answers come from the Solutions and Wiki documents you can
              view.
            </p>
          </header>
        )}
        {children}
      </div>
    </section>
  );
}

/** 12.1 question heading and the signed-in asker's initials (AC-001.6, AC-001.7). */
function QuestionHeading({ question, initials }: { question: string; initials: string }) {
  return (
    <header className={styles.question}>
      <Avatar aria-hidden size={28} className={styles.avatar}>
        {initials}
      </Avatar>
      <h3>{question}</h3>
    </header>
  );
}

/** The Wiki documents cited in the conversation, each once and sorted, so the availability query key is stable. */
function citedWikiDocumentIds(turns: readonly ChatTurn[]): string[] {
  const ids = turns.flatMap(({ answer }) =>
    answer.citations.flatMap((c) => (c.type === 'WIKI_DOCUMENT' ? [c.wikiDocumentId] : [])),
  );
  return [...new Set(ids)].sort();
}
