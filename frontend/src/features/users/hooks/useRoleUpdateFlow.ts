import { useRef, useState } from 'react';
import {
  Role,
  type ApiError,
  type UpdateUserRolesRequest,
  type UpdateUserStatusRequest,
  type UserDetail,
} from '@/shared/api';
import { useChangeUserRoles } from './useUserMutations';

type Handovers = Pick<UpdateUserStatusRequest, 'projectHandovers' | 'spaceHandovers'>;

export function useRoleUpdateFlow(user: UserDetail, onError: (error: ApiError) => void) {
  const change = useChangeUserRoles();
  const [step, setStep] = useState<'confirm-tr' | 'handover' | null>(null);
  const [requested, setRequested] = useState<UpdateUserRolesRequest | null>(null);
  const submitting = useRef(false);

  function scope(data: UpdateUserRolesRequest) {
    const removesManagement =
      !data.roles.includes(Role.PROJECT_MANAGER) ||
      data.departmentId !== user.department?.departmentId;
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

  function perform(data: UpdateUserRolesRequest, reportError = onError) {
    if (submitting.current) return;
    submitting.current = true;
    change.mutate(
      { userId: user.id, data },
      {
        onSuccess: () => {
          setStep(null);
          setRequested(null);
        },
        onError: reportError,
        onSettled: () => {
          submitting.current = false;
        },
      },
    );
  }

  function proceed(data: UpdateUserRolesRequest) {
    const removed = scope(data);
    setRequested(data);
    if (removed.projectIds.length || removed.spaceIds.length) setStep('handover');
    else perform(data);
  }

  function submit(values: UpdateUserRolesRequest) {
    const data = {
      ...values,
      roles: [...new Set([Role.EMPLOYEE, ...values.roles])],
      departmentId: values.departmentId ?? null,
      spaceIds: values.roles.includes(Role.KNOWLEDGE_MANAGER) ? (values.spaceIds ?? []) : [],
    };
    const removesMembership =
      user.roles.includes(Role.TICKET_RESOLVER) &&
      !data.roles.includes(Role.TICKET_RESOLVER) &&
      user.ticketResolverProjectIds.length > 0;
    if (removesMembership) {
      setRequested(data);
      setStep('confirm-tr');
    } else proceed(data);
  }

  function cancelStep() {
    if (submitting.current) return;
    setStep(null);
    setRequested(null);
  }

  return {
    step,
    isPending: change.isPending,
    submit,
    cancelStep,
    handoverScope: requested ? scope(requested) : undefined,
    confirmRemoval: () => {
      if (requested) proceed({ ...requested, confirmTicketResolverRemoval: true });
    },
    handover: (values: Handovers, reportError: (error: ApiError) => void) => {
      if (requested) perform({ ...requested, ...values }, reportError);
    },
  };
}
