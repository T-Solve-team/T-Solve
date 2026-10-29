import {
  ProjectRole,
  Role,
  UserAccountStatus,
  type CurrentUser,
  type SpaceHandover,
  type UserAccount,
  type UserDetail,
  type UserHandoverOptions,
  type UserProject,
} from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { getDevUser, type DevUserId } from '@/shared/auth';
import { DEPARTMENTS, seedUsers, SPACES, WORKSPACE_ID } from './data';

const accountId = (n: number) => `10000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const ACTORS: Record<DevUserId, string> = {
  admin: accountId(1),
  'admin-tr': accountId(8),
  employee: accountId(2),
  tr: accountId(4),
  'tr-two-departments': accountId(5),
  pm: accountId(3),
  km: accountId(6),
  'pm-km': accountId(7),
};
type MockProject = UserProject & { memberIds: string[]; jiraProjectKey: string };

function seedProjects(): MockProject[] {
  const department = DEPARTMENTS[0];
  if (!department) return [];
  return [101, 102, 103, 104].map((n) => ({
    projectId: `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`,
    jiraProjectKey: n === 101 ? 'ITSUP' : n === 102 ? 'ITNET' : n === 103 ? 'ITTOOLS' : 'ARCHIVE',
    name:
      n === 101
        ? 'IT Support'
        : n === 102
          ? 'IT Network'
          : n === 103
            ? 'Engineering Tools'
            : 'Archived Support',
    department,
    isActive: n !== 104,
    managerId: accountId(n === 103 ? 9 : 5),
    memberIds: [accountId(n === 103 ? 9 : 5), accountId(4)],
  }));
}

const spaceHistory: (SpaceHandover & { previousUserId: string })[] = [];
export const store = {
  users: seedUsers(),
  projects: seedProjects(),
  changed: new Set<string>(),
  spaceHistory,
};
const INITIAL_USERS = seedUsers();
const INITIAL_PROJECTS = seedProjects();

function sameValues(first: readonly string[], second: readonly string[]): boolean {
  return first.length === second.length && first.every((value) => second.includes(value));
}

export function resetUserState(): void {
  store.users = seedUsers();
  store.projects = seedProjects();
  store.changed.clear();
  store.spaceHistory.length = 0;
}

export function actorAccountId(): string | undefined {
  const actor = getDevUser();
  return actor ? ACTORS[actor] : undefined;
}

export function accountAccessError() {
  const actor = getDevUser();
  if (!actor) return problem(401, 'MSG07');
  const user = store.users.find((item) => item.id === ACTORS[actor]);
  if (!user || user.status === UserAccountStatus.DEACTIVATED) return problem(401, 'MSG07');
  const isAdmin = store.changed.has(user.id)
    ? user.roles.includes(Role.ADMIN)
    : actor === 'admin' || actor === 'admin-tr';
  return isAdmin ? null : problem(403, 'MSG08');
}

export function workspaceUser(id: string) {
  return store.users.find((user) => user.id === id && user.workspaceId === WORKSPACE_ID);
}

export function publicAccount(user: ReturnType<typeof seedUsers>[number]): UserAccount {
  const { workspaceId: _workspaceId, spaceIds: _spaceIds, ...account } = user;
  return account;
}

export function userDetail(id: string): UserDetail | undefined {
  const user = workspaceUser(id);
  if (!user) return undefined;
  return {
    ...publicAccount(user),
    isCurrentUser: id === actorAccountId(),
    projects: store.projects
      .filter((project) => project.memberIds.includes(id) || project.managerId === id)
      .map(({ memberIds: _members, jiraProjectKey: _key, ...project }) => project),
    managedSpaces: SPACES.filter((space) => user.spaceIds.includes(space.spaceId)),
  };
}

export function handoverOptions(id: string): UserHandoverOptions {
  const user = workspaceUser(id);
  const eligible = store.users.filter(
    (candidate) =>
      candidate.workspaceId === WORKSPACE_ID &&
      candidate.id !== id &&
      candidate.status === UserAccountStatus.ACTIVE,
  );
  return {
    projects: store.projects
      .filter((project) => project.isActive && project.managerId === id)
      .map(({ memberIds: _members, jiraProjectKey: _key, ...project }) => ({
        project,
        recipients: eligible
          .filter(
            (candidate) =>
              candidate.roles.includes(Role.PROJECT_MANAGER) &&
              candidate.department?.departmentId === project.department.departmentId,
          )
          .map(publicAccount),
      })),
    spaces: SPACES.filter((space) => user?.spaceIds.includes(space.spaceId)).map((space) => ({
      space,
      recipients: eligible
        .filter((candidate) => candidate.roles.includes(Role.KNOWLEDGE_MANAGER))
        .map(publicAccount),
    })),
  };
}

/** Only changed fixture accounts override /me; legacy ids and unrelated central fixtures stay intact. */
export function resolveMockSession(actor: DevUserId, baseline: CurrentUser): CurrentUser | null {
  const id = ACTORS[actor];
  if (!store.changed.has(id)) return baseline;
  const user = workspaceUser(id);
  if (!user || user.status === UserAccountStatus.DEACTIVATED) return null;
  const original = INITIAL_USERS.find((item) => item.id === id);
  if (!original) return baseline;
  const departmentChanged = user.department?.departmentId !== original.department?.departmentId;
  const related = store.projects.filter((project) => {
    const initial = INITIAL_PROJECTS.find((item) => item.projectId === project.projectId);
    return (
      initial?.managerId !== project.managerId &&
      (project.memberIds.includes(id) || project.managerId === id || initial?.managerId === id)
    );
  });
  return {
    ...baseline,
    roles: sameValues(user.roles, original.roles) ? baseline.roles : user.roles,
    department: departmentChanged ? user.department : baseline.department,
    departments: departmentChanged
      ? user.department
        ? [user.department]
        : []
      : baseline.departments,
    managedSpaces: sameValues(user.spaceIds, original.spaceIds)
      ? baseline.managedSpaces
      : SPACES.filter((space) => user.spaceIds.includes(space.spaceId)),
    projects: [
      ...baseline.projects.filter(
        (project) => !related.some((item) => item.projectId === project.projectId),
      ),
      ...related.map((project) => ({
        projectId: project.projectId,
        jiraProjectKey: project.jiraProjectKey,
        departmentId: project.department.departmentId,
        departmentName: project.department.name,
        roleInProject: project.managerId === id ? ProjectRole.PROJECT_MANAGER : ProjectRole.MEMBER,
      })),
    ],
  };
}
