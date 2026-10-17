/**
 * Spec contract — parse, lint, hash, and trace a module's spec.md. Shared by the gate hook,
 * session-context, sdd.ts, and the CLI's upgrade migration.
 *
 * Format (templates/spec.md): stories are headings `### STORY-001: Title`; acceptance criteria are
 * list items under a story, `- AC-001.1: WHEN <trigger>, THE <system> SHALL <response>` (EARS).
 * Open questions stay in the spec as `[NEEDS CLARIFICATION: …]` until the user answers them.
 */
import { join } from "node:path";

export interface Ac {
  id: string;
  story: string;
  text: string;
  line: number;
}
export interface Story {
  id: string;
  line: number;
  acs: Ac[];
}
export interface Spec {
  stories: Story[];
  /** Data rows in the `## Changelog` table; null when the spec has no changelog section. */
  changelogRows: number | null;
  /** Lines as the checks see them: fenced code blanked, inline code and HTML comments removed. */
  prose: string[];
}

const STORY_HEADING = /^(#{2,4})\s+(STORY-\d{3})\b/;
const HEADING = /^(#{1,6})\s/;
const AC_ITEM = /^\s*[-*]\s+(AC-[^\s:]+):?\s*(.*)$/;
const AC_ID = /^AC-(\d{3})\.(\d+)$/;
const AC_MENTION = /\bAC-\d{3}\.\d+\b/g;
const VAGUE =
  /\b(fast|quick(?:ly)?|appropriate(?:ly)?|reasonabl[ey]|user[- ]friendly|intuitive(?:ly)?|robust|efficient(?:ly)?|easy|easily|seamless(?:ly)?|etc|as needed|and\/or)\b/i;

/**
 * Fenced code blanked, HTML comments and inline code removed — markers, placeholders, and
 * acceptance criteria only count in prose (so a template's guidance comment never fails a spec).
 */
function proseLines(lines: string[]): string[] {
  let fenced = false;
  let comment = false;
  return lines.map((line) => {
    if (!comment && /^\s*(```|~~~)/.test(line)) {
      fenced = !fenced;
      return "";
    }
    if (fenced) return "";
    let text = "";
    let rest = line;
    while (rest) {
      const at = rest.indexOf(comment ? "-->" : "<!--");
      if (at === -1) {
        if (!comment) text += rest;
        break;
      }
      if (!comment) text += rest.slice(0, at);
      rest = rest.slice(at + (comment ? 3 : 4));
      comment = !comment;
    }
    return text.replace(/`[^`]*`/g, "");
  });
}

export function parseSpec(text: string): Spec {
  const prose = proseLines(text.replace(/\r\n?/g, "\n").split("\n"));
  const stories: Story[] = [];
  let story: Story | null = null;
  let storyLevel = 0;
  let changelogRows: number | null = null;
  let changelogTableRow = -1; // -1 = not inside the changelog section

  prose.forEach((line, i) => {
    const storyHeading = line.match(STORY_HEADING);
    if (storyHeading) {
      story = { id: storyHeading[2], line: i + 1, acs: [] };
      storyLevel = storyHeading[1].length;
      stories.push(story);
      changelogTableRow = -1;
      return;
    }
    const heading = line.match(HEADING);
    if (heading) {
      if (story && heading[1].length > storyLevel) return; // a sub-section of the story (#### Business Rules…)
      story = null;
      changelogTableRow = /^#{1,6}\s+changelog\b/i.test(line) ? 0 : -1;
      if (changelogTableRow === 0) changelogRows = 0;
      return;
    }
    if (changelogTableRow >= 0 && line.trim().startsWith("|")) {
      changelogTableRow++;
      const cells = line
        .trim()
        .replace(/^\||\|$/g, "")
        .split("|")
        .map((c) => c.trim());
      const separator = cells.every((c) => /^:?-+:?$/.test(c));
      // Row 1 is the header; count data rows that say something.
      if (changelogTableRow > 1 && !separator && cells.some(Boolean)) changelogRows = (changelogRows ?? 0) + 1;
      return;
    }
    const ac = story && line.match(AC_ITEM);
    if (story && ac) story.acs.push({ id: ac[1], story: story.id, text: ac[2].trim(), line: i + 1 });
  });

  return { stories, changelogRows, prose };
}

export function lintSpec(spec: Spec): { errors: string[]; warnings: string[] } {
  const errors: string[] = [];
  const warnings: string[] = [];
  const seen = new Map<string, number>();

  if (spec.stories.length === 0) errors.push("no user stories — add `### STORY-001: <title>` with acceptance criteria");
  for (const story of spec.stories) {
    const n = story.id.slice("STORY-".length);
    if (story.acs.length === 0) {
      errors.push(`L${story.line}: ${story.id} has no acceptance criteria — add "- AC-${n}.1: WHEN …, THE … SHALL …"`);
    }
    for (const ac of story.acs) {
      const id = ac.id.match(AC_ID);
      if (!id) errors.push(`L${ac.line}: "${ac.id}" is not an acceptance-criterion id — use AC-${n}.<n>`);
      else if (id[1] !== n) errors.push(`L${ac.line}: ${ac.id} is under ${story.id} — number it AC-${n}.<n>`);
      if (seen.has(ac.id)) errors.push(`L${ac.line}: duplicate ${ac.id} (first at L${seen.get(ac.id)})`);
      else seen.set(ac.id, ac.line);

      if (/\[NEEDS CLARIFICATION/i.test(ac.text)) continue; // reported with the other markers below
      if (!/\bSHALL\b/i.test(ac.text)) {
        warnings.push(`L${ac.line}: ${ac.id} is not in EARS form — "WHEN <trigger>, THE <system> SHALL <response>"`);
      }
      const vague = ac.text.match(VAGUE);
      if (vague) warnings.push(`L${ac.line}: ${ac.id} uses vague wording "${vague[0]}" — state a measurable outcome`);
    }
    // A triggered happy path needs its failure path: what happens when the trigger goes wrong.
    if (story.acs.some((a) => /^WHEN\b/i.test(a.text)) && !story.acs.some((a) => /^IF\b.*\bTHEN\b/i.test(a.text))) {
      warnings.push(
        `L${story.line}: ${story.id} has a WHEN criterion but no "IF <unwanted condition>, THEN THE <system> SHALL …" — add the failure path`,
      );
    }
  }

  spec.prose.forEach((line, i) => {
    const at = `L${i + 1}`;
    if (/\[NEEDS CLARIFICATION/i.test(line)) {
      errors.push(`${at}: unresolved [NEEDS CLARIFICATION] — ask the user, then write the answer into the spec`);
    }
    const marker = line.match(/\bTBD\b|\bTODO\b(?=\s*[:(-]|\s*$)/);
    if (marker) errors.push(`${at}: unresolved ${marker[0]} — decide it, or mark it [NEEDS CLARIFICATION: <question>]`);
    const placeholder = line.match(/\{[a-z][^{}]*\}/i);
    if (placeholder) errors.push(`${at}: template placeholder ${placeholder[0]} — replace it with real content`);
  });

  if (spec.changelogRows === null) errors.push("no `## Changelog` table — every spec change is logged there");
  else if (spec.changelogRows === 0) errors.push("the Changelog has no rows — add one for the initial spec");
  return { errors, warnings };
}

/** Version of a spec's content: frontmatter excluded; line endings and trailing spaces normalized. */
export function specHash(text: string): string {
  const body = text
    .replace(/\r\n?/g, "\n")
    .replace(/^---\n[\s\S]*?\n---(?:\n|$)/, "")
    .split("\n")
    .map((line) => line.trimEnd())
    .join("\n")
    .trim();
  return new Bun.CryptoHasher("sha256").update(body).digest("hex");
}

export interface TraceTest {
  path: string;
  text: string;
  /** Other modules that may also own this test file, with the criterion ids their specs define. */
  others?: { name: string; acIds: Set<string> }[];
}

/**
 * Which tests mention each acceptance criterion. `stories` narrows the criteria that must be
 * covered (e.g. one task's stories). Ids are numbered per module, so a mention in a test that
 * another module with the same id may also own proves nothing: it is reported as ambiguous and
 * does not count. Ids no owning module defines are reported as stale.
 */
export function trace(spec: Spec, tests: TraceTest[], stories: string[] = []) {
  const all = spec.stories.flatMap((s) => s.acs);
  const known = new Set(all.map((a) => a.id));
  const selected = stories.length ? all.filter((a) => stories.includes(a.story)) : all;
  const covered: Record<string, string[]> = Object.fromEntries(selected.map((a) => [a.id, [] as string[]]));
  const ambiguous: Record<string, string[]> = {};
  const unknown = new Set<string>();

  for (const test of tests) {
    for (const [id] of test.text.matchAll(AC_MENTION)) {
      const rivals = (test.others ?? []).filter((o) => o.acIds.has(id)).map((o) => o.name);
      if (!known.has(id)) {
        if (!rivals.length) unknown.add(id);
        continue;
      }
      if (!covered[id]) continue; // outside the selected stories
      if (rivals.length) ambiguous[id] ??= [];
      const list = rivals.length ? ambiguous[id] : covered[id];
      const entry = rivals.length ? `${test.path} (also ${rivals.join(", ")})` : test.path;
      if (!list.includes(entry)) list.push(entry);
    }
  }
  const uncovered = Object.keys(covered).filter((id) => covered[id].length === 0);
  return { covered, uncovered, ambiguous, unknown: [...unknown].sort() };
}

/** Criterion id → text: what the user saw when they approved the spec. */
export function acMap(spec: Spec): Record<string, string> {
  return Object.fromEntries(spec.stories.flatMap((s) => s.acs.map((a) => [a.id, a.text])));
}

/** Criteria added, reworded, or removed between two versions of a spec. */
export function acChanges(before: Record<string, string>, after: Record<string, string>) {
  return {
    added: Object.keys(after).filter((id) => !(id in before)),
    changed: Object.keys(after).filter((id) => id in before && before[id] !== after[id]),
    removed: Object.keys(before).filter((id) => !(id in after)),
  };
}

/**
 * The plan's tasks against the spec: a task naming a story the spec does not have is an error,
 * a story no task covers is a warning (it may be planned in a later iteration).
 */
export function lintTasks(spec: Spec, tasksFiles: string[]): { errors: string[]; warnings: string[] } {
  const stories = new Set(spec.stories.map((s) => s.id));
  const named = new Set(tasksFiles.flatMap((text) => [...text.matchAll(/\bSTORY-\d{3}\b/g)].map((m) => m[0])));
  return {
    errors: [...named].filter((id) => !stories.has(id)).map((id) => `tasks name ${id}, which the spec does not have`),
    warnings: [...stories].filter((id) => !named.has(id)).map((id) => `${id} has no task in tasks.md`),
  };
}

// ─── workspace state ─────────────────────────────────────────────────────

export interface Approval {
  specHash: string;
  approvedAt: string;
  by: string;
  /** Criteria as approved; absent in approvals recorded before 1.1.0. */
  acs?: Record<string, string>;
}
export type SpecState = "approved" | "not-approved" | "changed" | "no-spec";

export const specPath = (workspace: string, module: string) =>
  join(workspace, "artifacts", "03-modules", module, "spec.md");

/** approvals.json — the spec version the user approved, per module. Throws if the file is not valid JSON. */
export async function readApprovals(workspace: string): Promise<Record<string, Approval>> {
  const file = Bun.file(join(workspace, "approvals.json"));
  return (await file.exists()) ? await file.json() : {};
}

export async function specState(
  workspace: string,
  module: string,
  approvals: Record<string, Approval>,
): Promise<SpecState> {
  const file = Bun.file(specPath(workspace, module));
  if (!(await file.exists())) return "no-spec";
  const approval = approvals[module];
  if (!approval) return "not-approved";
  return approval.specHash === specHash(await file.text()) ? "approved" : "changed";
}
