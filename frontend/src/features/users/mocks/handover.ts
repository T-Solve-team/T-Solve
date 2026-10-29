import type { ProjectHandover, SpaceHandover, UserHandoverOptions } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { store, workspaceUser } from './state';

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function assignments(value: unknown, idKey: string): boolean {
  return (
    Array.isArray(value) &&
    value.every(
      (item: unknown) =>
        isRecord(item) && typeof item[idKey] === 'string' && typeof item.recipientId === 'string',
    )
  );
}

export function isProjectHandovers(value: unknown): value is ProjectHandover[] {
  return assignments(value, 'projectId');
}

export function isSpaceHandovers(value: unknown): value is SpaceHandover[] {
  return assignments(value, 'spaceId');
}

export function validateHandover(
  options: UserHandoverOptions,
  projects: ProjectHandover[],
  spaces: SpaceHandover[],
) {
  const unavailable = options.projects.find((item) => item.recipients.length === 0);
  if (unavailable)
    return problem(409, 'MSG59', {
      params: { department_name: unavailable.project.department.name },
    });
  if (options.spaces.some((item) => item.recipients.length === 0)) return problem(409, 'MSG118');
  if (
    projects.length !== options.projects.length ||
    spaces.length !== options.spaces.length ||
    new Set(projects.map((item) => item.projectId)).size !== projects.length ||
    new Set(spaces.map((item) => item.spaceId)).size !== spaces.length ||
    options.projects.some(
      (item) =>
        !projects.some(
          (choice) =>
            choice.projectId === item.project.projectId &&
            item.recipients.some((candidate) => candidate.id === choice.recipientId),
        ),
    ) ||
    options.spaces.some(
      (item) =>
        !spaces.some(
          (choice) =>
            choice.spaceId === item.space.spaceId &&
            item.recipients.some((candidate) => candidate.id === choice.recipientId),
        ),
    )
  )
    return problem(400, 'MSG01', { params: { field_name: 'Hand Over Responsibilities' } });
  return null;
}

/** Call only after validating the whole operation; no await between validation and writes. */
export function applyHandover(
  id: string,
  projects: ProjectHandover[],
  spaces: SpaceHandover[],
): void {
  const target = workspaceUser(id);
  if (!target) return;
  for (const choice of projects) {
    const project = store.projects.find((item) => item.projectId === choice.projectId);
    if (project) {
      project.managerId = choice.recipientId;
      if (!project.memberIds.includes(choice.recipientId))
        project.memberIds.push(choice.recipientId);
      store.changed.add(choice.recipientId);
    }
  }
  for (const choice of spaces) {
    const recipient = workspaceUser(choice.recipientId);
    if (recipient) {
      recipient.spaceIds.push(choice.spaceId);
      store.spaceHistory.push({
        spaceId: choice.spaceId,
        previousUserId: id,
        recipientId: recipient.id,
      });
      store.changed.add(recipient.id);
    }
  }
  target.spaceIds = target.spaceIds.filter(
    (spaceId) => !spaces.some((choice) => choice.spaceId === spaceId),
  );
}
