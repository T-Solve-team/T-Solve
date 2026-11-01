import { Alert, Flex, Form, Input } from 'antd';
import { useRef } from 'react';
import type { RejectTicketsRequest } from '@/shared/api';
import { applyApiErrors, rules } from '@/shared/forms';
import { msg, showMessage } from '@/shared/messages';
import { FormField, FormModal } from '@/shared/ui';
import { useRejectSelectedTickets } from '../hooks/useReviewDecisions';

type RejectFormValues = Pick<RejectTicketsRequest, 'reason'>;
const REASON_MAX = 1000;

type RejectTicketModalProps = {
  ticketIds: string[];
  isDisabled?: boolean;
  isOpen: boolean;
  onClose: () => void;
  /** After a successful rejection (MSG16 is already shown). */
  onRejected: () => void;
};

/**
 * 7.3 Reject Ticket (popup) — SRS III.7.3, Figma "7.3 Reject Ticket (popup)".
 * MSG15 (callout 1) · Reason * (2) · Reject (3) · Cancel (4). One reason for all selected tickets.
 */
export function RejectTicketModal({
  ticketIds,
  isOpen,
  onClose,
  onRejected,
  isDisabled = false,
}: RejectTicketModalProps) {
  const [form] = Form.useForm<RejectFormValues>();
  const reject = useRejectSelectedTickets();
  const submitting = useRef(false);

  function handleSubmit(values: RejectFormValues) {
    if (isDisabled || submitting.current) return;
    submitting.current = true;
    reject.mutate(
      { data: { ticketIds, reason: values.reason } },
      {
        onSuccess: onRejected,
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
    <FormModal<RejectFormValues>
      isOpen={isOpen}
      title="Reject Ticket"
      kind="danger"
      width={600}
      form={form}
      okText="Reject"
      isOkDanger
      isSubmitting={reject.isPending}
      isOkDisabled={isDisabled}
      onSubmit={handleSubmit}
      onCancel={() => {
        if (!submitting.current) onClose();
      }}
    >
      <Flex vertical gap="middle">
        <Alert type="warning" showIcon title={msg('MSG15', { count: ticketIds.length })} />
        <FormField<RejectFormValues>
          name="reason"
          label="Reason"
          rules={[rules.required('Reason'), rules.maxLength('Reason', REASON_MAX)]}
        >
          <Input.TextArea rows={3} count={{ show: true, max: REASON_MAX }} />
        </FormField>
      </Flex>
    </FormModal>
  );
}
