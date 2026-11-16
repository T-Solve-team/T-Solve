import { Alert, Form, Select } from 'antd';
import { useRef } from 'react';
import { useListWikiPermissionCandidates, type GrantWikiPermissionsRequest } from '@/shared/api';
import { msg } from '@/shared/messages';
import { EmptyState, ErrorState, FormField, FormModal, LoadingState } from '@/shared/ui';
import { useWikiPermissionMutations } from '../hooks/useWikiPermissionMutations';

type GrantAccessModalProps = { spaceId: string; onClose: () => void };

export function GrantAccessModal({ spaceId, onClose }: GrantAccessModalProps) {
  const [form] = Form.useForm<GrantWikiPermissionsRequest>();
  const candidates = useListWikiPermissionCandidates(spaceId);
  const { grant } = useWikiPermissionMutations(spaceId);
  const submitting = useRef(false);
  const departmentIds = Form.useWatch('departmentIds', form) as string[] | undefined;
  const userIds = Form.useWatch('userIds', form) as string[] | undefined;
  const hasSelection = Boolean(departmentIds?.length || userIds?.length);
  const hasCandidates = Boolean(
    candidates.data?.departments.length || candidates.data?.users.length,
  );

  return (
    <FormModal<GrantWikiPermissionsRequest>
      isOpen
      title="Grant access"
      form={form}
      okText="Save"
      isSubmitting={grant.isPending}
      isOkDisabled={!hasSelection || !hasCandidates || candidates.isError || candidates.isPending}
      onCancel={() => {
        if (!submitting.current) onClose();
      }}
      onSubmit={(values) => {
        if (submitting.current) return;
        submitting.current = true;
        grant.mutate(
          {
            spaceId,
            data: { departmentIds: values.departmentIds ?? [], userIds: values.userIds ?? [] },
          },
          {
            onSuccess: onClose,
            onSettled: () => {
              submitting.current = false;
            },
          },
        );
      }}
    >
      {grant.error && (
        <Alert type="error" showIcon title={msg(grant.error.code, grant.error.params)} />
      )}
      {candidates.isPending ? (
        <LoadingState />
      ) : candidates.isError ? (
        <ErrorState error={candidates.error} onRetry={() => void candidates.refetch()} />
      ) : !hasCandidates ? (
        <EmptyState />
      ) : (
        <>
          <FormField<GrantWikiPermissionsRequest>
            name="departmentIds"
            label="Departments"
            initialValue={[]}
          >
            <Select
              mode="multiple"
              virtual={false}
              optionFilterProp="label"
              options={candidates.data.departments.map((department) => ({
                value: department.departmentId,
                label: department.name,
              }))}
            />
          </FormField>
          <FormField<GrantWikiPermissionsRequest> name="userIds" label="Users" initialValue={[]}>
            <Select
              mode="multiple"
              virtual={false}
              optionFilterProp="label"
              options={candidates.data.users.map((user) => ({
                value: user.userId,
                label: `${user.fullName} (${user.email})${user.homeDepartment ? ` · ${user.homeDepartment}` : ''}`,
              }))}
            />
          </FormField>
        </>
      )}
    </FormModal>
  );
}
