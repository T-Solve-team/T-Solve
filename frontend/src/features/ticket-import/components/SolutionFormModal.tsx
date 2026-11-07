/** 5.3 Import Ticket Preview with the Solution to write — spec STORY-015 (a file without Solutions). */
import { Descriptions, Flex, Form, Input } from 'antd';
import { useRef } from 'react';
import { useGetImportPreviewRow, type FillSolutionRequest } from '@/shared/api';
import { applyApiErrors, rules } from '@/shared/forms';
import { showMessage } from '@/shared/messages';
import { ErrorState, FormField, FormModal, LoadingState } from '@/shared/ui';
import { useFillPreviewSolution } from '../hooks/useImportPreview';
import { SECTIONS } from '../lib/import-row';

type SolutionValues = Omit<FillSolutionRequest, 'rowNumbers'>;

type SolutionFormModalProps = {
  previewId: number;
  /** The Needs Solution or Valid row to write; null keeps the popup closed. */
  rowNo: number | null;
  onClose: () => void;
};

/**
 * The row's fields read-only and its Solution sections as fields with Apply (AC-015.2). Apply saves through
 * fill-solution; a rejection shows the backend's message at its field and keeps what was written (AC-015.3).
 */
export function SolutionFormModal({ previewId, rowNo, onClose }: SolutionFormModalProps) {
  const [form] = Form.useForm<SolutionValues>();
  const fill = useFillPreviewSolution(previewId);
  // A double click reaches the handler twice before `isPending` renders: one request only.
  const isSending = useRef(false);

  function handleSubmit(values: SolutionValues) {
    if (rowNo === null || isSending.current) return;
    isSending.current = true;
    fill.mutate(
      { previewId, data: { ...values, rowNumbers: [rowNo] } },
      {
        onSettled: () => {
          isSending.current = false;
        },
        onSuccess: onClose,
        onError: (error) => {
          // The global MSG07 dialog is the one message for a 401 (guideline 06 §6).
          if (error.status === 401) return;
          if (!applyApiErrors(form, error)) showMessage(error.code, error.params);
        },
      },
    );
  }

  return (
    <FormModal<SolutionValues>
      isOpen={rowNo !== null}
      title="Import Ticket Preview"
      width={720}
      form={form}
      okText="Apply"
      isSubmitting={fill.isPending}
      onSubmit={handleSubmit}
      onCancel={() => {
        if (!isSending.current) onClose();
      }}
    >
      {rowNo !== null && <RowFields previewId={previewId} rowNo={rowNo} />}
    </FormModal>
  );
}

function RowFields({ previewId, rowNo }: { previewId: number; rowNo: number }) {
  const row = useGetImportPreviewRow(previewId, rowNo);
  if (row.isPending) return <LoadingState />;
  if (row.isError) return <ErrorState error={row.error} onRetry={() => void row.refetch()} />;

  const { data } = row;
  return (
    <Flex vertical gap="middle">
      <Descriptions
        column={1}
        bordered
        size="small"
        items={[
          { key: 'ticketId', label: 'Ticket ID', children: data.ticketId },
          { key: 'title', label: 'Title', children: data.title },
          { key: 'description', label: 'Description', children: data.description },
          { key: 'author', label: 'Author', children: data.author },
          { key: 'resolvedDate', label: 'Resolved date', children: data.resolvedDate },
        ]}
      />
      {/* Mounted once the row is read, so each field starts from the row's saved section (edge case 51). */}
      {SECTIONS.map(([label, field]) => (
        <FormField<SolutionValues>
          key={field}
          name={field}
          label={label}
          initialValue={data[field] ?? undefined}
          rules={field === 'workaround' ? [] : [rules.required(label)]}
        >
          <Input.TextArea rows={3} />
        </FormField>
      ))}
    </Flex>
  );
}
