import { Form, Input } from 'antd';
import type { RequestChangesRequest } from '@/shared/api';
import { applyApiErrors, rules } from '@/shared/forms';
import { showMessage } from '@/shared/messages';
import { FormField, FormModal } from '@/shared/ui';
import { useRequestChangesForTickets } from '../hooks/useReviewDecisions';

type RequestChangesFormValues = Pick<RequestChangesRequest, 'comment'>;
const COMMENT_MAX = 1000;

type RequestChangesModalProps = {
  ticketIds: number[];
  isOpen: boolean;
  onClose: () => void;
  /** After the tickets were sent back (MSG17 is already shown). */
  onSent: () => void;
};

/**
 * 7.2 Request Changes (popup) — SRS III.7.2. Comment * (shown to each author in Jira, MSG53) · Send ·
 * Cancel. FE Lead D1 / FE-34: one shared comment for every selected ticket.
 */
export function RequestChangesModal({
  ticketIds,
  isOpen,
  onClose,
  onSent,
}: RequestChangesModalProps) {
  const [form] = Form.useForm<RequestChangesFormValues>();
  const requestChanges = useRequestChangesForTickets();

  function handleSubmit(values: RequestChangesFormValues) {
    requestChanges.mutate(
      { data: { ticketIds, comment: values.comment } },
      {
        onSuccess: onSent,
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
      onSubmit={handleSubmit}
      onCancel={onClose}
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
