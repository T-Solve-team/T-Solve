import { MessageOutlined } from '@ant-design/icons';
import { Button, Flex, Tooltip } from 'antd';
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

/** AC-002.2: the AI title of the first answer, else the first question. */
function titleOf(conversation: Conversation): string {
  const first = conversation.turns[0];
  return first?.answer.title ?? first?.question ?? '';
}

/** 12.1 conversation list: New conversation, then the session's conversations newest first (UC-52). */
export function ConversationList({
  conversations,
  openId,
  disabled,
  onOpen,
  onNew,
}: ConversationListProps) {
  return (
    <Flex vertical gap="middle" className={styles.list}>
      <Button disabled={disabled} onClick={onNew}>
        {NEW_TITLE}
      </Button>
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
        {conversations.map((conversation) => (
          <li key={conversation.id}>
            {/* AC-002.13: one line cut with "…"; the tooltip shows the whole title on hover and focus. */}
            <Tooltip title={titleOf(conversation)} placement="right">
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
                  {titleOf(conversation)}
                </span>
              </Button>
            </Tooltip>
          </li>
        ))}
      </ul>
    </Flex>
  );
}
