import { Button, Input, Select } from 'antd';
import type { WikiPublishMetadata, WikiUploadPreview } from '@/shared/api';
import { msg } from '@/shared/messages';
import { DataTable, FormField, type DataTableColumn } from '@/shared/ui';

/** Editable upload draft; retain the original File until publish or cancel. */
export type WikiUploadRow = WikiUploadPreview & {
  id: string;
  file: File;
  visibility: WikiPublishMetadata['documents'][number]['visibility'];
  titleErrors?: string[];
};

const VISIBILITIES = [
  { value: 'DEPARTMENT', label: 'Department' },
  { value: 'COMPANY_WIDE', label: 'Company-wide' },
];

type WikiUploadTableProps = {
  rows: WikiUploadRow[];
  isBusy: boolean;
  onChange: (id: string, change: Partial<Pick<WikiUploadRow, 'title' | 'visibility'>>) => void;
  onRemove: (id: string) => void;
};

export function WikiUploadTable({ rows, isBusy, onChange, onRemove }: WikiUploadTableProps) {
  const columns: DataTableColumn<WikiUploadRow>[] = [
    { key: 'fileName', title: 'File name', dataIndex: 'fileName', width: 170 },
    {
      key: 'visibility',
      title: 'Visibility',
      width: 180,
      render: (_, row) => (
        <FormField label="Visibility" htmlFor={`visibility-${row.id}`}>
          <Select
            id={`visibility-${row.id}`}
            aria-label={`Visibility for ${row.fileName}`}
            value={row.visibility}
            options={VISIBILITIES}
            virtual={false}
            disabled={isBusy}
            onChange={(visibility: WikiUploadRow['visibility']) => onChange(row.id, { visibility })}
          />
        </FormField>
      ),
    },
    {
      key: 'title',
      title: 'Title',
      width: 250,
      render: (_, row) => {
        const localError = !row.title.trim()
          ? msg('MSG01', { field_name: 'Title' })
          : row.title.length > 200
            ? msg('MSG02', { field_name: 'Title', max_length: 200 })
            : undefined;
        const errors = localError ? [localError] : (row.titleErrors ?? []);
        return (
          <FormField
            label="Title"
            htmlFor={`title-${row.id}`}
            required
            validateStatus={errors.length ? 'error' : undefined}
            help={errors.map((error) => (
              <div key={error}>{error}</div>
            ))}
          >
            <Input
              id={`title-${row.id}`}
              aria-label={`Title for ${row.fileName}`}
              value={row.title}
              count={{ show: true, max: 200 }}
              disabled={isBusy}
              onChange={(event) => onChange(row.id, { title: event.target.value })}
            />
          </FormField>
        );
      },
    },
    { key: 'excerpt', title: 'Extracted text', dataIndex: 'excerpt', width: 240 },
    { key: 'wordCount', title: 'Words', dataIndex: 'wordCount', width: 80 },
    {
      key: 'result',
      title: 'Result',
      width: 200,
      render: (_, row) =>
        row.result === 'READY' ? 'Ready' : msg('MSG90', { file_name: row.fileName }),
    },
    {
      key: 'remove',
      title: '',
      width: 100,
      render: (_, row) => (
        <Button
          disabled={isBusy}
          aria-label={`Remove ${row.fileName}`}
          onClick={() => onRemove(row.id)}
        >
          Remove
        </Button>
      ),
    },
  ];
  return (
    <DataTable
      label="Documents to publish"
      columns={columns}
      rows={rows}
      rowKey={(row) => row.id}
      scrollX={1220}
    />
  );
}
