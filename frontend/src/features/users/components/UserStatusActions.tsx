import { LoadingOutlined } from '@ant-design/icons';
import { Button, Flex, Typography } from 'antd';
import { useId, useRef, useState } from 'react';
import {
  isUnauthorizedError,
  UserAccountStatus,
  type ApiError,
  type UpdateUserStatusRequest,
  type UserDetail,
} from '@/shared/api';
import { msg, showMessage } from '@/shared/messages';
import { ConfirmDialog, ErrorState, LoadingState } from '@/shared/ui';
import { useUserHandoverOptions } from '../hooks/useUserDetail';
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
  const options = useUserHandoverOptions(user.id, false);
  const [lookupStatus, setLookupStatus] = useState<'idle' | 'checking' | 'error'>('idle');
  const isChecking = lookupStatus === 'checking';
  const checking = useRef(false);

  async function checkHandover() {
    if (checking.current || submitting.current) return;
    checking.current = true;
    setLookupStatus('checking');
    const result = await options.refetch();
    checking.current = false;
    setLookupStatus(result.isSuccess ? 'idle' : 'error');
    if (result.isSuccess) {
      setDialog(result.data.projects.length || result.data.spaces.length ? 'handover' : 'confirm');
    }
  }

  function save(data: UpdateUserStatusRequest, onError?: (error: ApiError) => void) {
    if (submitting.current) return;
    submitting.current = true;
    change.mutate(
      { userId: user.id, data },
      {
        onSuccess: () => setDialog(null),
        onError:
          onError ??
          ((error) => {
            if (isUnauthorizedError(error)) return;
            showMessage(error.code, error.params);
          }),
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
          disabled={isSelfDeactivation || change.isPending || isChecking}
          icon={change.isPending ? <LoadingOutlined aria-hidden /> : undefined}
          aria-busy={change.isPending}
          onClick={() => {
            if (isActive) void checkHandover();
            else save({ status: UserAccountStatus.ACTIVE });
          }}
        >
          {isActive ? 'Deactivate' : 'Reactivate'}
        </Button>
        {isSelfDeactivation && <Typography.Text id={selfHelpId}>{msg('MSG112')}</Typography.Text>}
      </Flex>
      {isChecking && <LoadingState />}
      {lookupStatus === 'error' && options.isError && (
        <ErrorState error={options.error} onRetry={() => void checkHandover()} />
      )}
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
