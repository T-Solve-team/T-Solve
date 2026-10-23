import { Button, Flex, Input, type FormInstance } from 'antd';
import type { KeyboardEvent } from 'react';
import { rules } from '@/shared/forms';
import { AppForm, FormField } from '@/shared/ui';
import { QUESTION_MAX_LENGTH } from '../model';
import styles from './QuestionBox.module.css';

export type QuestionFormValues = { question: string };

type QuestionBoxProps = {
  form: FormInstance<QuestionFormValues>;
  isSending: boolean;
  onAsk: (question: string) => void;
};

/**
 * 12.1 question box (callout 2) and Send: at most 1,000 characters (MSG01, MSG02); Enter sends, Shift+Enter adds a
 * line. The text is cleared by the page only when the answer arrives, so a failed question stays (UC-51).
 */
export function QuestionBox({ form, isSending, onAsk }: QuestionBoxProps) {
  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent.isComposing) return;
    event.preventDefault();
    form.submit();
  }

  return (
    <AppForm<QuestionFormValues>
      form={form}
      requiredMark={false}
      onFinish={(values) => onAsk(values.question)}
    >
      <Flex gap="middle" align="end">
        <FormField<QuestionFormValues>
          name="question"
          className={styles.field}
          rules={[rules.required('Question'), rules.maxLength('Question', QUESTION_MAX_LENGTH)]}
        >
          <Input.TextArea
            aria-label="Question"
            autoSize={{ minRows: 3, maxRows: 8 }}
            count={{ show: true, max: QUESTION_MAX_LENGTH }}
            onKeyDown={handleKeyDown}
          />
        </FormField>
        <Button
          type="primary"
          htmlType="submit"
          loading={isSending}
          disabled={isSending}
          className={styles.send}
        >
          Send
        </Button>
      </Flex>
    </AppForm>
  );
}
