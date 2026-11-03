import { DEPARTMENTS } from './data';
import { store } from './state';

// Only the mock boundary bridges the legacy mutation/detail proposal and the SQL list contract.
// The store appends/retains accounts, so fixture positions remain stable across edits and handovers.
export function listUserId(id: string): string {
  if (/^[1-9][0-9]*$/.test(id)) return id;
  const index = store.users.findIndex((user) => user.id === id);
  if (index < 0) throw new Error('Unknown mock account');
  return String(index + 1);
}

export function listDepartmentId(id: string): string {
  if (/^[1-9][0-9]*$/.test(id)) return id;
  const index = DEPARTMENTS.findIndex((department) => department.departmentId === id);
  if (index < 0) throw new Error('Unknown mock Department');
  return String(index + 1);
}

export function detailAccountId(id: string): string {
  return store.users.find((user) => user.id === id || listUserId(user.id) === id)?.id ?? id;
}
