import type { HttpHandler } from 'msw';
import * as auditLog from '@/features/audit-log/mocks/handlers';
import * as auth from '@/features/auth/mocks/handlers';
import * as chatbot from '@/features/chatbot/mocks/handlers';
import * as feedback from '@/features/feedback/mocks/handlers';
import * as jiraIntegration from '@/features/jira-integration/mocks/handlers';
import * as knowledgeDashboard from '@/features/knowledge-dashboard/mocks/handlers';
import * as myProfile from '@/features/my-profile/mocks/handlers';
import * as review from '@/features/review/mocks/handlers';
import * as search from '@/features/search/mocks/handlers';
import * as ticketImport from '@/features/ticket-import/mocks/handlers';
import * as tickets from '@/features/tickets/mocks/handlers';
import * as triage from '@/features/triage/mocks/handlers';
import * as users from '@/features/users/mocks/handlers';
import * as wiki from '@/features/wiki/mocks/handlers';
import * as workspace from '@/features/workspace/mocks/handlers';
import * as generated from '@/shared/api/generated/index.msw';
import { sessionHandlers } from './session';

/**
 * Every mock handler, in priority order (MSW uses the first match). Pre-filled once for all 15 features
 * (guideline 02): a feature adds handlers in its own mocks/handlers.ts, never here.
 * 1. session (/me, logout) — FE Lead
 * 2. hand-written feature handlers — realistic data and business errors
 * 3. orval's generated handlers (faker data) — every other endpoint in the spec, picked up automatically
 */
const generatedHandlers: HttpHandler[] = Object.values(generated).flatMap((tagHandlers) =>
  tagHandlers(),
);

/** Every feature's mocks, in the order their handlers are tried. */
export const FEATURE_MOCKS = [
  auth,
  myProfile,
  users,
  workspace,
  jiraIntegration,
  ticketImport,
  tickets,
  search,
  review,
  feedback,
  knowledgeDashboard,
  auditLog,
  triage,
  wiki,
  chatbot,
];

export const handlers: HttpHandler[] = [
  ...sessionHandlers,
  ...FEATURE_MOCKS.flatMap((feature) => feature.handlers),
  ...generatedHandlers,
];

/** Restores every feature's mock data (tickets decided in one test are back for the next). */
export function resetMockData(): void {
  for (const feature of FEATURE_MOCKS) feature.resetMockData();
}
