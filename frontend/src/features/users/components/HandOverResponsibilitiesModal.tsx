/** 1.3.3 Hand Over Responsibilities — D3 replaces the generic confirmation. */
import { Alert, Flex, Form, Select } from 'antd';
import type { ApiError, UpdateUserStatusRequest } from '@/shared/api';
import { applyApiErrors, rules } from '@/shared/forms';
import { msg, showMessage } from '@/shared/messages';
import { ErrorState, FormField, FormModal, LoadingState } from '@/shared/ui';
import { useUserHandoverOptions } from '../hooks/useUserDetail';

type Handovers = Pick<UpdateUserStatusRequest, 'projectHandovers' | 'spaceHandovers'>;
type HandOverResponsibilitiesModalProps = {
  userId: string;
  userName: string;
  isSubmitting: boolean;
  onCancel: () => void;
  onSubmit: (values: Handovers, onError: (error: ApiError) => void) => void;
};

export function HandOverResponsibilitiesModal({
  userId,
  userName,
  isSubmitting,
  onCancel,
  onSubmit,
}: HandOverResponsibilitiesModalProps) {
  const options = useUserHandoverOptions(userId);
  const [form] = Form.useForm<Handovers>();
  const unavailable =
    options.data?.projects.some((item) => !item.recipients.length) ||
    options.data?.spaces.some((item) => !item.recipients.length);

  function submit(values: Handovers) {
    if (!options.data || unavailable || isSubmitting) return;
    onSubmit(
      {
        projectHandovers: options.data.projects.map((item, index) => ({
          projectId: item.project.projectId,
          recipientId: values.projectHandovers?.[index]?.recipientId ?? '',
        })),
        spaceHandovers: options.data.spaces.map((item, index) => ({
          spaceId: item.space.spaceId,
          recipientId: values.spaceHandovers?.[index]?.recipientId ?? '',
        })),
      },
      (error) => {
        if (!applyApiErrors(form, error)) showMessage(error.code, error.params);
      },
    );
  }

  return (
    <FormModal<Handovers>
      isOpen
      title="Hand Over Responsibilities"
      form={form}
      okText="Deactivate"
      isOkDanger
      isSubmitting={isSubmitting}
      onSubmit={submit}
      onCancel={onCancel}
    >
      <Flex vertical gap="middle">
        <Alert
          type="warning"
          showIcon
          title={msg('MSG_NEW_2', {
            user_name: userName,
            responsibilities: 'Projects and Spaces',
            action: 'deactivation',
          })}
        />
        {options.isPending && <LoadingState />}
        {options.isError && (
          <ErrorState error={options.error} onRetry={() => void options.refetch()} />
        )}
        {options.data?.projects.map(({ project, recipients }, index) => (
          <div key={project.projectId}>
            {!recipients.length && (
              <Alert
                type="error"
                title={msg('MSG59', { department_name: project.department.name })}
              />
            )}
            <FormField<Handovers>
              name={['projectHandovers', index, 'recipientId']}
              label={project.name}
              rules={[rules.required(project.name)]}
            >
              <Select
                virtual={false}
                disabled={isSubmitting || !recipients.length}
                options={recipients.map((recipient) => ({
                  value: recipient.id,
                  label: `${recipient.fullName ?? recipient.email} (${recipient.email})`,
                }))}
              />
            </FormField>
          </div>
        ))}
        {options.data?.spaces.map(({ space, recipients }, index) => (
          <div key={space.spaceId}>
            {!recipients.length && <Alert type="error" title={msg('MSG118')} />}
            <FormField<Handovers>
              name={['spaceHandovers', index, 'recipientId']}
              label={space.name}
              rules={[rules.required(space.name)]}
            >
              <Select
                virtual={false}
                disabled={isSubmitting || !recipients.length}
                options={recipients.map((recipient) => ({
                  value: recipient.id,
                  label: `${recipient.fullName ?? recipient.email} (${recipient.email})`,
                }))}
              />
            </FormField>
          </div>
        ))}
      </Flex>
    </FormModal>
  );
}
