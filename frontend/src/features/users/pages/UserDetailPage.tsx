/** 1.3.2 User Detail — SRS III.1.3.2 */
import { Button, Checkbox, Flex, Form, Input, Select, Typography } from 'antd';
import { useRef } from 'react';
import { useNavigate, useParams } from 'react-router';
import {
  Role,
  useGetUserCreationOptions,
  useGetUserDepartments,
  type CreateUserRequest,
} from '@/shared/api';
import { ROLE_LABELS, ROLES } from '@/shared/auth';
import { applyApiErrors, rules } from '@/shared/forms';
import { msg, showMessage } from '@/shared/messages';
import { paths } from '@/shared/routing';
import {
  AppForm,
  EmptyState,
  ErrorState,
  FormField,
  LoadingState,
  ScreenPlaceholder,
  SectionCard,
} from '@/shared/ui';
import { useAddUser } from '../hooks/useUserMutations';

function CreateUserForm() {
  const navigate = useNavigate();
  const [form] = Form.useForm<CreateUserRequest>();
  const roles: CreateUserRequest['roles'] = Form.useWatch('roles', form) ?? [Role.EMPLOYEE];
  const departments = useGetUserDepartments();
  const options = useGetUserCreationOptions();
  const create = useAddUser();
  const submitting = useRef(false);
  const isProjectManager = roles.includes(Role.PROJECT_MANAGER);
  const isKnowledgeManager = roles.includes(Role.KNOWLEDGE_MANAGER);

  function submit(values: CreateUserRequest) {
    if (submitting.current) return;
    submitting.current = true;
    create.mutate(
      {
        data: {
          ...values,
          email: values.email.trim(),
          roles: [...new Set([Role.EMPLOYEE, ...values.roles])],
          spaceIds: isKnowledgeManager ? values.spaceIds : [],
        },
      },
      {
        onSuccess: () => void navigate(paths.userList),
        onError: (error) => {
          if (!applyApiErrors(form, error)) showMessage(error.code, error.params);
        },
        onSettled: () => {
          submitting.current = false;
        },
      },
    );
  }

  if (departments.isPending || options.isPending) return <LoadingState />;
  if (departments.isError)
    return <ErrorState error={departments.error} onRetry={() => void departments.refetch()} />;
  if (options.isError)
    return <ErrorState error={options.error} onRetry={() => void options.refetch()} />;

  return (
    <SectionCard title="Add user">
      <AppForm<CreateUserRequest>
        form={form}
        initialValues={{ roles: [Role.EMPLOYEE], spaceIds: [] }}
        onFinish={submit}
        disabled={create.isPending}
      >
        <FormField<CreateUserRequest>
          name="email"
          label="Email"
          rules={[rules.required('Email'), rules.maxLength('Email', 100), rules.email()]}
          extra={options.data.companyDomain}
        >
          <Input autoComplete="email" count={{ show: true, max: 100 }} />
        </FormField>
        <FormField<CreateUserRequest>
          name="departmentId"
          label="Department"
          rules={isProjectManager ? [{ ...rules.required('Department'), type: 'number' }] : []}
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
        {departments.data.length === 0 && <EmptyState />}
        <FormField<CreateUserRequest>
          name="roles"
          label="Roles"
          rules={[{ ...rules.required('Roles'), type: 'array' }]}
        >
          <Checkbox.Group
            options={ROLES.map((value) => ({
              value,
              label: ROLE_LABELS[value],
              disabled: value === Role.EMPLOYEE,
            }))}
          />
        </FormField>
        {isKnowledgeManager && (
          <FormField<CreateUserRequest>
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
        {isKnowledgeManager && options.data.spaces.length === 0 && (
          <Typography.Text>{msg('MSG04')}</Typography.Text>
        )}
        <Flex gap="small" justify="end">
          <Button onClick={() => void navigate(paths.userList)} disabled={create.isPending}>
            Cancel
          </Button>
          <Button
            type="primary"
            htmlType="submit"
            loading={create.isPending}
            disabled={create.isPending}
          >
            Save
          </Button>
        </Flex>
      </AppForm>
    </SectionCard>
  );
}

export function UserDetailPage() {
  const { userId } = useParams();
  return userId ? <ScreenPlaceholder number="1.3.2" name="User Detail" /> : <CreateUserForm />;
}

export default UserDetailPage;
