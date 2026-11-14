/** 5.3 Import Ticket Preview with the Solution to write — spec STORY-015 (a file without Solutions). */
import { Descriptions, Flex, Form, Input } from 'antd';
import { useRef, useState } from 'react';
import {
  useGetImportPreviewRow,
  type ApiError,
  type FillSolutionRequest,
  type ImportPreviewRowResponse,
} from '@/shared/api';
import { applyApiErrors, rules } from '@/shared/forms';
import { showMessage } from '@/shared/messages';
import { ConfirmDialog, ErrorState, FormField, FormModal, LoadingState } from '@/shared/ui';
import { useFillPreviewSolution } from '../hooks/useImportPreview';
import { SECTIONS } from '../lib/import-row';
import { jiraSnapshotItems } from './jira-snapshot';

type SolutionValues = Omit<FillSolutionRequest, 'rowNumbers'>;

type SolutionFormModalProps = {
  previewId: number;
  /** The row opened through Preview; null for Fill solution or a closed popup. */
  rowNo: number | null;
  /** Fill solution opens empty sections for these selected rows (AC-015.5). */
  rowNumbers?: number[];
  replaceCount?: number;
  onSuccess?: () => void;
  /** After the popup has shown the rejection, e.g. for the page to leave a preview that is gone (design Edge 10). */
  onError?: (error: ApiError) => void;
  onClose: () => void;
};

/**
 * The row's fields read-only and its Solution sections as fields with Apply (AC-015.2). Apply saves through
 * fill-solution; a rejection shows the backend's message at its field and keeps what was written (AC-015.3).
 */
export function SolutionFormModal({
  previewId,
  rowNo,
  rowNumbers = [],
  replaceCount = 0,
  onSuccess,
  onError,
  onClose,
}: SolutionFormModalProps) {
  const [form] = Form.useForm<SolutionValues>();
  const fill = useFillPreviewSolution(previewId);
  // A double click reaches the handler twice before `isPending` renders: one request only.
  const isSending = useRef(false);
  const [pendingValues, setPendingValues] = useState<SolutionValues | null>(null);

  function save(values: SolutionValues) {
    const targets = rowNo === null ? rowNumbers : [rowNo];
    if (targets.length === 0 || isSending.current) return;
    isSending.current = true;
    fill.mutate(
      { previewId, data: { ...values, rowNumbers: targets } },
      {
        onSettled: () => {
          isSending.current = false;
        },
        onSuccess: () => {
          onSuccess?.();
          onClose();
        },
        onError: (error) => {
          // The global MSG07 dialog is the one message for a 401 (guideline 06 §6).
          if (error.status === 401) return;
          if (!applyApiErrors(form, error)) showMessage(error.code, error.params);
          onError?.(error);
        },
      },
    );
  }

  function handleSubmit(values: SolutionValues) {
    if (isSending.current) return;
    if (rowNo === null && replaceCount > 0) setPendingValues(values);
    else save(values);
  }

  return (
    <>
      <FormModal<SolutionValues>
        isOpen={rowNo !== null || rowNumbers.length > 0}
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
        {rowNo !== null ? <RowFields previewId={previewId} rowNo={rowNo} /> : <SolutionFields />}
      </FormModal>
      <ConfirmDialog
        isOpen={pendingValues !== null}
        code="MSG36"
        params={{ count: replaceCount }}
        onConfirm={() => {
          if (pendingValues) save(pendingValues);
          setPendingValues(null);
        }}
        onCancel={() => setPendingValues(null)}
      />
    </>
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
          ...jiraSnapshotItems(data),
        ]}
      />
      {/* Mounted once the row is read, so each field starts from the row's saved section (edge case 51). */}
      <SolutionFields values={data} />
    </Flex>
  );
}

/** The Solution sections as fields, in template order — also on the Manual entry tab. */
export function SolutionFields({
  values,
}: {
  values?: Pick<ImportPreviewRowResponse, keyof SolutionValues>;
}) {
  return (
    <Flex vertical gap="middle">
      {SECTIONS.map(([label, field]) => (
        <FormField<SolutionValues>
          key={field}
          name={field}
          label={label}
          initialValue={values?.[field] ?? undefined}
          rules={field === 'workaround' ? [] : [rules.required(label)]}
        >
          <Input.TextArea rows={3} />
        </FormField>
      ))}
    </Flex>
  );
}
