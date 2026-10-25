import agentsRaw from '../../AGENTS.md?raw';
import { ROLE_LABELS, ROLES } from '@/shared/auth/roles';
import { MESSAGES } from '@/shared/messages/catalog';
import { paths } from '@/shared/routing/paths';

const guidelines = import.meta.glob<string>('../../guidelines/*.md', {
  query: '?raw',
  import: 'default',
  eager: true,
});
const docs: [string, string][] = [
  ['AGENTS.md', agentsRaw],
  ...Object.entries(guidelines).map(([file, text]): [string, string] => [
    file.replace('../../', ''),
    text,
  ]),
];
const guideline = (prefix: string) =>
  docs.find(([file]) => file.startsWith(`guidelines/${prefix}`))?.[1] ?? '';

/** Spec AC-010.1: SRS v2 and retired words, as whole words with this capitalisation. */
const RETIRED_WORDS = [
  'Staff',
  'Department Manager',
  'Escalation handler',
  'Migrate',
  'Sensitive',
  'Route to handler',
  'Correct class',
  'Wiki review',
  'Wiki editor',
  'Transfer owner',
];

const quotedRoles = (list: string) => [...list.matchAll(/'([A-Z_]+)'/g)].map((m) => m[1] ?? '');

/** Role values written in code examples: `role: 'X'`, `roles: [...]`, `hasRole('X')`, `hasAnyRole([...])`. */
function roleValues(text: string): string[] {
  return [
    ...[...text.matchAll(/\brole:\s*'([A-Z_]+)'/g)].map((m) => m[1] ?? ''),
    ...[...text.matchAll(/\broles(?::|=\{)\s*\[([^\]]*)\]/g)].flatMap((m) =>
      quotedRoles(m[1] ?? ''),
    ),
    ...[...text.matchAll(/\bhas(?:Any)?Role\(([^)]*)\)/g)].flatMap((m) => quotedRoles(m[1] ?? '')),
  ];
}

describe('frontend guidelines', () => {
  it('AC-010.1 AGENTS.md names the 34 web screens and the five roles', () => {
    expect(agentsRaw).toMatch(/\b34 (web )?screens\b/);
    for (const label of Object.values(ROLE_LABELS)) expect(agentsRaw).toContain(label);
  });

  it('AC-010.1 AGENTS.md and guidelines 01, 02, 08 name 34 web screens, never 35', () => {
    for (const [file, text] of [
      ['AGENTS.md', agentsRaw],
      ['01', guideline('01')],
      ['02', guideline('02')],
      ['08', guideline('08')],
    ] as const) {
      expect(text, file).not.toMatch(/\b35 (web )?screens\b/);
      expect(text, file).toMatch(/\b34 (web )?screens\b/);
    }
    expect(guideline('09')).not.toMatch(/\b35 screens\b/);
    expect(guideline('01')).toMatch(/26 with a path, 8 popups/);
  });

  it('AC-010.1 no doc lists 9.3 Audit Entry Detail', () => {
    for (const [file, text] of docs) expect(text, file).not.toContain('Audit Entry Detail');
  });

  it.each(docs)('AC-010.1 %s uses no retired word', (_file, text) => {
    for (const word of RETIRED_WORDS) expect(text).not.toMatch(new RegExp(`\\b${word}\\b`));
  });

  it('AC-010.2 the glossary defines the Report3 terms and lists the role values', () => {
    const glossary = guideline('03');
    for (const term of [
      'Employee',
      'Ticket Resolver',
      'Knowledge Manager',
      'Space',
      'Wiki document',
      'Folder',
      'Company-wide',
      'Triage class',
      'Knowledge Chatbot',
      'Citation',
    ])
      expect(glossary, term).toMatch(new RegExp(`^\\| ${term}\\b`, 'm'));
    const roleRow = glossary.match(/^\| Role \|.*$/m)?.[0] ?? '';
    expect([...roleRow.matchAll(/`([A-Z_]+)`/g)].map((m) => m[1])).toEqual([...ROLES]);
  });

  it('AC-010.3 the decision log records the role model and the Report3 catalog, and FE-34 is kept by D1', () => {
    const log = guideline('13');
    const index = log.split('\n').filter((line) => /^\| FE-\d+ \|/.test(line));
    expect(index.some((line) => /several roles/i.test(line))).toBe(true);
    expect(index.some((line) => /Report3/.test(line) && /catalog/i.test(line))).toBe(true);
    expect(index.find((line) => line.startsWith('| FE-34 |'))).toMatch(/kept by D1/);
  });

  it.each(docs)('AC-010.4 %s uses only MSG codes of the catalog', (_file, text) => {
    const codes = [...text.matchAll(/\bMSG(?:_NEW_)?\d+\b/g)].map((m) => m[0]);
    expect(codes.filter((code) => !Object.hasOwn(MESSAGES, code))).toEqual([]);
  });

  it.each(docs)('AC-010.4 %s uses only existing paths keys and role values', (_file, text) => {
    // `paths.ts` is the file, not a key.
    const keys = [...text.matchAll(/\bpaths\.(?!ts\b)([a-zA-Z]+)/g)].map((m) => m[1] ?? '');
    expect(keys.filter((key) => !Object.hasOwn(paths, key))).toEqual([]);
    expect(roleValues(text).filter((role) => !(ROLES as readonly string[]).includes(role))).toEqual(
      [],
    );
  });
});
