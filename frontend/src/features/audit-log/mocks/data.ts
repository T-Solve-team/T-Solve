import { AuditAction, type AuditEntry } from '@/shared/api';

export const AUDIT_WORKSPACE = '00000000-0000-4000-8000-0000000000c1';
const uuid = (n: number) => `53000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const actions = Object.values(AuditAction);
export type MockAuditEntry = AuditEntry & { workspaceId: string };

/** Synthetic, deterministic fixtures; hidden tenant keys never leave the mock handler. */
export const AUDIT_ENTRIES: MockAuditEntry[] = Array.from({ length: 34 }, (_, index) => ({
  id: uuid(index + 1),
  workspaceId: AUDIT_WORKSPACE,
  createdAt: new Date(
    Date.parse('2026-10-30T09:30:00Z') - Math.floor(index / 2) * 86_400_000,
  ).toISOString(),
  actor: index % 2 === 0 ? 'duong.pham@company.example' : 'lan.tran@company.example',
  action: actions[index % actions.length] ?? AuditAction.APPROVE,
  resourceType: index % 3 === 0 ? 'USER' : 'TICKET',
  resourceId: uuid(100 + index),
  summary:
    index % 7 === 0
      ? null
      : `Recorded action for ${index % 3 === 0 ? 'user account' : 'ticket'} ${index + 1}`,
}));

AUDIT_ENTRIES.push(
  {
    id: uuid(35),
    workspaceId: AUDIT_WORKSPACE,
    createdAt: '2026-10-01T16:59:59.999Z',
    actor: 'former.user@company.example',
    action: AuditAction.IMPORT,
    resourceType: 'TICKET',
    resourceId: uuid(135),
    summary: 'Imported resolved tickets',
  },
  {
    id: uuid(36),
    workspaceId: '00000000-0000-4000-8000-0000000000c2',
    createdAt: '2026-10-30T09:30:00Z',
    actor: 'duong.pham@company.example',
    action: AuditAction.APPROVE,
    resourceType: 'TICKET',
    resourceId: uuid(136),
    summary: 'Foreign Workspace event',
  },
);
