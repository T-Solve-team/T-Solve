import { DislikeOutlined, LikeOutlined } from '@ant-design/icons';
import { Button, Flex, Form, Input } from 'antd';
import { useRef } from 'react';
import type { TicketFeedbackRequest } from '@/shared/api';
import { applyApiErrors, rules } from '@/shared/forms';
import { showMessage } from '@/shared/messages';
import { FormField, FormModal } from '@/shared/ui';
import { useSendFeedback } from '../hooks/useSendFeedback';

type FeedbackFormValues = { isHelpful?: boolean; comment?: string };
const COMMENT_MAX = 500;

const CHOICES = [
  { isHelpful: true, label: 'Helpful', icon: <LikeOutlined aria-hidden /> },
  { isHelpful: false, label: 'Not helpful', icon: <DislikeOutlined aria-hidden /> },
];

type HelpfulChoiceProps = {
  value?: boolean;
  onChange?: (value: boolean) => void;
  /** Form.Item links the field's error to its control through these. */
  id?: string;
  'aria-describedby'?: string;
  'aria-invalid'?: boolean;
};

/**
 * Helpful? as two buttons in a group named after the field; the chosen one is selected (`aria-pressed`).
 * Form.Item passes value, onChange, the id and the error link.
 */
function HelpfulChoice({ value, onChange, ...field }: HelpfulChoiceProps) {
  return (
    <Flex role="group" aria-label="Helpful?" gap="small" {...field}>
      {CHOICES.map((choice) => {
        const isSelected = value === choice.isHelpful;
        return (
          <Button
            key={choice.label}
            type={isSelected ? 'primary' : 'default'}
            icon={choice.icon}
            aria-pressed={isSelected}
            onClick={() => onChange?.(choice.isHelpful)}
          >
            {choice.label}
          </Button>
        );
      })}
    </Flex>
  );
}

type GiveFeedbackModalProps = {
  ticketId: string;
  isOpen: boolean;
  onClose: () => void;
};

/**
 * 8.1 Give Feedback (popup) — SRS III.8.1, Figma "8.1 Give Feedback". Helpful? * (Helpful · Not helpful) ·
 * Comment · Send · Cancel. Mounted by 5.5 Ticket Detail behind its "Give feedback" button.
 */
export function GiveFeedbackModal({ ticketId, isOpen, onClose }: GiveFeedbackModalProps) {
  const [form] = Form.useForm<FeedbackFormValues>();
  const sendFeedback = useSendFeedback();
  // FormModal validates asynchronously, so a double click reaches onSubmit twice before isPending renders (BUG-068).
  const isSending = useRef(false);

  function handleSubmit(values: FeedbackFormValues) {
    // The required rule guarantees a choice; never send an answer the user did not give.
    if (isSending.current || values.isHelpful === undefined) return;
    isSending.current = true;
    const data: TicketFeedbackRequest = {
      isHelpful: values.isHelpful,
      comment: values.comment?.trim() || null,
    };
    sendFeedback.mutate(
      { ticketId, data },
      {
        onSuccess: onClose,
        onError: (error) => {
          // 401 is handled once for the whole app (session expired).
          if (error.status !== 401 && !applyApiErrors(form, error)) {
            showMessage(error.code, error.params);
          }
        },
        onSettled: () => {
          isSending.current = false;
        },
      },
    );
  }

  return (
    <FormModal<FeedbackFormValues>
      isOpen={isOpen}
      title="Give Feedback"
      form={form}
      okText="Send"
      isSubmitting={sendFeedback.isPending}
      onSubmit={handleSubmit}
      onCancel={onClose}
    >
      <FormField<FeedbackFormValues>
        name="isHelpful"
        label="Helpful?"
        // The buttons have no blur: check on change, so a choice clears MSG01 at once.
        validateTrigger="onChange"
        // A rule without a type is checked as a string, which `true` / `false` would fail.
        rules={[{ ...rules.required('Helpful?'), type: 'boolean' }]}
      >
        <HelpfulChoice />
      </FormField>
      <FormField<FeedbackFormValues>
        name="comment"
        label="Comment"
        rules={[rules.maxLength('Comment', COMMENT_MAX)]}
      >
        <Input.TextArea rows={3} count={{ show: true, max: COMMENT_MAX }} />
      </FormField>
    </FormModal>
  );
}
