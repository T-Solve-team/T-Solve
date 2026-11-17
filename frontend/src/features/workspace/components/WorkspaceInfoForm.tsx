import { Button, Flex, Form, Input, Typography } from 'antd';
import type { WorkspaceInfo, WorkspaceUpdateRequest } from '@/shared/api';
import { applyApiErrors, rules } from '@/shared/forms';
import { formatDateTime } from '@/shared/lib/date';
import { showMessage } from '@/shared/messages';
import { AppForm, FormField, SectionCard } from '@/shared/ui';
import { useUpdateWorkspaceInfo } from '../hooks/useWorkspace';

type WorkspaceInfoFormProps = { workspace: WorkspaceInfo };

/** 2.2 "Company information" section (STORY-010). */
export function WorkspaceInfoForm({ workspace }: WorkspaceInfoFormProps) {
  const [form] = Form.useForm<WorkspaceUpdateRequest>();
  const update = useUpdateWorkspaceInfo();
  // Only the company name is edited (AC-010.3); T-Solve holds no Jira credential to show (KAN-349).
  const loaded = { companyName: workspace.companyName };

  function submit(values: WorkspaceUpdateRequest) {
    update.mutate(
      { data: { companyName: values.companyName } },
      {
        onError: (error) => {
          if (!applyApiErrors(form, error)) showMessage(error.code, error.params);
        },
      },
    );
  }

  return (
    <SectionCard title="Company information">
      <AppForm<WorkspaceUpdateRequest>
        form={form}
        initialValues={loaded}
        onFinish={submit}
        disabled={update.isPending}
      >
        <FormField<WorkspaceUpdateRequest>
          name="companyName"
          label="Company name"
          rules={[rules.required('Company name'), rules.maxLength('Company name', 100)]}
        >
          <Input autoComplete="organization" count={{ show: true, max: 100 }} />
        </FormField>
        <FormField<WorkspaceUpdateRequest> label="Jira site address">
          <Typography.Text>{workspace.jiraSiteUrl}</Typography.Text>
        </FormField>
        <FormField<WorkspaceUpdateRequest>
          label="Jira App"
          extra={
            workspace.jiraAppConnected
              ? undefined
              : 'Install the T-Solve Jira App on this Jira site.'
          }
        >
          <Typography.Text>
            {workspace.jiraAppConnected
              ? `Connected — last update ${formatDateTime(workspace.jiraAppTokenReceivedAt)}`
              : 'Not connected'}
          </Typography.Text>
        </FormField>
        <Flex justify="end" gap="small">
          <Button onClick={() => form.resetFields()}>Cancel</Button>
          <Button
            type="primary"
            htmlType="submit"
            loading={update.isPending}
            aria-busy={update.isPending}
          >
            Save
          </Button>
        </Flex>
      </AppForm>
    </SectionCard>
  );
}
