import { Button, Flex, Form, Input, Typography } from 'antd';
import type { WorkspaceInfo, WorkspaceUpdateRequest } from '@/shared/api';
import { applyApiErrors, rules } from '@/shared/forms';
import { showMessage } from '@/shared/messages';
import { AppForm, FormField, SectionCard } from '@/shared/ui';
import { useUpdateWorkspaceInfo } from '../hooks/useWorkspace';

type WorkspaceInfoFormProps = { workspace: WorkspaceInfo };

/** An empty token keeps the stored one, so it is left out of the request (AC-006.3, edge 19). */
function toRequest({
  serviceAccountToken,
  ...rest
}: WorkspaceUpdateRequest): WorkspaceUpdateRequest {
  return serviceAccountToken?.trim() ? { ...rest, serviceAccountToken } : rest;
}

/** 2.2 "Company information" section (STORY-006). */
export function WorkspaceInfoForm({ workspace }: WorkspaceInfoFormProps) {
  const [form] = Form.useForm<WorkspaceUpdateRequest>();
  const update = useUpdateWorkspaceInfo();
  // Only these two fields: a stored token is never put into the form (AC-006.2).
  const loaded = {
    companyName: workspace.companyName,
    serviceAccountEmail: workspace.serviceAccountEmail,
  };

  function submit(values: WorkspaceUpdateRequest) {
    update.mutate(
      { data: toRequest(values) },
      {
        onSuccess: () => form.resetFields(['serviceAccountToken']),
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
          name="serviceAccountEmail"
          label="Service account email"
          rules={[
            rules.required('Service account email'),
            rules.maxLength('Service account email', 100),
            rules.email(),
          ]}
        >
          <Input autoComplete="username" count={{ show: true, max: 100 }} />
        </FormField>
        <FormField<WorkspaceUpdateRequest>
          name="serviceAccountToken"
          label="Service account API token"
          extra="Leave empty to keep the current token."
        >
          <Input.Password autoComplete="new-password" />
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
