import { InboxOutlined } from '@ant-design/icons';
import { Button, Flex, Typography, Upload } from 'antd';
import { useState } from 'react';
import { msg } from '@/shared/messages';

export type EvidenceUploadValue = {
  keptFiles: { id: string; name: string; size: number }[];
  newFiles: File[];
};

type EvidenceUploadProps = {
  value?: EvidenceUploadValue;
  onChange?: (value: EvidenceUploadValue) => void;
  maxFiles?: number;
  maxBytes?: number;
  accept?: string;
  error?: string;
  disabled?: boolean;
  /** Supplied by FormField so its visible label targets the file input. */
  id?: string;
};

const EMPTY_VALUE: EvidenceUploadValue = { keptFiles: [], newFiles: [] };
const ACCEPT = '.png,.jpg,.jpeg,.pdf,.docx,.xlsx,.txt,.log';
const FILE_IDS = new WeakMap<File, string>();

function fileId(file: File) {
  let id = FILE_IDS.get(file);
  if (!id) {
    id = crypto.randomUUID();
    FILE_IDS.set(file, id);
  }
  return id;
}

/** Figma Web/Upload for Evidence. Picks local files only; the enclosing form sends them. */
export function EvidenceUpload({
  value = EMPTY_VALUE,
  onChange,
  maxFiles = 5,
  maxBytes = 10 * 1024 * 1024,
  accept = ACCEPT,
  error,
  disabled = false,
  id,
}: EvidenceUploadProps) {
  // The error belongs to the value it was raised for, so a value replaced from outside (a form reset) drops it.
  const [validation, setValidation] = useState<{
    error: string;
    value: EvidenceUploadValue;
  } | null>(null);
  const validationError = validation?.value === value ? validation.error : null;

  function add(files: File[]) {
    if (disabled) return;
    const extensions = accept
      .toLowerCase()
      .split(',')
      .map((extension) => extension.trim());
    const newFiles = [...value.newFiles];
    const errors: string[] = [];
    for (const file of files) {
      const extension = file.name.slice(file.name.lastIndexOf('.')).toLowerCase();
      if (!extensions.includes(extension) || file.size > maxBytes) {
        errors.push(msg('MSG34', { file_name: file.name }));
      } else if (value.keptFiles.length + newFiles.length >= maxFiles) {
        errors.push(msg('MSG72'));
      } else {
        newFiles.push(file);
      }
    }
    const next = newFiles.length !== value.newFiles.length ? { ...value, newFiles } : value;
    const message = [...new Set(errors)].join(' ');
    setValidation(message ? { error: message, value: next } : null);
    if (next !== value) onChange?.(next);
  }

  function remove(next: EvidenceUploadValue) {
    if (disabled) return;
    setValidation(null);
    onChange?.(next);
  }

  return (
    <Flex vertical gap="small">
      <Upload.Dragger
        id={id}
        accept={{ format: accept, filter: 'native' }}
        multiple
        disabled={disabled}
        fileList={[]}
        showUploadList={false}
        beforeUpload={(file, batch) => {
          // Upload calls this for every file before React renders again: apply the whole batch once.
          if (file === batch[0]) add(batch);
          return Upload.LIST_IGNORE;
        }}
      >
        <p className="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p className="ant-upload-text">Evidence</p>
      </Upload.Dragger>
      {value.keptFiles.map((file) => (
        <Flex key={`kept-${file.id}`} align="center" justify="space-between" gap="small">
          <span>{file.name}</span>
          <Button
            disabled={disabled}
            aria-label={`Remove ${file.name}`}
            onClick={() =>
              remove({ ...value, keptFiles: value.keptFiles.filter((kept) => kept.id !== file.id) })
            }
          >
            Remove
          </Button>
        </Flex>
      ))}
      {value.newFiles.map((file) => (
        <Flex key={fileId(file)} align="center" justify="space-between" gap="small">
          <span>{file.name}</span>
          <Button
            disabled={disabled}
            aria-label={`Remove ${file.name}`}
            onClick={() =>
              remove({ ...value, newFiles: value.newFiles.filter((picked) => picked !== file) })
            }
          >
            Remove
          </Button>
        </Flex>
      ))}
      {(error || validationError) && (
        <Typography.Text type="danger">{error || validationError}</Typography.Text>
      )}
    </Flex>
  );
}
