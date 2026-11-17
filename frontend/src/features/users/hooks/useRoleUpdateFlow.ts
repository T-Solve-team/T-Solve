import { useRef, useState } from 'react';
import {
  Role,
  type ApiError,
  type UpdateUserRolesRequest,
  type UpdateUserStatusRequest,
  type UserDetail,
} from '@/shared/api';
import { useUserHandoverOptions } from './useUserDetail';
import { useChangeUserRoles } from './useUserMutations';
import type { MsgParams } from '@/shared/messages';

type Handovers = Pick<UpdateUserStatusRequest, 'projectHandovers' | 'spaceHandovers'>;

export function useRoleUpdateFlow(user: UserDetail, onError: (error: ApiError) => void) {
  const change = useChangeUserRoles();
  const options = useUserHandoverOptions(user.id, false);
  const checking = useRef(false);
  const [step, setStep] = useState<'confirm-tr' | 'handover' | 'checking' | 'options-error' | null>(
    null,
  );
  const [requested, setRequested] = useState<UpdateUserRolesRequest | null>(null);
  const [confirmationParams, setConfirmationParams] = useState<MsgParams>({});
  const submitting = useRef(false);

  function scope(data: UpdateUserRolesRequest) {
    const removesManagement =
      !data.roles.includes(Role.PROJECT_MANAGER) ||
      data.departmentId !== (user.department?.departmentId ?? null);
    return {
      projectIds: user.projects
        .filter((project) => project.isActive && project.managerId === user.id && removesManagement)
        .map((project) => project.projectId),
      spaceIds: user.managedSpaces
        .filter(
          (space) =>
            !data.roles.includes(Role.KNOWLEDGE_MANAGER) || !data.spaceIds.includes(space.spaceId),
        )
        .map((space) => space.spaceId),
    };
  }

  function perform(data: UpdateUserRolesRequest, reportError?: (error: ApiError) => void) {
    if (submitting.current) return;
    submitting.current = true;
    change.mutate(
      { userId: user.id, data },
      {
        onSuccess: () => {
          setStep(null);
          setRequested(null);
        },
        onError: (error) => {
          if (error.code === 'MSG78') {
            setRequested(data);
            setConfirmationParams(error.params);
            setStep('confirm-tr');
          } else {
            if (!reportError) setStep(null);
            (reportError ?? onError)(error);
          }
        },
        onSettled: () => {
          submitting.current = false;
        },
      },
    );
  }

  async function proceed(data: UpdateUserRolesRequest) {
    if (checking.current || submitting.current) return;
    const removed = scope(data);
    setRequested(data);
    if (!removed.projectIds.length && !removed.spaceIds.length) {
      perform(data);
      return;
    }
    checking.current = true;
    setStep('checking');
    const result = await options.refetch();
    checking.current = false;
    if (!result.isSuccess) {
      setStep('options-error');
      return;
    }
    const needsHandover =
      result.data.projects.some((item) => removed.projectIds.includes(item.project.projectId)) ||
      result.data.spaces.some((item) => removed.spaceIds.includes(item.space.spaceId));
    if (needsHandover) setStep('handover');
    else {
      setStep(null);
      perform(data);
    }
  }

  function submit(values: UpdateUserRolesRequest) {
    setConfirmationParams({});
    const data = {
      ...values,
      roles: [...new Set([Role.EMPLOYEE, ...values.roles])],
      departmentId: values.departmentId ?? null,
      spaceIds: values.roles.includes(Role.KNOWLEDGE_MANAGER) ? (values.spaceIds ?? []) : [],
    };
    const removedMembershipCount = new Set([
      ...user.ticketResolverProjectIds,
      ...scope(data).projectIds,
    ]).size;
    const removesMembership =
      user.roles.includes(Role.TICKET_RESOLVER) &&
      !data.roles.includes(Role.TICKET_RESOLVER) &&
      removedMembershipCount > 0;
    if (removesMembership) {
      setConfirmationParams({ count: removedMembershipCount });
      setRequested(data);
      setStep('confirm-tr');
    } else void proceed(data);
  }

  function cancelStep() {
    if (submitting.current) return;
    setStep(null);
    setRequested(null);
  }

  return {
    step,
    optionsError: options.error,
    retryOptions: () => {
      if (requested) void proceed(requested);
    },
    confirmationParams: {
      user_name: user.fullName ?? user.email,
      count: user.ticketResolverProjectIds.length,
      ...confirmationParams,
    },
    isPending: change.isPending,
    submit,
    cancelStep,
    handoverScope: requested ? scope(requested) : undefined,
    confirmRemoval: () => {
      if (requested) void proceed({ ...requested, confirmTicketResolverRemoval: true });
    },
    handover: (values: Handovers, reportError: (error: ApiError) => void) => {
      if (requested) perform({ ...requested, ...values }, reportError);
    },
  };
}
