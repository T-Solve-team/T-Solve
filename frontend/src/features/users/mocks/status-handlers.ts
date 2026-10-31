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
import {
  applyHandover,
  isRecord,
  isProjectHandovers,
  isSpaceHandovers,
  validateHandover,
} from './handover';

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
      !isRecord(body) ||
      (body.status !== UserAccountStatus.ACTIVE && body.status !== UserAccountStatus.DEACTIVATED)
    )
      return problem(400, 'MSG06');
    const projectHandovers: unknown =
      body.projectHandovers === undefined ? [] : body.projectHandovers;
    const spaceHandovers: unknown = body.spaceHandovers === undefined ? [] : body.spaceHandovers;
    if (!isProjectHandovers(projectHandovers) || !isSpaceHandovers(spaceHandovers))
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
      const invalid = validateHandover(handoverOptions(id), projectHandovers, spaceHandovers);
      if (invalid) return invalid;
      applyHandover(id, projectHandovers, spaceHandovers);
    } else if (projectHandovers.length > 0 || spaceHandovers.length > 0)
      return problem(400, 'MSG06');
    target.status = body.status;
    store.changed.add(id);
    return HttpResponse.json(userDetail(id));
  }),
];
