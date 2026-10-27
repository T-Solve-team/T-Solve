import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import { Role, UserAccountStatus } from '@/shared/api';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import {
  accountAccessError,
  actorAccountId,
  handoverOptions,
  store,
  userDetail,
  workspaceUser,
} from './state';
import { WORKSPACE_ID } from './data';

function record(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function assignments(value: unknown, idKey: string): value is Record<string, string>[] {
  return (
    Array.isArray(value) &&
    value.every(
      (item: unknown) =>
        record(item) && typeof item[idKey] === 'string' && typeof item.recipientId === 'string',
    )
  );
}

export const statusHandlers: HttpHandler[] = [
  http.get('*/api/v1/users/:userId/handover-options', async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const denied = accountAccessError();
    if (denied) return denied;
    const id = String(params.userId);
    return workspaceUser(id) ? HttpResponse.json(handoverOptions(id)) : problem(404, 'MSG04');
  }),
  http.get('*/api/v1/users/:userId', async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const denied = accountAccessError();
    if (denied) return denied;
    const detail = userDetail(String(params.userId));
    return detail ? HttpResponse.json(detail) : problem(404, 'MSG04');
  }),
  http.put('*/api/v1/users/:userId/status', async ({ params, request }) => {
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
    if (
      !record(body) ||
      (body.status !== UserAccountStatus.ACTIVE && body.status !== UserAccountStatus.DEACTIVATED)
    )
      return problem(400, 'MSG06');
    const projectHandovers: unknown =
      body.projectHandovers === undefined ? [] : body.projectHandovers;
    const spaceHandovers: unknown = body.spaceHandovers === undefined ? [] : body.spaceHandovers;
    if (!assignments(projectHandovers, 'projectId') || !assignments(spaceHandovers, 'spaceId'))
      return problem(400, 'MSG06');
    if (body.status === UserAccountStatus.DEACTIVATED) {
      if (
        target.status === UserAccountStatus.ACTIVE &&
        target.roles.includes(Role.ADMIN) &&
        store.users.filter(
          (user) =>
            user.workspaceId === WORKSPACE_ID &&
            user.status === UserAccountStatus.ACTIVE &&
            user.roles.includes(Role.ADMIN),
        ).length === 1
      )
        return problem(409, 'MSG56');
      if (id === actorAccountId()) return problem(409, 'MSG112');
      const options = handoverOptions(id);
      const unavailableProject = options.projects.find((item) => item.recipients.length === 0);
      if (unavailableProject)
        return problem(409, 'MSG59', {
          params: { department_name: unavailableProject.project.department.name },
        });
      if (options.spaces.some((item) => item.recipients.length === 0))
        return problem(409, 'MSG118');
      if (
        projectHandovers.length !== options.projects.length ||
        spaceHandovers.length !== options.spaces.length ||
        new Set(projectHandovers.map((item) => item.projectId)).size !== projectHandovers.length ||
        new Set(spaceHandovers.map((item) => item.spaceId)).size !== spaceHandovers.length ||
        options.projects.some(
          (item) =>
            !projectHandovers.some(
              (choice) =>
                choice.projectId === item.project.projectId &&
                item.recipients.some((candidate) => candidate.id === choice.recipientId),
            ),
        ) ||
        options.spaces.some(
          (item) =>
            !spaceHandovers.some(
              (choice) =>
                choice.spaceId === item.space.spaceId &&
                item.recipients.some((candidate) => candidate.id === choice.recipientId),
            ),
        )
      )
        return problem(400, 'MSG01', { params: { field_name: 'Hand Over Responsibilities' } });
      // Everything is validated above. No await occurs between validation and these writes.
      for (const choice of projectHandovers) {
        const project = store.projects.find((item) => item.projectId === choice.projectId);
        if (project && choice.recipientId) {
          project.managerId = choice.recipientId;
          if (!project.memberIds.includes(choice.recipientId))
            project.memberIds.push(choice.recipientId);
          store.changed.add(choice.recipientId);
        }
      }
      for (const choice of spaceHandovers) {
        const recipient = workspaceUser(choice.recipientId ?? '');
        if (recipient && choice.spaceId) {
          recipient.spaceIds.push(choice.spaceId);
          store.spaceHistory.push({
            spaceId: choice.spaceId,
            previousUserId: id,
            recipientId: recipient.id,
          });
          store.changed.add(recipient.id);
        }
      }
      target.spaceIds = [];
    } else if (projectHandovers.length > 0 || spaceHandovers.length > 0)
      return problem(400, 'MSG06');
    target.status = body.status;
    store.changed.add(id);
    return HttpResponse.json(userDetail(id));
  }),
];
