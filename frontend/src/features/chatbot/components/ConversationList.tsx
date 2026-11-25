import { DeleteOutlined, MessageOutlined, PlusOutlined } from '@ant-design/icons';
import { Alert, Button, Flex, Spin, Tooltip } from 'antd';
import { useState } from 'react';
import type { ChatbotConversationSummary } from '@/shared/api';
import { msg } from '@/shared/messages';
import { ConfirmDialog } from '@/shared/ui';
import type { useConversations } from '../hooks/useConversations';
import styles from './Conversation.module.css';

type ConversationListProps = {
  conversations: readonly ChatbotConversationSummary[];
  openId: string | null;
  disabled: boolean;
  list: ReturnType<typeof useConversations>['list'];
  remove: ReturnType<typeof useConversations>['remove'];
  onLoadMore: () => void;
  onOpen: (id: string) => void;
  onNew: () => void;
};

const NEW_TITLE = 'New conversation';

/**
 * 12.1 sidebar: the server's saved conversations by pages of 10 with "Show more", one-line titles, and confirmation
 * before deleting one (UC-52; AC-002.3, AC-002.13 – AC-002.17).
 */
export function ConversationList({
  conversations,
  openId,
  disabled,
  list,
  remove,
  onLoadMore,
  onOpen,
  onNew,
}: ConversationListProps) {
  const [deleting, setDeleting] = useState<ChatbotConversationSummary | null>(null);
  const isDisabled = disabled || remove.isPending;
  return (
    <aside aria-label="Conversations" className={styles.list}>
      <Button
        type="primary"
        icon={<PlusOutlined aria-hidden />}
        disabled={isDisabled}
        onClick={onNew}
      >
        {NEW_TITLE}
      </Button>
      <div className={styles.history}>
        {list.isPending && (
          <div role="status" aria-label="Loading conversations">
            <Spin />
          </div>
        )}
        {list.isError && !list.isFetchNextPageError && (
          <Flex vertical gap="small">
            <Alert type="error" title={msg('MSG06')} />
            <Button disabled={isDisabled} onClick={() => void list.refetch()}>
              Retry
            </Button>
          </Flex>
        )}
        <ul aria-label="Conversations" className={styles.entries}>
          {openId === null && (
            <li>
              <Button
                type="text"
                block
                icon={<MessageOutlined aria-hidden />}
                aria-current="true"
                disabled={isDisabled}
                className={styles.entry}
              >
                {NEW_TITLE}
              </Button>
            </li>
          )}
          {(!list.isError || list.isFetchNextPageError) &&
            conversations.map((conversation) => (
              <li key={conversation.chatSessionId}>
                <Flex align="center">
                  <Tooltip
                    title={conversation.title}
                    placement="right"
                    trigger={['hover', 'focus']}
                  >
                    <Button
                      type="text"
                      block
                      icon={<MessageOutlined aria-hidden />}
                      disabled={isDisabled}
                      aria-current={conversation.chatSessionId === openId ? 'true' : undefined}
                      className={styles.entry}
                      onClick={() => onOpen(conversation.chatSessionId)}
                    >
                      <span data-title-line className={styles.title}>
                        {conversation.title}
                      </span>
                    </Button>
                  </Tooltip>
                  <Button
                    type="text"
                    icon={<DeleteOutlined />}
                    aria-label="Delete"
                    disabled={isDisabled}
                    onClick={() => setDeleting(conversation)}
                  />
                </Flex>
              </li>
            ))}
        </ul>
        {list.hasNextPage && (
          <Button
            type="text"
            block
            className={styles.showMore}
            loading={list.isFetchingNextPage}
            disabled={isDisabled}
            onClick={onLoadMore}
          >
            Show more
          </Button>
        )}
      </div>
      <ConfirmDialog
        isOpen={deleting !== null}
        code="MSG103"
        isDanger
        params={{ title: deleting?.title ?? '' }}
        isConfirming={remove.isPending}
        onCancel={() => {
          if (!remove.isPending) setDeleting(null);
        }}
        onConfirm={() => {
          if (!deleting || isDisabled) return;
          remove.mutate(
            { chatSessionId: deleting.chatSessionId },
            {
              onSuccess: () => setDeleting(null),
            },
          );
        }}
      />
    </aside>
  );
}
