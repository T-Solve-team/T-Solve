import { MESSAGES } from './catalog';
import { msg } from './msg';

describe('msg', () => {
  it('fills placeholders', () => {
    expect(msg('MSG14', { count: 3 })).toBe('3 ticket(s) approved and published.');
    expect(msg('MSG02', { field_name: 'Reason', max_length: 1000 })).toBe(
      'Reason must not exceed 1000 characters.',
    );
  });

  it('keeps a placeholder whose parameter is missing', () => {
    expect(msg('MSG14')).toBe('{count} ticket(s) approved and published.');
  });

  it('has no retired codes', () => {
    const retired = [
      'MSG09',
      'MSG11',
      'MSG20',
      'MSG23',
      'MSG28',
      'MSG37',
      'MSG48',
      'MSG49',
      'MSG69',
    ];
    for (const code of retired) expect(MESSAGES).not.toHaveProperty(code);
  });

  it('AC-002.2 uses Report3 review message types (BUG-004)', () => {
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
