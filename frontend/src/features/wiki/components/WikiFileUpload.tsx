import { InboxOutlined } from '@ant-design/icons';
// eslint-disable-next-line no-restricted-imports -- Task 8 requires Wiki-specific beforeUpload rules; no shared Wiki uploader exists (guideline 11).
import { Upload } from 'antd';
import { FormField } from '@/shared/ui';

type WikiFileUploadProps = {
  isBusy: boolean;
  onAdd: (files: File[]) => void;
};

/** Only extract/publish send files through the generated client. */
export function WikiFileUpload({ isBusy, onAdd }: WikiFileUploadProps) {
  return (
    <FormField label="Files" htmlFor="wiki-files">
      <Upload.Dragger
        id="wiki-files"
        aria-label="Files"
        multiple
        accept={{ format: '.pdf,.docx', filter: 'native' }}
        disabled={isBusy}
        fileList={[]}
        showUploadList={false}
        beforeUpload={(file, files) => {
          // beforeUpload runs per file; handle the whole selection once.
          if (file === files[0]) onAdd(files);
          return Upload.LIST_IGNORE;
        }}
      >
        <InboxOutlined aria-hidden />
        <p>PDF or DOCX, up to 10 files, at most 10 MB each.</p>
      </Upload.Dragger>
    </FormField>
  );
}
