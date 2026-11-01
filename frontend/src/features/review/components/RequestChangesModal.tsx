import { Form, Input } from 'antd';
import { useRef } from 'react';
import type { RequestChangesRequest } from '@/shared/api';
import { applyApiErrors, rules } from '@/shared/forms';
import { showMessage } from '@/shared/messages';
import { FormField, FormModal } from '@/shared/ui';
import { useRequestChangesForTickets } from '../hooks/useReviewDecisions';

type RequestChangesFormValues = Pick<RequestChangesRequest, 'comment'>;
const COMMENT_MAX = 1000;

type RequestChangesModalProps = {
  ticketIds: string[];
  isDisabled?: boolean;
  isOpen: boolean;
  onClose: () => void;
  /** After the tickets were sent back (MSG17 is already shown). */
  onSent: () => void;
};

/**
 * 7.2 Request Changes (popup) — SRS III.7.2. Comment * (shown to each author in Jira, MSG64) · Send ·
 * Cancel. From the Review Queue it takes every selected ticket with one comment (decision FE-34).
 */
export function RequestChangesModal({
  ticketIds,
  isOpen,
  onClose,
  onSent,
  isDisabled = false,
}: RequestChangesModalProps) {
  const [form] = Form.useForm<RequestChangesFormValues>();
  const requestChanges = useRequestChangesForTickets();
  const submitting = useRef(false);

  function handleSubmit(values: RequestChangesFormValues) {
    if (isDisabled || submitting.current) return;
    submitting.current = true;
    requestChanges.mutate(
      { data: { ticketIds, comment: values.comment } },
      {
        onSuccess: onSent,
        onSettled: () => {
          submitting.current = false;
        },
        onError: (error) => {
          if (!applyApiErrors(form, error)) showMessage(error.code, error.params);
        },
      },
    );
  }

  return (
    <FormModal<RequestChangesFormValues>
      isOpen={isOpen}
      title="Request Changes"
      width={600}
      form={form}
      okText="Send"
      isSubmitting={requestChanges.isPending}
      isOkDisabled={isDisabled}
      onSubmit={handleSubmit}
      onCancel={() => {
        if (!submitting.current) onClose();
      }}
    >
      <FormField<RequestChangesFormValues>
        name="comment"
        label="Comment"
        rules={[rules.required('Comment'), rules.maxLength('Comment', COMMENT_MAX)]}
      >
        <Input.TextArea rows={3} count={{ show: true, max: COMMENT_MAX }} />
      </FormField>
    </FormModal>
  );
}
