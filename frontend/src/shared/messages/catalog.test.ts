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

const webMessages = Object.entries(report3).filter(
  ([code, { type }]) => !JIRA_APP_ONLY.includes(type) && !LEFT_OUT.includes(code),
);
const entry = (code: string) => (MESSAGES as Record<string, { type: string; text: string }>)[code];

describe('message catalog', () => {
  it('has the Report3 reference file with all 103 messages', () => {
    expect(Object.keys(report3)).toHaveLength(103);
  });

  it.each(webMessages)('AC-006.1 holds %s with the exact Report3 text', (code, { text }) => {
    expect(entry(code)?.text).toBe(text);
  });

  it('AC-006.1 leaves out the Jira App messages and the codes of decisions D2 and D3', () => {
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
    ]);
    for (const code of [...jiraApp.map(([c]) => c), ...LEFT_OUT])
      expect(MESSAGES).not.toHaveProperty(code);
  });

  it.each(webMessages)(
    'AC-006.2 gives %s the catalog type of its Report3 type',
    (code, { type }) => {
      expect(entry(code)?.type).toBe(TYPE_OF[type]);
    },
  );

  it('AC-006.3 holds MSG_NEW_2, the Hand Over popup intro, until System_Messages numbers it', () => {
    expect(MESSAGES.MSG_NEW_2).toEqual({
      type: 'inline',
      text: '{user_name} is responsible for the {responsibilities} below. Choose who takes over each one before {action}.',
    });
  });

  it('AC-006.7 holds no code other than the Report3 web codes and MSG_NEW_2', () => {
    const expected: string[] = [...webMessages.map(([code]) => code), 'MSG_NEW_2'];
    expect(Object.keys(MESSAGES).sort()).toEqual(expected.sort());
    expectTypeOf<'MSG20'>().not.toMatchTypeOf<MsgCode>();
  });
});
