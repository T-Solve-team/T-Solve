import { App } from 'antd';
import { act, render, screen, waitFor } from '@testing-library/react';
import { dismissAcknowledgement, MessageHost, msg, showAcknowledgement, showMessage } from '.';

function renderHost() {
  return render(
    <App>
      <MessageHost />
    </App>,
  );
}

describe('dismissAcknowledgement', () => {
  it('closes only the dialog of that message code and resolves its promise', async () => {
    renderHost();
    let closed07 = false;
    let closed08 = false;
    act(() => {
      void showAcknowledgement('MSG07').then(() => (closed07 = true));
      void showAcknowledgement('MSG08').then(() => (closed08 = true));
    });
    await waitFor(() => expect(screen.getAllByRole('dialog')).toHaveLength(2));

    act(() => dismissAcknowledgement('MSG07'));

    await waitFor(() => expect(screen.getAllByRole('dialog')).toHaveLength(1));
    expect(screen.getByRole('dialog')).toHaveTextContent(msg('MSG08'));
    expect(closed07).toBe(true);
    expect(closed08).toBe(false);
  });

  it('does nothing when no dialog of that code is open', () => {
    renderHost();
    expect(() => dismissAcknowledgement('MSG07')).not.toThrow();
  });
});

describe('showMessage', () => {
  it.each(['MSG119', 'MSG120'] as const)(
    'AC-011.5 never shows %s as a toast (the site page is the only place)',
    (code) => {
      renderHost();
      act(() => showMessage(code));
      expect(screen.queryByText(msg(code))).not.toBeInTheDocument();
    },
  );

  it('shows another code as a toast', async () => {
    renderHost();
    act(() => showMessage('MSG06'));
    expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
  });
});
