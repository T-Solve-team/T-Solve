import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/render';
import { DetailModal } from './DetailModal';

function renderModal(onClose = vi.fn()) {
  renderWithProviders(
    <DetailModal isOpen title="Import Ticket Preview" onClose={onClose}>
      <p>Row content</p>
    </DetailModal>,
  );
  return onClose;
}

describe('DetailModal', () => {
  it('shows the title, the content and one Close button, with no OK or Cancel', async () => {
    renderModal();

    const dialog = await screen.findByRole('dialog', { name: 'Import Ticket Preview' });
    expect(within(dialog).getByText('Row content')).toBeInTheDocument();
    // The footer button; the header's close icon has the name "Close" but no text.
    expect(within(dialog).getByText('Close').closest('button')).toBeInTheDocument();
    expect(within(dialog).queryByRole('button', { name: 'OK' })).not.toBeInTheDocument();
    expect(within(dialog).queryByRole('button', { name: 'Cancel' })).not.toBeInTheDocument();
  });

  it('calls onClose from the Close button and from Esc', async () => {
    const user = userEvent.setup();
    const onClose = renderModal();
    await screen.findByRole('dialog');

    await user.click(screen.getByText('Close'));
    expect(onClose).toHaveBeenCalledOnce();

    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalledTimes(2);
  });
});
