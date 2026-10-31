import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { server } from '@/mocks/server';
import { setDevUser } from '@/shared/auth';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

// Its own file: the app keeps one MSG07 dialog open per module (app/providers/global-errors.ts), so a second 401 test
// in KnowledgeChatbotPage.test.tsx, which already has one for the question, could never show its dialog.
describe('KnowledgeChatbotPage rating, session expired', () => {
  beforeAll(async () => {
    await import('./KnowledgeChatbotPage');
  });

  beforeEach(() => sessionStorage.clear());

  it('AC-003.5 leaves a 401 to the sign-out (MSG07) and shows no MSG06 (edge 28)', async () => {
    const user = userEvent.setup();
    renderApp('/chatbot', { role: 'EMPLOYEE' });
    const box = await screen.findByRole('textbox', { name: 'Question' }, { timeout: 5000 });
    await user.type(box, 'How do I connect to the VPN from home?{Enter}');
    const helpful = await screen.findByRole('button', { name: 'Helpful' });
    await waitFor(() => expect(helpful).toBeEnabled());
    // The session expires on the server: every request now answers 401.
    setDevUser(null);
    server.use(http.put('*/api/v1/chatbot/answers/:answerId/rating', () => problem(401)));

    await user.click(helpful);

    expect(await screen.findByRole('dialog')).toHaveTextContent(msg('MSG07'));
    expect(screen.queryByText(msg('MSG06'))).not.toBeInTheDocument();
  });
});
