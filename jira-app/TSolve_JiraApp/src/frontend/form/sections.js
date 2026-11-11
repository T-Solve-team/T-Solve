// The five Solution sections of the Solution Form (GBR-33): their API field, their label, and whether they are
// required. Workaround is the only optional one (AC-002.3).
export const SECTIONS = [
  { field: 'symptoms', label: 'Symptoms', required: true },
  { field: 'rootCause', label: 'Root cause', required: true },
  { field: 'resolutionSteps', label: 'Resolution steps', required: true },
  { field: 'workaround', label: 'Workaround', required: false },
  { field: 'scopeOfApplicability', label: 'Scope of applicability', required: true },
];

/** The five sections together may hold this many characters (AC-002.4). */
export const MAX_SOLUTION_LENGTH = 10000;

export const EMPTY_SECTIONS = Object.fromEntries(SECTIONS.map(({ field }) => [field, '']));

/** The sections with every field present, as the API expects them. */
export function normalize(sections) {
  return Object.fromEntries(SECTIONS.map(({ field }) => [field, sections?.[field] ?? '']));
}

/** Characters counted as code points, so an emoji counts once — as the server counts (AC-002.5). */
export function solutionLength(sections) {
  return SECTIONS.reduce((total, { field }) => total + [...(sections[field] ?? '')].length, 0);
}

/**
 * The first problem that stops Finish, as `{field, code, params}`, or null: MSG01 under the first empty required
 * section (AC-002.3), then MSG02 for the whole Solution (AC-002.4).
 */
export function validateSections(sections) {
  const empty = SECTIONS.find(({ field, required }) => required && !(sections[field] ?? '').trim());
  if (empty) {
    return { field: empty.field, code: 'MSG01', params: { field_name: empty.label } };
  }
  if (solutionLength(sections) > MAX_SOLUTION_LENGTH) {
    return { field: null, code: 'MSG02', params: { field_name: 'Solution', max_length: '10,000' } };
  }
  return null;
}
