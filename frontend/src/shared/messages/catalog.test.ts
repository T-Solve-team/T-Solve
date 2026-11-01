import { MESSAGES, type MsgCode } from './catalog';
import report3Raw from './report3-messages.json?raw';

type Report3Message = { type: string; context: string; text: string };
const report3 = JSON.parse(report3Raw) as Record<string, Report3Message>;

/** Spec AC-006.2: Report3 message type → catalog type. */
const TYPE_OF: Record<string, string> = {
  'Red text below the field': 'field',
  'Red text below the upload area': 'field',
  Inline: 'inline',
  'Inline (row)': 'inline',
  'Inline (error row in the preview)': 'inline',
  'Inline (chat answer)': 'inline',
  Toast: 'toast',
  'Confirmation dialog': 'confirm',
  Tooltip: 'tooltip',
};
const JIRA_APP_ONLY = [
  'Inline (tracker panel)',
  'Toast (tracker panel)',
  'Banner (tracker panel)',
  'Tracker comment',
];
/** D2 (no empty Solution) and D3 (the Hand Over popup replaces the confirm dialogs). */
const LEFT_OUT = ['MSG20', 'MSG70', 'MSG58', 'MSG79'];
/** AC-006.3: codes the catalog holds that report3-messages.json does not; System_Messages will number them. */
const NOT_IN_REPORT3 = ['MSG142', 'MSG143'];

/** AC-006.1: a code whose Context starts "Retired" (edge case 20) is not in the catalog. */
const isRetired = ({ context }: Report3Message) => /^Retired\b/.test(context);
/** AC-006.1: the Platform Console codes MSG125-MSG133 and MSG135-MSG140 (their own module). */
const isPlatformConsole = (code: string) => {
  const n = Number(code.slice(3));
  return (n >= 125 && n <= 133) || (n >= 135 && n <= 140);
};

const webMessages = Object.entries(report3).filter(
  ([code, message]) =>
    !JIRA_APP_ONLY.includes(message.type) &&
    !LEFT_OUT.includes(code) &&
    !NOT_IN_REPORT3.includes(code) &&
    !isRetired(message) &&
    !isPlatformConsole(code),
);
const entry = (code: string) => (MESSAGES as Record<string, { type: string; text: string }>)[code];

describe('message catalog', () => {
  it('has the Report3 reference file with all 141 messages', () => {
    expect(Object.keys(report3)).toHaveLength(141);
  });

  it.each(webMessages)('AC-006.1 holds %s with the exact Report3 text', (code, { text }) => {
    expect(entry(code)?.text).toBe(text);
  });

  it('AC-006.1 leaves out the Jira App messages, the Retired codes, the Platform Console codes and the codes of D2 and D3', () => {
    const jiraApp = Object.entries(report3).filter(([, { type }]) => JIRA_APP_ONLY.includes(type));
    expect(jiraApp.map(([code]) => code)).toEqual([
      'MSG64',
      'MSG74',
      'MSG76',
      'MSG77',
      'MSG80',
      'MSG81',
      'MSG82',
      'MSG83',
      'MSG121',
      'MSG124',
    ]);
    const retired = Object.entries(report3).filter(([, message]) => isRetired(message));
    const platform = Object.keys(report3).filter(isPlatformConsole);
    for (const code of [
      ...jiraApp.map(([c]) => c),
      ...LEFT_OUT,
      ...retired.map(([c]) => c),
      ...platform,
    ])
      expect(MESSAGES).not.toHaveProperty(code);
  });

  it('AC-006.1 treats exactly 17 codes as Retired and 15 as Platform Console, and holds 95 web codes', () => {
    expect(Object.values(report3).filter(isRetired)).toHaveLength(17);
    expect(Object.keys(report3).filter(isPlatformConsole)).toHaveLength(15);
    expect(webMessages).toHaveLength(95);
  });

  it('AC-006.8 holds MSG119, MSG120, MSG122, MSG123 and MSG141 as inline and MSG134 as field with the Report3 text', () => {
    const expected = [
      ['MSG119', 'inline'],
      ['MSG120', 'inline'],
      ['MSG122', 'inline'],
      ['MSG123', 'inline'],
      ['MSG141', 'inline'],
      ['MSG134', 'field'],
    ] as const;
    for (const [code, type] of expected) {
      expect(entry(code)?.type, code).toBe(type);
      expect(entry(code)?.text, code).toBe(report3[code]?.text);
    }
  });

  it('AC-006.8 keeps the {google_domain} placeholder of MSG122 and MSG134 for the caller to fill', () => {
    expect(entry('MSG122')?.text).toContain('{google_domain}');
    expect(entry('MSG134')?.text).toContain('{google_domain}');
  });

  it('edge 20 holds no Retired code (MSG61, MSG73)', () => {
    expect(MESSAGES).not.toHaveProperty('MSG61');
    expect(MESSAGES).not.toHaveProperty('MSG73');
  });

  it.each(webMessages)(
    'AC-006.2 gives %s the catalog type of its Report3 type',
    (code, { type }) => {
      expect(entry(code)?.type).toBe(TYPE_OF[type]);
    },
  );

  it('gives a toast the error level when the action failed and the success level otherwise', () => {
    // design.md "Toast level": no Report3 toast is info today; a new info toast is added here.
    const levels = Object.entries(MESSAGES as Record<string, { type: string; level?: string }>)
      .filter(([, { type }]) => type === 'toast')
      .map(([code, { level }]) => [code, level]);
    expect(levels.filter(([, level]) => level === 'error').map(([code]) => code)).toEqual([
      'MSG06',
      'MSG33',
      'MSG69',
    ]);
    expect(levels.filter(([, level]) => level !== 'error' && level !== 'success')).toEqual([]);
  });

  it('AC-006.3 holds MSG_NEW_2, the Hand Over popup intro, until System_Messages numbers it', () => {
    expect(MESSAGES.MSG_NEW_2).toEqual({
      type: 'inline',
      text: '{user_name} is responsible for the {responsibilities} below. Choose who takes over each one before {action}.',
    });
  });

  it('AC-006.3 holds MSG142 and MSG143, the empty-state messages of the identity profile, as inline', () => {
    expect(entry('MSG142')).toEqual({ type: 'inline', text: 'No Spaces found' });
    expect(entry('MSG143')).toEqual({ type: 'inline', text: 'Not a member of any Project.' });
  });

  it('AC-006.7 holds no code other than the Report3 web codes, MSG_NEW_2, MSG142 and MSG143', () => {
    const expected: string[] = [
      ...webMessages.map(([code]) => code),
      'MSG_NEW_2',
      ...NOT_IN_REPORT3,
    ];
    expect(Object.keys(MESSAGES).sort()).toEqual(expected.sort());
    expectTypeOf<'MSG20'>().not.toMatchTypeOf<MsgCode>();
  });
});
