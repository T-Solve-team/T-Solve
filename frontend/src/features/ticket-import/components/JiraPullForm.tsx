import { Alert, Button, DatePicker, Flex, Form, Select, type FormRule } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { usePullJiraImport, type ApiError } from '@/shared/api';
import { rules } from '@/shared/forms';
import { APP_TIME_ZONE } from '@/shared/lib';
import { MESSAGES, msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { AppForm, FormField } from '@/shared/ui';
import { useManagedProjectOptions } from '../hooks/useManagedProjects';

type JiraPullValues = { projectId: string; resolvedFrom: Dayjs; resolvedTo: Dayjs };

const DATE_FORMAT = 'DD/MM/YYYY';

function vietnamToday(): string {
  return dayjs().tz(APP_TIME_ZONE).format('YYYY-MM-DD');
}

/** MSG39: the start date after the end date, or either date in the future (AC-001.5). */
const validRange: FormRule = ({ getFieldValue }) => ({
  validator: () => {
    const from = getFieldValue('resolvedFrom') as Dayjs | undefined;
    const to = getFieldValue('resolvedTo') as Dayjs | undefined;
    const today = vietnamToday();
    const isInvalid =
      (from && from.format('YYYY-MM-DD') > today) ||
      (to && to.format('YYYY-MM-DD') > today) ||
      (from && to && from.isAfter(to, 'day'));
    return isInvalid ? Promise.reject(new Error(msg('MSG39'))) : Promise.resolve();
  },
});

/**
 * Jira tab of 5.1 Import Tickets — SRS III.5.1, UC-20 step 2: Project *, Resolved from *, Resolved to *, Pull from
 * Jira. Success opens the Import Preview; MSG04, MSG50 and MSG08 are shown above the form (AC-001.4…AC-001.8).
 */
export function JiraPullForm() {
  const [form] = Form.useForm<JiraPullValues>();
  const navigate = useNavigate();
  const projects = useManagedProjectOptions();
  const [rejection, setRejection] = useState<ApiError | null>(null);

  const pull = usePullJiraImport({ mutation: { meta: { handlesOwnErrors: true } } });
  // A double click passes validation twice before `isPending` renders: one pull only.
  const isSending = useRef(false);

  function handleFinish(values: JiraPullValues) {
    if (isSending.current) return;
    isSending.current = true;
    setRejection(null);
    pull.mutate(
      {
        data: {
          projectId: values.projectId,
          resolvedFrom: values.resolvedFrom.format('YYYY-MM-DD'),
          resolvedTo: values.resolvedTo.format('YYYY-MM-DD'),
        },
      },
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
            form.setFields([{ name: 'resolvedTo', errors: [msg(error.code, error.params)] }]);
          } else {
            setRejection(error);
          }
        },
      },
    );
  }

  const disabledDate = (date: Dayjs) => date.format('YYYY-MM-DD') > vietnamToday();

  return (
    <Flex vertical gap="middle">
      {rejection && (
        <Alert
          type="error"
          showIcon
          title={msg(rejection.code, { tracker_name: 'Jira', ...rejection.params })}
        />
      )}
      {/* name: field ids differ from the CSV tab's, which stays in the page (label → input). */}
      <AppForm<JiraPullValues> name="jiraPull" form={form} onFinish={handleFinish}>
        <FormField<JiraPullValues>
          name="projectId"
          label="Project"
          rules={[rules.required('Project')]}
        >
          <Select options={projects} virtual={false} />
        </FormField>
        <FormField<JiraPullValues>
          name="resolvedFrom"
          label="Resolved from"
          dependencies={['resolvedTo']}
          rules={[{ required: true, message: msg('MSG01', { field_name: 'Resolved from' }) }]}
        >
          <DatePicker format={DATE_FORMAT} disabledDate={disabledDate} />
        </FormField>
        <FormField<JiraPullValues>
          name="resolvedTo"
          label="Resolved to"
          dependencies={['resolvedFrom']}
          rules={[
            { required: true, message: msg('MSG01', { field_name: 'Resolved to' }) },
            validRange,
          ]}
        >
          <DatePicker format={DATE_FORMAT} disabledDate={disabledDate} />
        </FormField>
        <Button type="primary" htmlType="submit" loading={pull.isPending}>
          Pull from Jira
        </Button>
      </AppForm>
    </Flex>
  );
}
