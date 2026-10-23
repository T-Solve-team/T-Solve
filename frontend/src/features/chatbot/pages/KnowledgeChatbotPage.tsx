/** 12.1 Knowledge Chatbot — SRS III.12.1, Report3 UC-51, GBR-36, Figma "12.1 Knowledge Chatbot". */
import { Form } from 'antd';
import { useRef } from 'react';
import { applyApiErrors } from '@/shared/forms';
import { showMessage } from '@/shared/messages';
import { ConversationThread } from '../components/ConversationThread';
import { QuestionBox, type QuestionFormValues } from '../components/QuestionBox';
import { useAskQuestion } from '../hooks/useAskQuestion';
import { useConversations } from '../hooks/useConversations';
import { toHistory } from '../model';
import styles from './KnowledgeChatbotPage.module.css';

export function KnowledgeChatbotPage() {
  const [form] = Form.useForm<QuestionFormValues>();
  const { turns, addTurn } = useConversations();
  const ask = useAskQuestion();
  // Set at once, unlike ask.isPending: a second Enter before the next render must not send the question again.
  const isAsking = useRef(false);

  function handleAsk(question: string) {
    if (isAsking.current) return;
    isAsking.current = true;
    ask.mutate(
      { data: { question, history: toHistory(turns) } },
      {
        onSuccess: (answer) => {
          addTurn({ question, answer });
          // Only the sent question: text typed while waiting stays (AC-001.7). Not resetFields(): it re-mounts the
          // field, and the box would lose focus after every answer.
          if (form.getFieldValue('question') === question) form.setFieldValue('question', '');
        },
        onError: (error) => {
          if (error.status === 401) return; // the global handler signs the user out (MSG07)
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
  }

  return (
    <div className={styles.chat}>
      <ConversationThread
        turns={turns}
        pendingQuestion={ask.isPending ? ask.variables.data.question : undefined}
      />
      <QuestionBox form={form} isSending={ask.isPending} onAsk={handleAsk} />
    </div>
  );
}

export default KnowledgeChatbotPage;
