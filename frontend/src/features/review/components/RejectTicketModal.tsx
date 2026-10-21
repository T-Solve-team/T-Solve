import { Alert, Flex, Form, Input } from 'antd';
import type { RejectTicketsRequest } from '@/shared/api';
import { applyApiErrors, rules } from '@/shared/forms';
import { msg, showMessage } from '@/shared/messages';
import { FormField, FormModal } from '@/shared/ui';
import { useRejectSelectedTickets } from '../hooks/useReviewDecisions';

type RejectFormValues = Pick<RejectTicketsRequest, 'reason'>;
const REASON_MAX = 1000;

type RejectTicketModalProps = {
  ticketIds: number[];
  isOpen: boolean;
  onClose: () => void;
  /** After a successful rejection (MSG20 is already shown). */
  onRejected: () => void;
};

/**
 * 7.3 Reject Ticket (popup) — SRS III.7.3, Figma "7.3 Reject Ticket (popup)".
 * MSG19 (callout 1) · Reason * (2) · Reject (3) · Cancel (4). One reason for all selected tickets.
 */
export function RejectTicketModal({
  ticketIds,
  isOpen,
  onClose,
  onRejected,
}: RejectTicketModalProps) {
  const [form] = Form.useForm<RejectFormValues>();
  const reject = useRejectSelectedTickets();

  function handleSubmit(values: RejectFormValues) {
    reject.mutate(
      { data: { ticketIds, reason: values.reason } },
      {
        onSuccess: onRejected,
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
      onSubmit={handleSubmit}
      onCancel={onClose}
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
