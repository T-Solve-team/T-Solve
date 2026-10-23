import { MessageOutlined } from '@ant-design/icons';
import { Button, Flex } from 'antd';
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
            <Button
              type="text"
              block
              icon={<MessageOutlined aria-hidden />}
              disabled={disabled}
              aria-current={conversation.id === openId ? 'true' : undefined}
              className={styles.entry}
              onClick={() => onOpen(conversation.id)}
            >
              {conversation.turns[0]?.question}
            </Button>
          </li>
        ))}
      </ul>
    </Flex>
  );
}
