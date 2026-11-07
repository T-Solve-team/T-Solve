import { InboxOutlined } from '@ant-design/icons';
import { Form, Upload, type UploadFile } from 'antd';
import { msg } from '@/shared/messages';
import { FormField } from '../AppForm';

const MAX_BYTES = 10 * 1024 * 1024;
/** SRS III.5.1, field "CSV file *". */
const HINT = 'One .csv file, UTF-8, at most 10 MB.';

type CsvUploadProps = {
  /** The form field (API property name), e.g. "file". Its value is the chosen `File`. */
  name: string;
  /** Visible label, also named by MSG01. */
  label: string;
  required?: boolean;
};

/**
 * Figma "Web/Upload" for one CSV file (decision D12): click or drag, the file name listed, removable.
 * A name not ending in .csv → MSG27, over 10 MB → MSG28, set on its own field; the file is then not kept.
 * Nothing is sent from here — the form sends the File (guideline 09 §6).
 */
export function CsvUpload({ name, label, required = false }: CsvUploadProps) {
  return (
    // rules.required checks a string; the value here is a File
    <FormField
      name={name}
      label={label}
      rules={
        required
          ? [{ required: true, type: 'object', message: msg('MSG01', { field_name: label }) }]
          : []
      }
    >
      <CsvDropZone name={name} />
    </FormField>
  );
}

type CsvDropZoneProps = { name: string; id?: string; value?: File };

/** `id` and `value` come from the surrounding Form.Item. */
function CsvDropZone({ name, id, value }: CsvDropZoneProps) {
  const form = Form.useFormInstance();
  const fileList: UploadFile[] = value ? [{ uid: 'csv', name: value.name, status: 'done' }] : [];

  function choose(file: File) {
    const code = !file.name.toLowerCase().endsWith('.csv')
      ? 'MSG27'
      : file.size > MAX_BYTES
        ? 'MSG28'
        : null;
    form.setFields([
      code ? { name, value: undefined, errors: [msg(code)] } : { name, value: file, errors: [] },
    ]);
  }

  return (
    <Upload.Dragger
      id={id}
      // 'native': the picker shows .csv files, but a dropped file of another type still reaches the check (MSG27)
      accept={{ format: '.csv', filter: 'native' }}
      maxCount={1}
      fileList={fileList}
      beforeUpload={(file) => {
        choose(file);
        return Upload.LIST_IGNORE; // never upload; the list shows the field value
      }}
      onRemove={() => {
        form.setFields([{ name, value: undefined, errors: [] }]);
      }}
    >
      <p className="ant-upload-drag-icon">
        <InboxOutlined />
      </p>
      <p className="ant-upload-hint">{HINT}</p>
    </Upload.Dragger>
  );
}
