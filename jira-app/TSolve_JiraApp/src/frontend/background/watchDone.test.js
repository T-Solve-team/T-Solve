import { adfText, DONE, isOwnChange, issueText, movedIntoDone, readIssue } from './watchDone';

describe('watchDone', () => {
  it('AC-001.1 reacts only to a change the viewer made', () => {
    const payload = { changes: [{ changeType: 'updated', atlassianId: 'me' }] };

    expect(isOwnChange(payload, 'me')).toBe(true);
    expect(isOwnChange(payload, 'someone-else')).toBe(false);
    expect(isOwnChange({}, 'me')).toBe(false);
    expect(isOwnChange(payload, undefined)).toBe(false);
  });

  it.each([
    ['indeterminate', DONE, true],
    ['new', DONE, true],
    [DONE, DONE, false],
    ['indeterminate', 'indeterminate', false],
    [undefined, DONE, false],
  ])('AC-001.1 AC-001.5 opens the form from %s to %s: %s', (previous, current, opens) => {
    expect(movedIntoDone(previous, current)).toBe(opens);
  });

  it('AC-002.2 reads the status category, summary and description text as the viewer', async () => {
    const requestJira = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        fields: {
          status: { statusCategory: { key: 'done' } },
          summary: 'VPN drops',
          description: {
            type: 'doc',
            version: 1,
            content: [
              { type: 'paragraph', content: [{ type: 'text', text: 'After sleep' }] },
              { type: 'paragraph', content: [{ type: 'text', text: 'Every hour' }] },
            ],
          },
        },
      }),
    });

    const issue = await readIssue(requestJira, 'ITSUP-12');

    expect(requestJira).toHaveBeenCalledWith('/rest/api/3/issue/ITSUP-12?fields=status,summary,description');
    expect(issue).toEqual({ category: 'done', summary: 'VPN drops', description: 'After sleep\nEvery hour' });
  });

  it('AC-001.1 fails when the issue cannot be read', async () => {
    const requestJira = jest.fn().mockResolvedValue({ ok: false, status: 403 });

    await expect(readIssue(requestJira, 'ITSUP-12')).rejects.toThrow('403');
  });

  it('turns list items and line breaks into lines', () => {
    const doc = {
      type: 'doc',
      content: [
        {
          type: 'bulletList',
          content: [
            { type: 'listItem', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'one' }] }] },
          ],
        },
        { type: 'paragraph', content: [{ type: 'text', text: 'a' }, { type: 'hardBreak' }, { type: 'text', text: 'b' }] },
      ],
    };

    expect(issueText(doc)).toBe('one\n\na\nb');
    expect(adfText(null)).toBe('');
  });
});
