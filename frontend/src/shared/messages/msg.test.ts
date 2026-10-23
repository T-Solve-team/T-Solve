import { MESSAGES } from './catalog';
import { TRACKER_NAME } from '@/shared/config/tracker';
import { msg } from './msg';

describe('msg', () => {
  it('AC-006.4 fills {tracker_name} with the configured tracker "Jira"', () => {
    expect(TRACKER_NAME).toBe('Jira');
    expect(msg('MSG17')).toBe(
      'Changes requested. The author will see your comment in Jira and receive it by email.',
    );
  });

  it('AC-006.4 uses another tracker name when the caller passes one', () => {
    expect(msg('MSG17', { tracker_name: 'GitLab' })).toContain('your comment in GitLab');
  });

  it('AC-006.5 replaces each {name} placeholder with its value', () => {
    expect(msg('MSG14', { count: 3 })).toBe('3 ticket(s) approved and published.');
    expect(msg('MSG02', { field_name: 'Reason', max_length: 1000 })).toBe(
      'Reason must not exceed 1000 characters.',
    );
  });

  it('AC-006.6 keeps a placeholder whose value is missing', () => {
    expect(msg('MSG14')).toBe('{count} ticket(s) approved and published.');
  });

  it('AC-002.2 uses Report3 review message types (BUG-008)', () => {
    expect(MESSAGES).toMatchObject({
      MSG13: { type: 'inline' },
      MSG14: { type: 'toast' },
      MSG15: { type: 'confirm' },
      MSG16: { type: 'toast' },
      MSG17: { type: 'toast' },
      MSG18: { type: 'tooltip' },
      MSG19: { type: 'confirm', text: 'Approve and publish {count} ticket(s)?' },
    });
  });
});
