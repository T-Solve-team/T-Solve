import { Button, Flex, Form, Input } from 'antd';
import { useRef } from 'react';
import type { WorkspaceSetupRequest } from '@/shared/api';
import { applyApiErrors, rules } from '@/shared/forms';
import { showMessage } from '@/shared/messages';
import { AppForm, FormField, SectionCard } from '@/shared/ui';
import { isWorkspaceAlreadySetUp, useSetUpWorkspace } from '../hooks/useWorkspace';

export function WorkspaceSetupForm() {
  const [form] = Form.useForm<WorkspaceSetupRequest>();
  const setup = useSetUpWorkspace();
  const submitting = useRef(false);

  function submit(values: WorkspaceSetupRequest) {
    if (submitting.current) return;
    submitting.current = true;
    setup.mutate(
      { data: values },
      {
        onError: (error) => {
          if (isWorkspaceAlreadySetUp(error)) return;
          if (!applyApiErrors(form, error)) showMessage(error.code, error.params);
        },
        onSettled: () => {
          submitting.current = false;
        },
      },
    );
  }

  return (
    <SectionCard title="Company information">
      <AppForm<WorkspaceSetupRequest> form={form} onFinish={submit} disabled={setup.isPending}>
        <FormField<WorkspaceSetupRequest>
          name="companyName"
          label="Company name"
          rules={[rules.required('Company name'), rules.maxLength('Company name', 100)]}
        >
          <Input autoComplete="organization" count={{ show: true, max: 100 }} />
        </FormField>
        <FormField<WorkspaceSetupRequest>
          name="jiraSiteUrl"
          label="Jira site address"
          rules={[rules.required('Jira site address'), rules.maxLength('Jira site address', 200)]}
        >
          <Input placeholder="https://company.atlassian.net" count={{ show: true, max: 200 }} />
        </FormField>
        <FormField<WorkspaceSetupRequest>
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
        <FormField<WorkspaceSetupRequest>
          name="serviceAccountToken"
          label="Service account API token"
          rules={[rules.required('Service account API token')]}
        >
          <Input.Password autoComplete="new-password" />
        </FormField>
        <Flex justify="end">
          <Button
            aria-label="Save"
            aria-busy={setup.isPending}
            type="primary"
            htmlType="submit"
            loading={setup.isPending}
            disabled={setup.isPending}
          >
            Save
          </Button>
        </Flex>
      </AppForm>
    </SectionCard>
  );
}
