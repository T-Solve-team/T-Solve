import { ArrowUpOutlined } from '@ant-design/icons';
import { Button, Form, Input, type FormInstance } from 'antd';
import type { ComponentProps, KeyboardEvent } from 'react';
import type { ChatbotQuestion } from '@/shared/api';
import { rules } from '@/shared/forms';
import { AppForm, FormField } from '@/shared/ui';
import { QUESTION_MAX_LENGTH } from '../model';
import styles from './QuestionBox.module.css';

export type QuestionFormValues = Pick<ChatbotQuestion, 'question'>;

type QuestionBoxProps = {
  form: FormInstance<QuestionFormValues>;
  hasQuestion: boolean;
  isSending: boolean;
  onAsk: (question: string) => void;
};

/**
 * 12.1 composer (AC-001.2–AC-001.6): Enter sends, Shift+Enter adds a line, and MSG01/MSG02 validate the question.
 */
export function QuestionBox({ form, hasQuestion, isSending, onAsk }: QuestionBoxProps) {
  const question = Form.useWatch('question', form) ?? '';

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent.isComposing) return;
    event.preventDefault();
    if (!isSending) form.submit();
  }

  return (
    <AppForm<QuestionFormValues>
      className={styles.form}
      form={form}
      requiredMark={false}
      onFinish={(values) => onAsk(values.question)}
    >
      <FormField<QuestionFormValues>
        name="question"
        className={styles.field}
        // BUG-007: the empty box only shows MSG01 on change or submit, never when it loses focus.
        validateTrigger="onChange"
        rules={[rules.required('Question'), rules.maxLength('Question', QUESTION_MAX_LENGTH)]}
      >
        <QuestionInput
          placeholder={hasQuestion ? 'Ask a follow-up question' : 'Ask a question'}
          isSending={isSending}
          onKeyDown={handleKeyDown}
        />
      </FormField>
      <div className={styles.metadata}>
        <span>
          {isSending ? 'Send waits for the current answer' : 'Enter sends, Shift+Enter adds a line'}
        </span>
        <span className={styles.count}>
          {question.length.toLocaleString('en-US')} / {QUESTION_MAX_LENGTH.toLocaleString('en-US')}
        </span>
      </div>
    </AppForm>
  );
}

/** 12.1 input and Send share a row, with the FormField's validation below it (AC-001.4, AC-001.5, AC-001.14). */
function QuestionInput({
  isSending,
  ...inputProps
}: ComponentProps<typeof Input.TextArea> & { isSending: boolean }) {
  return (
    <div className={styles.row}>
      <Input.TextArea
        {...inputProps}
        aria-label="Question"
        autoSize={{ minRows: 1, maxRows: 8 }}
        variant="borderless"
        className={styles.input}
      />
      <Button
        type="primary"
        htmlType="submit"
        size="large"
        icon={<ArrowUpOutlined aria-hidden />}
        aria-label="Send"
        disabled={isSending}
        className={styles.send}
      />
    </div>
  );
}
