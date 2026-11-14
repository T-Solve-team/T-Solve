import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { msg } from '@/shared/messages';
import { renderWithProviders } from '@/test/render';
import { EvidenceUpload, type EvidenceUploadValue } from './EvidenceUpload';

const TEN_MB = 10 * 1024 * 1024;

function file(name: string, size?: number) {
  const value = new File(['contents'], name);
  if (size !== undefined) Object.defineProperty(value, 'size', { value: size });
  return value;
}

function input(): HTMLInputElement {
  const element = document.querySelector<HTMLInputElement>('input[type="file"]');
  if (!element) throw new Error('no Evidence input');
  return element;
}

function Field({
  initialValue = { keptFiles: [], newFiles: [] },
  disabled = false,
}: {
  initialValue?: EvidenceUploadValue;
  disabled?: boolean;
}) {
  const [value, setValue] = useState(initialValue);
  return <EvidenceUpload value={value} onChange={setValue} disabled={disabled} />;
}

describe('EvidenceUpload', () => {
  it('AC-006.22 lists multiple files, accepts uppercase and exactly 10 MB, and removes one', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <Field
        initialValue={{ keptFiles: [{ id: 'kept', name: 'saved.pdf', size: 12 }], newFiles: [] }}
      />,
    );
    await user.upload(input(), [file('photo.JPEG', TEN_MB), file('report.log')]);
    expect(await screen.findByText('photo.JPEG')).toBeInTheDocument();
    expect(screen.getByText('report.log')).toBeInTheDocument();
    expect(screen.getByText('saved.pdf')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Remove photo.JPEG' }));
    expect(screen.queryByText('photo.JPEG')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Remove saved.pdf' }));
    expect(screen.queryByText('saved.pdf')).not.toBeInTheDocument();
    expect(screen.getByText('report.log')).toBeInTheDocument();
  });

  it('AC-006.22 refuses the sixth file with MSG72 including kept files in the count', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <Field
        initialValue={{ keptFiles: [{ id: 'kept', name: 'saved.pdf', size: 1 }], newFiles: [] }}
      />,
    );
    await user.upload(
      input(),
      Array.from({ length: 5 }, (_, index) => file(`file-${index}.txt`)),
    );
    expect(await screen.findByText(msg('MSG72'))).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /^Remove / })).toHaveLength(5);
    expect(screen.queryByText('file-4.txt')).not.toBeInTheDocument();
  });

  it.each([
    ['program.exe', 1],
    ['huge.pdf', TEN_MB + 1],
  ] as const)(
    'AC-006.22 refuses %s with MSG34 and retains the earlier files',
    async (name, size) => {
      const user = userEvent.setup({ applyAccept: false });
      renderWithProviders(<Field />);
      await user.upload(input(), file('keep.log'));
      await user.upload(input(), file(name, size));
      expect(await screen.findByText(msg('MSG34', { file_name: name }))).toBeInTheDocument();
      expect(screen.queryByText(name)).not.toBeInTheDocument();
      expect(screen.getByText('keep.log')).toBeInTheDocument();
    },
  );

  it('AC-006.22 never uploads files automatically', async () => {
    const user = userEvent.setup();
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const xhrSpy = vi.spyOn(XMLHttpRequest.prototype, 'open');
    renderWithProviders(<Field />);
    await user.upload(input(), file('local.txt'));
    await screen.findByText('local.txt');
    await waitFor(() => expect(xhrSpy).not.toHaveBeenCalled());
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('AC-006.22 shows the supplied error and disables adding and removing while disabled', () => {
    renderWithProviders(
      <EvidenceUpload
        value={{ keptFiles: [{ id: 'kept', name: 'saved.txt', size: 1 }], newFiles: [] }}
        disabled
        error={msg('MSG72')}
      />,
    );
    expect(screen.getByText(msg('MSG72'))).toBeInTheDocument();
    expect(input()).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Remove saved.txt' })).toBeDisabled();
  });
});
