import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Button } from 'antd';
import { msg } from '@/shared/messages';
import { renderWithProviders } from '@/test/render';
import { AppForm } from '../AppForm';
import { CsvUpload } from './CsvUpload';

const TEN_MB = 10 * 1024 * 1024;
const HINT = 'One .csv file, UTF-8, at most 10 MB.';

function csvFile(name = 'tickets.csv', size?: number): File {
  const file = new File(['ticket_id,title\n'], name, { type: 'text/csv' });
  if (size !== undefined) Object.defineProperty(file, 'size', { value: size });
  return file;
}

function renderField(onFinish = vi.fn()) {
  renderWithProviders(
    <AppForm<{ file?: File }> onFinish={onFinish}>
      <CsvUpload name="file" label="CSV file" required />
      <Button htmlType="submit">Upload</Button>
    </AppForm>,
  );
  return onFinish;
}

function fileInput(): HTMLInputElement {
  const input = document.querySelector<HTMLInputElement>('input[type="file"]');
  if (!input) throw new Error('no file input');
  return input;
}

describe('CsvUpload', () => {
  it('AC-011.1 keeps one file chosen by clicking, shows its name and lets the user remove it', async () => {
    const user = userEvent.setup();
    const onFinish = renderField();

    await user.upload(fileInput(), csvFile());
    expect(await screen.findByText('tickets.csv')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /remove/i }));
    // antd plays a leave animation; jsdom never ends it, so the item goes at the motion deadline (2 s)
    await waitFor(() => expect(screen.queryByText('tickets.csv')).not.toBeInTheDocument(), {
      timeout: 3000,
    });

    await user.click(screen.getByRole('button', { name: 'Upload' }));
    expect(await screen.findByText(msg('MSG01', { field_name: 'CSV file' }))).toBeInTheDocument();
    expect(onFinish).not.toHaveBeenCalled();
  });

  it('AC-011.1 keeps a dragged file and replaces the earlier one', async () => {
    const user = userEvent.setup();
    renderField();
    await user.upload(fileInput(), csvFile('first.csv'));
    expect(await screen.findByText('first.csv')).toBeInTheDocument();

    fireEvent.drop(screen.getByText(HINT), {
      dataTransfer: { files: [csvFile('second.csv')], items: [] },
    });

    expect(await screen.findByText('second.csv')).toBeInTheDocument();
    expect(screen.queryByText('first.csv')).not.toBeInTheDocument();
  });

  it.each(['data.csv.txt', 'tickets.xlsx', 'csv'])(
    'AC-011.2 does not keep %s and shows MSG27 at the field',
    async (name) => {
      const user = userEvent.setup({ applyAccept: false });
      renderField();

      await user.upload(fileInput(), csvFile(name));

      expect(await screen.findByText(msg('MSG27'))).toBeInTheDocument();
      expect(screen.queryByText(name)).not.toBeInTheDocument();
    },
  );

  it('AC-011.2 reports MSG27 for a dropped file that is not a CSV file', async () => {
    renderField();

    fireEvent.drop(screen.getByText(HINT), {
      dataTransfer: { files: [csvFile('photo.png')], items: [] },
    });

    expect(await screen.findByText(msg('MSG27'))).toBeInTheDocument();
    expect(screen.queryByText('photo.png')).not.toBeInTheDocument();
  });

  it('AC-011.2 keeps REPORT.CSV (any case) and a file of exactly 10 MB', async () => {
    const user = userEvent.setup();
    renderField();

    await user.upload(fileInput(), csvFile('REPORT.CSV', TEN_MB));

    expect(await screen.findByText('REPORT.CSV')).toBeInTheDocument();
    expect(screen.queryByText(msg('MSG28'))).not.toBeInTheDocument();
  });

  it('AC-011.2 does not keep a file larger than 10 MB and shows MSG28 at the field', async () => {
    const user = userEvent.setup();
    const onFinish = renderField();

    await user.upload(fileInput(), csvFile('big.csv', TEN_MB + 1));

    expect(await screen.findByText(msg('MSG28'))).toBeInTheDocument();
    expect(screen.queryByText('big.csv')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Upload' }));
    expect(onFinish).not.toHaveBeenCalled();
  });

  it('AC-011.3 never sends the file itself; the form gets the File when it is submitted', async () => {
    const user = userEvent.setup();
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const xhrSpy = vi.spyOn(XMLHttpRequest.prototype, 'open');
    const onFinish = renderField();
    const file = csvFile();

    await user.upload(fileInput(), file);
    await screen.findByText('tickets.csv');
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(xhrSpy).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'Upload' }));
    await waitFor(() => expect(onFinish).toHaveBeenCalledWith({ file }));
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(xhrSpy).not.toHaveBeenCalled();
  });
});
