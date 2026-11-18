import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/render';
import { FileDropzone } from './FileDropzone';

function fileInput(): HTMLInputElement {
  const input = document.querySelector<HTMLInputElement>('input[type="file"]');
  if (!input) throw new Error('no file input');
  return input;
}

describe('FileDropzone', () => {
  it('hands the whole selection to onAdd once and keeps no list of its own (BUG-276)', async () => {
    const onAdd = vi.fn<(files: File[]) => void>();
    renderWithProviders(
      <FileDropzone label="Files" accept=".pdf,.docx" hint="PDF or DOCX." onAdd={onAdd} />,
    );
    const files = [new File(['a'], 'a.pdf'), new File(['b'], 'b.docx')];

    await userEvent.setup().upload(fileInput(), files);

    expect(onAdd).toHaveBeenCalledTimes(1);
    expect(onAdd.mock.calls[0]?.[0].map((file) => file.name)).toEqual(['a.pdf', 'b.docx']);
    expect(screen.getByText('PDF or DOCX.')).toBeInTheDocument();
    expect(screen.queryByText('a.pdf')).not.toBeInTheDocument();
  });

  it('accepts nothing while disabled (BUG-276)', async () => {
    const onAdd = vi.fn<(files: File[]) => void>();
    renderWithProviders(
      <FileDropzone label="Files" accept=".pdf" hint="PDF." disabled onAdd={onAdd} />,
    );

    await userEvent.setup().upload(fileInput(), new File(['a'], 'a.pdf'));

    expect(onAdd).not.toHaveBeenCalled();
  });
});
