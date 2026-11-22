/** 12.1 Knowledge Chatbot — SRS III.12.1, Report3 UC-51, GBR-36, Figma "12.1 Knowledge Chatbot". */
import { Alert, Button, Flex, Form, Spin } from 'antd';
import { useRef } from 'react';
import { useSession } from '@/shared/auth';
import { applyApiErrors } from '@/shared/forms';
import { msg, showMessage } from '@/shared/messages';
import { ConversationList } from '../components/ConversationList';
import { ConversationThread } from '../components/ConversationThread';
import { QuestionBox, type QuestionFormValues } from '../components/QuestionBox';
import { useAskQuestion } from '../hooks/useAskQuestion';
import { useConversations } from '../hooks/useConversations';
import { useRateAnswer } from '../hooks/useRateAnswer';
import styles from './KnowledgeChatbotPage.module.css';

/** The route is behind the sign-in guard, so the session has a user whenever this renders (nothing shows otherwise). */
export function KnowledgeChatbotPage() {
  const { session } = useSession();
  return session ? (
    <KnowledgeChatbot key={session.user.id} fullName={session.user.fullName} />
  ) : null;
}

function KnowledgeChatbot({ fullName }: { fullName: string }) {
  const [form] = Form.useForm<QuestionFormValues>();
  const { conversations, openId, turns, open, startNew, list, conversation, remove, loadMore } =
    useConversations();
  const ask = useAskQuestion();
  const rating = useRateAnswer();
  // Set at once, unlike ask.isPending: a second Enter before the next render must not send the question again.
  const isAsking = useRef(false);

  function handleAsk(question: string) {
    if (isAsking.current) return;
    isAsking.current = true;
    ask.mutate(
      // The backend reads the earlier turns from the open conversation (KAN-183); none starts a new one.
      { data: { question, chatSessionId: openId ?? undefined } },
      {
        onSuccess: (answer) => {
          if (answer.chatSessionId) open(answer.chatSessionId);
        },
        onError: (error) => {
          if (error.status === 401) return; // the global handler signs the user out (MSG07)
          if (form.getFieldValue('question') === '') form.setFieldValue('question', question);
          // BUG-006: only an error on the question can be shown under the box; any other is MSG06.
          const isOnQuestion = error.fieldErrors.some(
            (fieldError) => fieldError.field === 'question',
          );
          if (!isOnQuestion || !applyApiErrors(form, error)) showMessage('MSG06');
        },
        onSettled: () => {
          isAsking.current = false;
        },
      },
    );
    // Keep the field mounted and leave any text entered while waiting alone (AC-001.6, AC-001.7).
    form.setFieldValue('question', '');
    form.focusField('question');
  }

  return (
    <div className={styles.page}>
      <ConversationList
        conversations={conversations}
        openId={openId}
        disabled={ask.isPending}
        list={list}
        remove={remove}
        onLoadMore={() => void loadMore()}
        onOpen={open}
        onNew={startNew}
      />
      <ConversationThread
        turns={turns}
        askerName={fullName}
        pendingQuestion={ask.isPending ? ask.variables.data.question : undefined}
        isRating={rating.isPending}
        onRate={(answerId, next) => {
          // A thread with turns always belongs to an open conversation.
          if (openId !== null) rating.rate(openId, answerId, next);
        }}
        status={
          openId !== null && conversation.isPending ? (
            <div role="status" aria-label="Loading conversation">
              <Spin />
            </div>
          ) : openId !== null && conversation.isError ? (
            <Flex vertical gap="small">
              <Alert type="error" title={msg('MSG06')} />
              <Button onClick={() => void conversation.refetch()}>Retry</Button>
            </Flex>
          ) : undefined
        }
      >
        <QuestionBox
          form={form}
          isSending={
            ask.isPending ||
            remove.isPending ||
            (openId !== null && conversation.isFetching && !conversation.data)
          }
          onAsk={handleAsk}
        />
      </ConversationThread>
    </div>
  );
}

export default KnowledgeChatbotPage;
