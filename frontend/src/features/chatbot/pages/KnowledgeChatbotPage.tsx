/** 12.1 Knowledge Chatbot — SRS III.12.1, Report3 UC-51, GBR-36, Figma "12.1 Knowledge Chatbot". */
import { Form } from 'antd';
import { useRef } from 'react';
import { useSession } from '@/shared/auth';
import { applyApiErrors } from '@/shared/forms';
import { showMessage } from '@/shared/messages';
import { ConversationList } from '../components/ConversationList';
import { ConversationThread } from '../components/ConversationThread';
import { QuestionBox, type QuestionFormValues } from '../components/QuestionBox';
import { useAskQuestion } from '../hooks/useAskQuestion';
import { useConversations } from '../hooks/useConversations';
import { useRateAnswer } from '../hooks/useRateAnswer';
import { toHistory } from '../model';
import styles from './KnowledgeChatbotPage.module.css';

/** The route is behind the sign-in guard, so the session has a user whenever this renders (nothing shows otherwise). */
export function KnowledgeChatbotPage() {
  const { session } = useSession();
  return session ? (
    <KnowledgeChatbot userId={session.user.id} fullName={session.user.fullName} />
  ) : null;
}

function KnowledgeChatbot({ userId, fullName }: { userId: string; fullName: string }) {
  const [form] = Form.useForm<QuestionFormValues>();
  const { conversations, openId, turns, open, startNew, addTurn, setRating } =
    useConversations(userId);
  const ask = useAskQuestion();
  const rating = useRateAnswer(userId, setRating);
  // Set at once, unlike ask.isPending: a second Enter before the next render must not send the question again.
  const isAsking = useRef(false);

  function handleAsk(question: string) {
    if (isAsking.current) return;
    isAsking.current = true;
    const conversationId = openId ?? crypto.randomUUID();
    ask.mutate(
      { data: { question, history: toHistory(turns) } },
      {
        onSuccess: (answer) => addTurn(conversationId, { question, answer }),
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
      >
        <QuestionBox form={form} isSending={ask.isPending} onAsk={handleAsk} />
      </ConversationThread>
    </div>
  );
}

export default KnowledgeChatbotPage;
