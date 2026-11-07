import { useMutation } from '@tanstack/react-query';
import { Alert, Button, Flex, Form, Select } from 'antd';
import { useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import {
  downloadCsvTemplate,
  useUploadCsvImport,
  type ApiError,
  type UploadCsvImportBody,
} from '@/shared/api';
import { rules } from '@/shared/forms';
import { MESSAGES, msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { AppForm, CsvUpload, FormField } from '@/shared/ui';
import { useManagedProjectOptions } from '../hooks/useManagedProjects';
import { saveTextFile } from '../lib/save-text-file';

const TEMPLATE_FILE_NAME = 'ticket-import-template.csv';

/**
 * CSV tab of 5.1 Import Tickets — SRS III.5.1, UC-21 steps 2–4: Project *, Download template, CSV file *, Upload.
 * Success opens the Import Preview; a rejected file keeps the chosen Project and file (AC-008.7).
 */
export function CsvImportForm() {
  const [form] = Form.useForm<UploadCsvImportBody>();
  const navigate = useNavigate();
  // An inline code returned by the upload (MSG29, MSG04, MSG08), shown above the form.
  const [rejection, setRejection] = useState<ApiError | null>(null);
  const projects = useManagedProjectOptions();

  // Errors: the global toast.
  const template = useMutation({
    mutationFn: () => downloadCsvTemplate(undefined, { headers: { Accept: 'text/csv' } }),
    onSuccess: (text) => saveTextFile(text, TEMPLATE_FILE_NAME),
  });

  const upload = useUploadCsvImport({ mutation: { meta: { handlesOwnErrors: true } } });
  // A double click passes the async validation twice before `isPending` renders: one upload only (AC-008.8).
  const isSending = useRef(false);

  function handleFinish(values: UploadCsvImportBody) {
    if (isSending.current) return;
    isSending.current = true;
    setRejection(null);
    upload.mutate(
      { data: values },
      {
        onSettled: () => {
          isSending.current = false;
        },
        onSuccess: (preview) =>
          void navigate(`${paths.importPreview}?previewId=${preview.previewId}`),
        onError: (error) => {
          // The global MSG07 dialog is the one message for a 401 (guideline 06 §6).
          if (error.status === 401) return;
          if (MESSAGES[error.code].type === 'field') {
            form.setFields([{ name: 'file', errors: [msg(error.code, error.params)] }]);
          } else {
            setRejection(error);
          }
        },
      },
    );
  }

  return (
    <Flex vertical gap="middle">
      {rejection && <Alert type="error" showIcon title={msg(rejection.code, rejection.params)} />}
      <AppForm<UploadCsvImportBody> form={form} onFinish={handleFinish}>
        <FormField<UploadCsvImportBody>
          name="projectId"
          label="Project"
          rules={[rules.required('Project')]}
        >
          <Select options={projects} virtual={false} />
        </FormField>
        <Button type="link" loading={template.isPending} onClick={() => template.mutate()}>
          Download template
        </Button>
        <CsvUpload name="file" label="CSV file" required />
        <Button type="primary" htmlType="submit" loading={upload.isPending}>
          Upload
        </Button>
      </AppForm>
    </Flex>
  );
}
