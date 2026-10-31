import { LoadingOutlined } from '@ant-design/icons';
import { Button, Flex, Typography } from 'antd';
import { useId, useRef, useState } from 'react';
import {
  UserAccountStatus,
  type ApiError,
  type UpdateUserStatusRequest,
  type UserDetail,
} from '@/shared/api';
import { msg, showMessage } from '@/shared/messages';
import { ConfirmDialog } from '@/shared/ui';
import { useChangeUserStatus } from '../hooks/useUserMutations';
import { HandOverResponsibilitiesModal } from './HandOverResponsibilitiesModal';

type UserStatusActionsProps = { user: UserDetail };

export function UserStatusActions({ user }: UserStatusActionsProps) {
  const selfHelpId = useId();
  const [dialog, setDialog] = useState<'confirm' | 'handover' | null>(null);
  const change = useChangeUserStatus();
  const submitting = useRef(false);
  const isActive = user.status === UserAccountStatus.ACTIVE;
  const isSelfDeactivation = isActive && user.isCurrentUser;
  const needsHandover =
    user.managedSpaces.length > 0 ||
    user.projects.some((project) => project.isActive && project.managerId === user.id);

  function save(data: UpdateUserStatusRequest, onError?: (error: ApiError) => void) {
    if (submitting.current) return;
    submitting.current = true;
    change.mutate(
      { userId: user.id, data },
      {
        onSuccess: () => setDialog(null),
        onError: onError ?? ((error) => showMessage(error.code, error.params)),
        onSettled: () => {
          submitting.current = false;
        },
      },
    );
  }

  function close() {
    if (!submitting.current) setDialog(null);
  }

  return (
    <>
      <Flex vertical align="end" gap="small">
        <Button
          aria-describedby={isSelfDeactivation ? selfHelpId : undefined}
          danger={isActive}
          disabled={isSelfDeactivation || change.isPending}
          icon={change.isPending ? <LoadingOutlined aria-hidden /> : undefined}
          aria-busy={change.isPending}
          onClick={() => {
            if (isActive) setDialog(needsHandover ? 'handover' : 'confirm');
            else save({ status: UserAccountStatus.ACTIVE });
          }}
        >
          {isActive ? 'Deactivate' : 'Reactivate'}
        </Button>
        {isSelfDeactivation && <Typography.Text id={selfHelpId}>{msg('MSG112')}</Typography.Text>}
      </Flex>
      {dialog === 'confirm' && (
        <ConfirmDialog
          isOpen={dialog === 'confirm'}
          code="MSG57"
          params={{ user_name: user.fullName ?? user.email }}
          isDanger
          isConfirming={change.isPending}
          onCancel={close}
          onConfirm={() => save({ status: UserAccountStatus.DEACTIVATED })}
        />
      )}
      {dialog === 'handover' && (
        <HandOverResponsibilitiesModal
          userId={user.id}
          userName={user.fullName ?? user.email}
          isSubmitting={change.isPending}
          onCancel={close}
          onSubmit={(handovers, onError) =>
            save({ ...handovers, status: UserAccountStatus.DEACTIVATED }, onError)
          }
        />
      )}
    </>
  );
}
