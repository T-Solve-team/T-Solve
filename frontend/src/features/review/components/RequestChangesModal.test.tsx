import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { renderWithProviders } from '@/test/render';
import { RequestChangesModal } from './RequestChangesModal';

describe('RequestChangesModal', () => {
  it('submits one ticketId with the comment (BUG-013, AC-003.1)', async () => {
    let submitted: unknown;
    server.use(
      http.post('*/api/v1/reviews/request-changes', async ({ request }) => {
        submitted = await request.json();
        return HttpResponse.json({ count: 1 });
      }),
    );
    const onSent = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <RequestChangesModal ticketId={7} isOpen onClose={vi.fn()} onSent={onSent} />,
    );
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText('Comment'), 'Add the exact error message.');
    await user.click(within(dialog).getByRole('button', { name: 'Send' }));
    await vi.waitFor(() => expect(onSent).toHaveBeenCalledOnce());
    expect(submitted).toEqual({ ticketId: 7, comment: 'Add the exact error message.' });
  });
});
