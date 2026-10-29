import { Button, Checkbox, Flex, Form, Select, Tooltip } from 'antd';
import {
  Role,
  useGetUserCreationOptions,
  useGetUserDepartments,
  type UpdateUserRolesRequest,
  type UserDetail,
} from '@/shared/api';
import { ROLE_LABELS, ROLES } from '@/shared/auth';
import { applyApiErrors, rules } from '@/shared/forms';
import { msg, showMessage } from '@/shared/messages';
import {
  AppForm,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  FormField,
  LoadingState,
  SectionCard,
} from '@/shared/ui';
import { useRoleUpdateFlow } from '../hooks/useRoleUpdateFlow';
import { HandOverResponsibilitiesModal } from './HandOverResponsibilitiesModal';

type UserRolesFormProps = { user: UserDetail };

export function UserRolesForm({ user }: UserRolesFormProps) {
  const [form] = Form.useForm<UpdateUserRolesRequest>();
  const roles: UpdateUserRolesRequest['roles'] = Form.useWatch('roles', form) ?? user.roles;
  const departments = useGetUserDepartments();
  const options = useGetUserCreationOptions();
  const flow = useRoleUpdateFlow(user, (error) => {
    if (!applyApiErrors(form, error)) showMessage(error.code, error.params);
  });
  const isProjectManager = roles.includes(Role.PROJECT_MANAGER);
  const isKnowledgeManager = roles.includes(Role.KNOWLEDGE_MANAGER);
  const isBusy = flow.isPending || flow.step !== null;
  const protectsAdmin = user.isCurrentUser && user.roles.includes(Role.ADMIN);

  if (departments.isPending || options.isPending) return <LoadingState />;
  if (departments.isError)
    return <ErrorState error={departments.error} onRetry={() => void departments.refetch()} />;
  if (options.isError)
    return <ErrorState error={options.error} onRetry={() => void options.refetch()} />;

  return (
    <SectionCard title="Roles">
      <AppForm<UpdateUserRolesRequest>
        aria-label="User roles"
        form={form}
        disabled={isBusy}
        onFinish={flow.submit}
        initialValues={{
          roles: user.roles,
          departmentId: user.department?.departmentId ?? null,
          spaceIds: user.managedSpaces.map((space) => space.spaceId),
        }}
      >
        <FormField<UpdateUserRolesRequest>
          name="departmentId"
          label="Department"
          rules={isProjectManager ? [rules.required('Department')] : []}
        >
          <Select
            allowClear
            virtual={false}
            options={departments.data.map((department) => ({
              value: department.departmentId,
              label: department.name,
            }))}
          />
        </FormField>
        {!departments.data.length && <EmptyState />}
        <FormField<UpdateUserRolesRequest>
          name="roles"
          label="Roles"
          rules={[{ ...rules.required('Roles'), type: 'array' }]}
        >
          <Checkbox.Group
            options={ROLES.map((value) => ({
              value,
              disabled:
                isBusy || value === Role.EMPLOYEE || (value === Role.ADMIN && protectsAdmin),
              label:
                value === Role.ADMIN && protectsAdmin ? (
                  <Tooltip title={msg('MSG113')}>
                    <span>{ROLE_LABELS[value]}</span>
                  </Tooltip>
                ) : (
                  ROLE_LABELS[value]
                ),
            }))}
          />
        </FormField>
        {isKnowledgeManager && (
          <FormField<UpdateUserRolesRequest>
            name="spaceIds"
            label="Spaces"
            rules={[{ ...rules.required('Spaces'), type: 'array' }]}
          >
            <Select
              mode="multiple"
              virtual={false}
              options={options.data.spaces.map((space) => ({
                value: space.spaceId,
                label: space.name,
              }))}
            />
          </FormField>
        )}
        {isKnowledgeManager && !options.data.spaces.length && <EmptyState />}
        <Flex justify="end" gap="small">
          <Button disabled={isBusy} onClick={() => form.resetFields()}>
            Cancel
          </Button>
          <Button
            aria-label="Save"
            type="primary"
            htmlType="submit"
            loading={flow.isPending}
            disabled={isBusy}
          >
            Save
          </Button>
        </Flex>
      </AppForm>
      {flow.step === 'confirm-tr' && (
        <ConfirmDialog
          isOpen
          code="MSG78"
          isDanger
          params={{
            user_name: user.fullName ?? user.email,
            count: user.ticketResolverProjectIds.length,
          }}
          isConfirming={flow.isPending}
          onConfirm={flow.confirmRemoval}
          onCancel={flow.cancelStep}
        />
      )}
      {flow.step === 'handover' && (
        <HandOverResponsibilitiesModal
          userId={user.id}
          userName={user.fullName ?? user.email}
          isSubmitting={flow.isPending}
          scope={flow.handoverScope}
          onCancel={flow.cancelStep}
          onSubmit={flow.handover}
        />
      )}
    </SectionCard>
  );
}
