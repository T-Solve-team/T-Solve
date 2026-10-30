/** 1.3.3 Hand Over Responsibilities — D3 replaces the generic confirmation. */
import { Alert, Flex, Form, Select } from 'antd';
import type { ApiError, UpdateUserStatusRequest } from '@/shared/api';
import { rules } from '@/shared/forms';
import { msg, showMessage } from '@/shared/messages';
import { ErrorState, FormField, FormModal, LoadingState } from '@/shared/ui';
import { useUserHandoverOptions } from '../hooks/useUserDetail';

type Handovers = Pick<UpdateUserStatusRequest, 'projectHandovers' | 'spaceHandovers'>;
type HandOverResponsibilitiesModalProps = {
  userId: string;
  userName: string;
  isSubmitting: boolean;
  scope?: { projectIds: string[]; spaceIds: string[] };
  onCancel: () => void;
  onSubmit: (values: Handovers, onError: (error: ApiError) => void) => void;
};

export function HandOverResponsibilitiesModal({
  userId,
  userName,
  isSubmitting,
  scope,
  onCancel,
  onSubmit,
}: HandOverResponsibilitiesModalProps) {
  const options = useUserHandoverOptions(userId);
  const [form] = Form.useForm<Handovers>();
  const projects = (options.data?.projects ?? []).filter(
    (item) => !scope || scope.projectIds.includes(item.project.projectId),
  );
  const spaces = (options.data?.spaces ?? []).filter(
    (item) => !scope || scope.spaceIds.includes(item.space.spaceId),
  );
  const fields = [
    ...projects.map(({ project, recipients }, index) => ({
      id: project.projectId,
      label: project.name,
      recipients,
      name: ['projectHandovers', index, 'recipientId'] satisfies [
        'projectHandovers',
        number,
        'recipientId',
      ],
      error: recipients.length
        ? undefined
        : msg('MSG59', { department_name: project.department.name }),
    })),
    ...spaces.map(({ space, recipients }, index) => ({
      id: space.spaceId,
      label: space.name,
      recipients,
      name: ['spaceHandovers', index, 'recipientId'] satisfies [
        'spaceHandovers',
        number,
        'recipientId',
      ],
      error: recipients.length ? undefined : msg('MSG118'),
    })),
  ];
  const unavailable = !options.isSuccess || fields.some((field) => !field.recipients.length);

  function submit(values: Handovers) {
    if (!options.data || unavailable || isSubmitting) return;
    onSubmit(
      {
        projectHandovers: projects.map((item, index) => ({
          projectId: item.project.projectId,
          recipientId: values.projectHandovers?.[index]?.recipientId ?? '',
        })),
        spaceHandovers: spaces.map((item, index) => ({
          spaceId: item.space.spaceId,
          recipientId: values.spaceHandovers?.[index]?.recipientId ?? '',
        })),
      },
      (error) => {
        // Assignment errors can target fields on the underlying edit form, not this popup.
        showMessage(error.code, error.params);
      },
    );
  }

  return (
    <FormModal<Handovers>
      isOpen
      title="Hand Over Responsibilities"
      form={form}
      okText={scope ? 'Save' : 'Deactivate'}
      isOkDanger={!scope}
      isOkDisabled={unavailable}
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
            action: scope ? 'updating roles and assignments' : 'deactivation',
          })}
        />
        {options.isPending && <LoadingState />}
        {options.isError && (
          <ErrorState error={options.error} onRetry={() => void options.refetch()} />
        )}
        {fields.map((field) => (
          <div key={field.id}>
            {field.error && <Alert type="error" title={field.error} />}
            <FormField<Handovers>
              name={field.name}
              label={field.label}
              rules={[rules.required(field.label)]}
            >
              <Select
                virtual={false}
                disabled={isSubmitting || !options.isSuccess || !field.recipients.length}
                options={field.recipients.map((recipient) => ({
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
