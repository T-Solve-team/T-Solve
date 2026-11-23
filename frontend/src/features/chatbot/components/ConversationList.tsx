import { MessageOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Tooltip } from 'antd';
import { useState } from 'react';
import type { Conversation } from '../model';
import styles from './Conversation.module.css';

type ConversationListProps = {
  conversations: readonly Conversation[];
  /** The open conversation; null is the empty "New conversation". */
  openId: string | null;
  /** True while an answer is pending: the answer lands in the conversation that asked (edge 17). */
  disabled: boolean;
  onOpen: (id: string) => void;
  onNew: () => void;
};

const NEW_TITLE = 'New conversation';
const PAGE_SIZE = 10;

/** AC-002.2: the AI title of the first answer, else the first question. */
function titleOf(conversation: Conversation): string {
  const first = conversation.turns[0];
  return first?.answer.title ?? first?.question ?? '';
}

/** 12.1 sidebar: New conversation, one-line titles, and the session history in batches (AC-002.3, AC-002.13, AC-002.14). */
export function ConversationList({
  conversations,
  openId,
  disabled,
  onOpen,
  onNew,
}: ConversationListProps) {
  const [visibleCount, setVisibleCount] = useState(PAGE_SIZE);

  return (
    <aside aria-label="Conversations" className={styles.list}>
      <Button
        type="primary"
        icon={<PlusOutlined aria-hidden />}
        disabled={disabled}
        onClick={onNew}
      >
        {NEW_TITLE}
      </Button>
      <div className={styles.history}>
        <ul aria-label="Conversations" className={styles.entries}>
          {openId === null && (
            <li>
              <Button
                type="text"
                block
                icon={<MessageOutlined aria-hidden />}
                aria-current="true"
                disabled={disabled}
                className={styles.entry}
              >
                {NEW_TITLE}
              </Button>
            </li>
          )}
          {conversations.slice(0, visibleCount).map((conversation) => {
            const title = titleOf(conversation);
            return (
              <li key={conversation.id}>
                <Tooltip title={title} placement="right" trigger={['hover', 'focus']}>
                  <Button
                    type="text"
                    block
                    icon={<MessageOutlined aria-hidden />}
                    disabled={disabled}
                    aria-current={conversation.id === openId ? 'true' : undefined}
                    className={styles.entry}
                    onClick={() => onOpen(conversation.id)}
                  >
                    <span data-title-line className={styles.title}>
                      {title}
                    </span>
                  </Button>
                </Tooltip>
              </li>
            );
          })}
        </ul>
        {conversations.length > visibleCount && (
          <Button
            type="text"
            block
            className={styles.showMore}
            onClick={() => setVisibleCount((count) => count + PAGE_SIZE)}
          >
            Show more
          </Button>
        )}
      </div>
    </aside>
  );
}
