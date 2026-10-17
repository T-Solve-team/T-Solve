/**
 * Module and feature state machine for progress.json. `sdd.ts status` and `sdd.ts feature` apply
 * it, so a status changes only along an allowed edge instead of by hand-editing JSON.
 */

export const MODULE_STATES = ["decomposed", "planned", "implementing", "evaluating", "completed", "blocked"];
export const FEATURE_STATES = ["pending", "in-progress", "evaluating", "passing", "fixing", "blocked"];

const MODULE_NEXT: Record<string, string[]> = {
  decomposed: ["planned"],
  planned: ["implementing"],
  implementing: ["evaluating", "blocked"],
  evaluating: ["implementing", "completed", "blocked"],
  blocked: ["implementing", "planned"],
  completed: ["planned"], // a change request reopens it
};

const FEATURE_NEXT: Record<string, string[]> = {
  pending: ["in-progress"],
  "in-progress": ["evaluating", "blocked"],
  evaluating: ["passing", "fixing", "blocked"],
  fixing: ["evaluating", "blocked"],
  blocked: ["in-progress"],
  passing: [],
};

const PHASE: Record<string, string> = {
  decomposed: "decompose",
  planned: "plan",
  implementing: "implement",
  evaluating: "evaluate",
  completed: "evaluate",
  blocked: "blocked",
};

export type ModuleEntry = {
  status?: string;
  currentPhase?: string;
  features?: Record<string, string>;
  /** The branch the module uses in every repo it touches: `feat/<module>`. */
  branch?: string;
  /** The manifest repo keys the module touches (module.md `repos:`, else `default`). */
  repos?: string[];
  /** Last recorded commit per repo key (progress.json version 2). */
  lastCommit?: Record<string, string>;
  lastUpdated?: string;
  [key: string]: unknown;
};
export type Progress = { modules?: Record<string, ModuleEntry>; [key: string]: unknown };

/** A module's status; workspaces from before `status` existed only had `currentPhase`. */
export const statusOf = (m: ModuleEntry | undefined) => m?.status ?? m?.currentPhase ?? "";

/** The result of a change: what moved (for the event log), or why it was refused. */
export type Change = { from: string; to: string } | { error: string };

const edges = (next: Record<string, string[]>, from: string) =>
  next[from]?.length ? next[from].join(", ") : "nothing — it is final";

/**
 * Move a module to `to` in place. A module enters progress.json as `decomposed`. Preconditions
 * that need the disk — entering `planned` requires the user's approval of the current spec — are
 * the caller's to check.
 */
export function setModuleStatus(progress: Progress, module: string, to: string, now: string): Change {
  if (!MODULE_STATES.includes(to))
    return { error: `unknown module status '${to}' — one of: ${MODULE_STATES.join(", ")}` };
  progress.modules ??= {};
  const entry = progress.modules[module];
  if (!entry) {
    if (to !== "decomposed") {
      return { error: `module '${module}' is not in progress.json — the decompose phase adds it as 'decomposed'` };
    }
    progress.modules[module] = { status: to, currentPhase: PHASE[to], features: {}, lastUpdated: now };
    return { from: "", to };
  }
  const from = statusOf(entry);
  if (from === to) return { from, to };
  if (!MODULE_NEXT[from]?.includes(to)) {
    return {
      error: `module '${module}': '${from}' → '${to}' is not allowed; from '${from}' it can go to ${edges(MODULE_NEXT, from)}`,
    };
  }
  if (to === "completed") {
    const features = Object.values(entry.features ?? {});
    const passing = features.filter((f) => f === "passing").length;
    if (!features.length || passing < features.length) {
      return { error: `module '${module}' cannot be completed: ${passing}/${features.length} features are passing` };
    }
  }
  Object.assign(entry, { status: to, currentPhase: PHASE[to], lastUpdated: now });
  return { from, to };
}

/** Move a feature to `to` in place. A feature enters its module as `pending`. */
export function setFeatureStatus(progress: Progress, module: string, feature: string, to: string, now: string): Change {
  if (!FEATURE_STATES.includes(to))
    return { error: `unknown feature status '${to}' — one of: ${FEATURE_STATES.join(", ")}` };
  const entry = progress.modules?.[module];
  if (!entry) return { error: `module '${module}' is not in progress.json` };
  entry.features ??= {};
  const from = entry.features[feature];
  if (from === undefined && to !== "pending") {
    return { error: `feature '${feature}' is not in module '${module}' — add it as 'pending' first` };
  }
  if (from !== undefined && from !== to && !FEATURE_NEXT[from]?.includes(to)) {
    return {
      error: `feature '${feature}': '${from}' → '${to}' is not allowed; from '${from}' it can go to ${edges(FEATURE_NEXT, from)}`,
    };
  }
  entry.features[feature] = to;
  entry.lastUpdated = now;
  return { from: from ?? "", to };
}
