import { delay, http, HttpResponse } from 'msw';
import { Role, UserAccountStatus } from '@/shared/api';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import { isRole } from '@/shared/auth';
import type { MsgCode, MsgParams } from '@/shared/messages';
import { DEPARTMENTS, SPACES, WORKSPACE_ID } from './data';
import {
  applyHandover,
  isProjectHandovers,
  isRecord,
  isSpaceHandovers,
  validateHandover,
} from './handover';
import {
  accountAccessError,
  actorAccountId,
  handoverOptions,
  store,
  ticketResolverProjectIds,
  userDetail,
  workspaceUser,
} from './state';

function fieldError(field: string, code: MsgCode, params?: MsgParams, status = 400) {
  return problem(status, code, { params, fieldErrors: [{ field, code, params }] });
}

function validSpaces(value: unknown): value is string[] {
  return (
    Array.isArray(value) &&
    value.every(
      (id: unknown) => typeof id === 'string' && SPACES.some((space) => space.spaceId === id),
    )
  );
}

export const rolesHandler = http.put('*/api/v1/users/:userId', async ({ params, request }) => {
  await delay(MOCK_DELAY_MS);
  const denied = accountAccessError();
  if (denied) return denied;
  const id = String(params.userId);
  const target = workspaceUser(id);
  if (!target) return problem(404, 'MSG04');
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return problem(400, 'MSG06');
  }
  if (!isRecord(body)) return problem(400, 'MSG06');
  if (
    !Array.isArray(body.roles) ||
    !body.roles.every((role: unknown) => typeof role === 'string' && isRole(role))
  )
    return fieldError('roles', 'MSG01', { field_name: 'Roles' });
  const roles = [...new Set([Role.EMPLOYEE, ...body.roles.filter(isRole)])];
  const department = DEPARTMENTS.find((item) => item.departmentId === body.departmentId) ?? null;
  if (
    (body.departmentId !== null && !department) ||
    (roles.includes(Role.PROJECT_MANAGER) && !department)
  )
    return fieldError('departmentId', 'MSG01', { field_name: 'Department' });
  const spaceIds: unknown = body.spaceIds;
  if (
    !validSpaces(spaceIds) ||
    new Set(spaceIds).size !== spaceIds.length ||
    (roles.includes(Role.KNOWLEDGE_MANAGER) ? spaceIds.length === 0 : spaceIds.length > 0)
  )
    return fieldError('spaceIds', 'MSG01', { field_name: 'Spaces' });
  const selectedSpaces: string[] = spaceIds;
  const occupied = SPACES.find(
    (space) =>
      selectedSpaces.includes(space.spaceId) &&
      store.users.some(
        (user) =>
          user.workspaceId === WORKSPACE_ID &&
          user.id !== id &&
          user.spaceIds.includes(space.spaceId),
      ),
  );
  if (occupied) return fieldError('spaceIds', 'MSG117', { space_name: occupied.name }, 409);
  if (target.roles.includes(Role.ADMIN) && !roles.includes(Role.ADMIN)) {
    if (
      target.status === UserAccountStatus.ACTIVE &&
      store.users.filter(
        (user) =>
          user.workspaceId === WORKSPACE_ID &&
          user.status === UserAccountStatus.ACTIVE &&
          user.roles.includes(Role.ADMIN),
      ).length === 1
    )
      return problem(409, 'MSG56');
    if (id === actorAccountId()) return problem(409, 'MSG113');
  }
  if (
    body.confirmTicketResolverRemoval !== undefined &&
    typeof body.confirmTicketResolverRemoval !== 'boolean'
  )
    return problem(400, 'MSG06');
  const projectHandovers: unknown =
    body.projectHandovers === undefined ? [] : body.projectHandovers;
  const spaceHandovers: unknown = body.spaceHandovers === undefined ? [] : body.spaceHandovers;
  if (!isProjectHandovers(projectHandovers) || !isSpaceHandovers(spaceHandovers))
    return problem(400, 'MSG06');
  const options = handoverOptions(id);
  const invalid = validateHandover(
    {
      projects:
        !roles.includes(Role.PROJECT_MANAGER) ||
        department?.departmentId !== target.department?.departmentId
          ? options.projects
          : [],
      spaces: options.spaces.filter((item) => !selectedSpaces.includes(item.space.spaceId)),
    },
    projectHandovers,
    spaceHandovers,
  );
  if (invalid) return invalid;
  const removedProjectIds = new Set(
    roles.includes(Role.TICKET_RESOLVER)
      ? []
      : [...ticketResolverProjectIds(id), ...projectHandovers.map((choice) => choice.projectId)],
  );
  const removedMembershipCount = target.roles.includes(Role.TICKET_RESOLVER)
    ? store.projects.filter(
        (project) => removedProjectIds.has(project.projectId) && project.memberIds.includes(id),
      ).length
    : 0;
  if (removedMembershipCount > 0 && body.confirmTicketResolverRemoval !== true)
    return problem(409, 'MSG78', {
      params: { user_name: target.fullName ?? target.email, count: removedMembershipCount },
    });
  // Validate all changes above before committing responsibilities, memberships and assignments.
  applyHandover(id, projectHandovers, spaceHandovers);
  for (const project of store.projects.filter((item) => removedProjectIds.has(item.projectId))) {
    project.memberIds = project.memberIds.filter((memberId) => memberId !== id);
    if (!project.formerMemberIds.includes(id)) project.formerMemberIds.push(id);
  }
  target.roles = roles;
  target.department = department;
  target.spaceIds = selectedSpaces;
  store.changed.add(id);
  store.assignmentChanges.add(id);
  return HttpResponse.json(userDetail(id));
});
