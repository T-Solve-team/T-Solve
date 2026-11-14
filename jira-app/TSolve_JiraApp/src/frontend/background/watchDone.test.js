import {
  adfText,
  DONE,
  isOwnChange,
  issueText,
  movedIntoDone,
  readIssue,
  recentlyMovedIntoDone,
  RECENT_MS,
} from './watchDone';

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

  it('reads when the status category changed and who changed the status last, newest first (BUG-214)', async () => {
    const requestJira = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        fields: {
          status: { statusCategory: { key: 'done' } },
          summary: 'VPN',
          description: null,
          statuscategorychangedate: '2026-11-14T12:00:00.000+0700',
        },
        changelog: {
          histories: [
            { author: { accountId: 'other' }, created: '2026-11-14T12:00:05.000+0700', items: [{ field: 'labels' }] },
            { author: { accountId: 'me' }, created: '2026-11-14T12:00:00.000+0700', items: [{ field: 'status' }] },
            { author: { accountId: 'other' }, created: '2026-11-14T11:00:00.000+0700', items: [{ field: 'status' }] },
          ],
        },
      }),
    });

    const issue = await readIssue(requestJira, 'ITSUP-12', { changelog: true });

    expect(requestJira).toHaveBeenCalledWith(
      '/rest/api/3/issue/ITSUP-12?fields=status,summary,description,statuscategorychangedate&expand=changelog',
    );
    expect(issue.statusChangedAt).toBe(Date.parse('2026-11-14T05:00:00.000Z'));
    expect(issue.lastStatusChange).toEqual({ accountId: 'me', at: Date.parse('2026-11-14T05:00:00.000Z') });
  });

  describe('recentlyMovedIntoDone (BUG-214: Jira reloads the script after the move into Done)', () => {
    const NOW = Date.parse('2026-11-14T05:00:10.000Z');
    const moved = (overrides = {}) => ({
      category: DONE,
      statusChangedAt: NOW - 10_000,
      lastStatusChange: { accountId: 'me', at: NOW - 10_000 },
      ...overrides,
    });

    it('opens for the viewer who just moved the issue into Done', () => {
      expect(recentlyMovedIntoDone(moved(), 'me', NOW)).toBe(true);
    });

    it('AC-001.5 does not open for an issue that was Done before the page loaded', () => {
      const old = NOW - RECENT_MS - 1;
      expect(recentlyMovedIntoDone(moved({ statusChangedAt: old, lastStatusChange: { accountId: 'me', at: old } }), 'me', NOW)).toBe(false);
    });

    it('does not open when someone else moved the issue', () => {
      expect(recentlyMovedIntoDone(moved({ lastStatusChange: { accountId: 'other', at: NOW - 10_000 } }), 'me', NOW)).toBe(false);
    });

    it('does not open while the issue is not Done, or without the change data', () => {
      expect(recentlyMovedIntoDone(moved({ category: 'indeterminate' }), 'me', NOW)).toBe(false);
      expect(recentlyMovedIntoDone(moved({ statusChangedAt: undefined }), 'me', NOW)).toBe(false);
      expect(recentlyMovedIntoDone(moved({ lastStatusChange: null }), 'me', NOW)).toBe(false);
      expect(recentlyMovedIntoDone(moved(), undefined, NOW)).toBe(false);
    });

    it('tolerates a small clock difference between the browser and Jira', () => {
      expect(recentlyMovedIntoDone(moved({ statusChangedAt: NOW + 5_000, lastStatusChange: { accountId: 'me', at: NOW + 5_000 } }), 'me', NOW)).toBe(true);
    });
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
