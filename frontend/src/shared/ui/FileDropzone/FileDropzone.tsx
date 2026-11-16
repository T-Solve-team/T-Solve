import { InboxOutlined } from '@ant-design/icons';
import { Upload } from 'antd';
import { useId } from 'react';
import { FormField } from '../AppForm';

type FileDropzoneProps = {
  /** Visible label, also the accessible name. */
  label: string;
  /** File extensions for the picker, e.g. ".pdf,.docx". A dropped file of another type still reaches `onAdd`. */
  accept: string;
  /** The rules shown under the icon (from the SRS screen layout). */
  hint: string;
  disabled?: boolean;
  /** The whole selection, once per pick or drop. The caller checks and keeps the files. */
  onAdd: (files: File[]) => void;
};

/**
 * Figma "Web/Upload" for several files: click or drag. It keeps no list and sends nothing —
 * the screen shows the chosen files and sends them (guideline 09 §6). One file in a form: `CsvUpload`.
 */
export function FileDropzone({ label, accept, hint, disabled = false, onAdd }: FileDropzoneProps) {
  const id = useId();
  return (
    <FormField label={label} htmlFor={id}>
      <Upload.Dragger
        id={id}
        aria-label={label}
        multiple
        accept={{ format: accept, filter: 'native' }}
        disabled={disabled}
        fileList={[]}
        showUploadList={false}
        beforeUpload={(file, files) => {
          // beforeUpload runs per file; hand over the whole selection once.
          if (file === files[0]) onAdd(files);
          return Upload.LIST_IGNORE;
        }}
      >
        <p className="ant-upload-drag-icon">
          <InboxOutlined aria-hidden />
        </p>
        <p className="ant-upload-hint">{hint}</p>
      </Upload.Dragger>
    </FormField>
  );
}
