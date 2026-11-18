import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { server } from '@/mocks/server';
import { askChatbot } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { setDevUser } from '@/shared/auth';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

// A separate file gives session expiry its own global MSG07 dialog guard, as in the rating 401 test.
describe('KnowledgeChatbotPage delete, session expired', () => {
  beforeAll(async () => {
    await import('./KnowledgeChatbotPage');
  });

  it('AC-002.14 leaves a delete 401 to MSG07 without showing MSG06 (BUG-342)', async () => {
    setDevUser('employee');
    await askChatbot({ question: 'VPN' });
    const user = userEvent.setup();
    renderApp('/chatbot', { role: 'EMPLOYEE' });
    const title = await screen.findByRole('button', { name: 'VPN' }, { timeout: 5000 });
    const row = title.closest('li');
    if (!row) throw new Error('Missing conversation');
    await user.click(within(row).getByRole('button', { name: 'Delete' }));
    setDevUser(null);
    server.use(http.delete('*/api/v1/chatbot/conversations/:id', () => problem(401, 'MSG07')));

    await user.click(screen.getByRole('button', { name: 'OK' }));

    await waitFor(() => {
      expect(
        screen.getAllByRole('dialog').some((dialog) => dialog.textContent?.includes(msg('MSG07'))),
      ).toBe(true);
    });
    expect(screen.queryByText(msg('MSG06'))).not.toBeInTheDocument();
  });
});
