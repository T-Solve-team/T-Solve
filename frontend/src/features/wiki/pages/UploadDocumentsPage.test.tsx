import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { server } from '@/mocks/server';
import type { WikiUploadPreview } from '@/shared/api';
import {
  getListWikiDocumentsQueryKey,
  getListCompanyWideDocumentsQueryKey,
  getSearchWikiDocumentsQueryKey,
} from '@/shared/api';
import {
  getExtractWikiUploadsMockHandler,
  getListWikiFoldersMockHandler,
  getPublishWikiDocumentsMockHandler,
} from '@/shared/api/generated/wiki/wiki.msw';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { renderApp } from '@/test/render';

const IT = '00000000-0000-4000-8000-0000000000a1';
const GUIDES = '00000000-0000-4000-8000-0000000000f1';
const NETWORK = '00000000-0000-4000-8000-0000000000f2';
const preview: WikiUploadPreview = {
  fileName: 'guide.PDF',
  title: 'guide',
  excerpt: 'Connect to the company VPN.',
  wordCount: 5,
  result: 'READY',
};

function file(name = 'guide.PDF', content = '%PDF-1.7\nReadable text') {
  return new File([content], name, {
    type: name.toLowerCase().endsWith('.pdf')
      ? 'application/pdf'
      : 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  });
}

async function openUpload(folder = GUIDES) {
  const view = renderApp(`${paths.wikiUpload}?space=${IT}${folder ? `&folder=${folder}` : ''}`, {
    role: 'KNOWLEDGE_MANAGER',
  });
  await screen.findByRole('combobox', { name: 'Folder' }, { timeout: 5000 });
  const user = userEvent.setup({ applyAccept: false });
  return { ...view, user, input: () => screen.getByLabelText('Files', { selector: 'input' }) };
}

async function addReadyFile(previews = [preview]) {
  server.use(getExtractWikiUploadsMockHandler(previews));
  const view = await openUpload();
  await view.user.upload(
    view.input(),
    previews.map((item) => file(item.fileName)),
  );
  await screen.findByRole('textbox', { name: 'Title for guide.PDF' });
  return view;
}

describe('UploadDocumentsPage', () => {
  it('AC-004.18 opens from Wiki Home with the selected folder and Department visibility', async () => {
    const { user, router } = await openUpload();
    expect(
      screen.getByRole('combobox', { name: 'Folder' }).closest('.ant-select'),
    ).toHaveTextContent('Guides');
    expect(
      screen.getByRole('combobox', { name: 'Visibility' }).closest('.ant-select'),
    ).toHaveTextContent('Department');
    expect(screen.getByRole('button', { name: 'Publish' })).toBeDisabled();
    await act(() => router.navigate(`${paths.wikiHome}?space=${IT}&folder=${NETWORK}`));
    await user.click(await screen.findByRole('button', { name: 'Upload documents' }));
    expect(router.state.location.search).toContain(`folder=${NETWORK}`);
    expect(await screen.findByRole('combobox', { name: 'Folder' })).toBeInTheDocument();
  });

  it('AC-004.18 loads all folder levels, offers Top level, and retries a failed folder request', async () => {
    server.use(http.get('*/api/v1/wiki/spaces/:spaceId/folders', () => problem(500, 'MSG06')));
    const view = renderApp(`${paths.wikiUpload}?space=${IT}`, { role: 'KNOWLEDGE_MANAGER' });
    expect(await screen.findByText(msg('MSG06'), {}, { timeout: 5000 })).toBeInTheDocument();
    server.use(getListWikiFoldersMockHandler([]));
    await userEvent.setup().click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('combobox', { name: 'Folder' })).toBeInTheDocument();
    expect(view.router.state.location.pathname).toBe(paths.wikiUpload);
    expect(screen.getByText('Top level')).toBeInTheDocument();
  });

  it('AC-004.19 rejects a selection over ten files without extracting any', async () => {
    const extract = vi.fn();
    server.use(
      http.post('*/api/v1/wiki/spaces/:spaceId/uploads/extract', () => {
        extract();
        return problem(400, 'MSG88');
      }),
    );
    const { user, input } = await openUpload();
    await user.upload(
      input(),
      Array.from({ length: 11 }, (_, index) => file(`${index}.pdf`)),
    );
    expect(await screen.findByText(msg('MSG88'))).toBeInTheDocument();
    expect(extract).not.toHaveBeenCalled();
    expect(screen.queryByRole('textbox', { name: /Title for/ })).not.toBeInTheDocument();
  });

  it('AC-004.19 counts existing rows when rejecting an extra batch and keeps them', async () => {
    const previews = Array.from({ length: 10 }, (_, index) => ({
      ...preview,
      fileName: `${index}.pdf`,
      title: `${index}`,
    }));
    server.use(getExtractWikiUploadsMockHandler(previews));
    const { user, input } = await openUpload();
    await user.upload(
      input(),
      previews.map((item) => file(item.fileName)),
    );
    await screen.findByRole('textbox', { name: 'Title for 9.pdf' });
    await user.upload(input(), file('extra.pdf'));
    expect(await screen.findByText(msg('MSG88'))).toBeInTheDocument();
    expect(screen.getAllByRole('textbox', { name: /Title for/ })).toHaveLength(10);
  });

  it('AC-004.19 rejects unsupported extensions and files above 10 MB with MSG89 by name', async () => {
    const { user, input } = await openUpload();
    const oversized = file('large.pdf');
    Object.defineProperty(oversized, 'size', { value: 10 * 1024 * 1024 + 1 });
    await user.upload(input(), [file('notes.txt'), oversized]);
    expect(await screen.findByText(msg('MSG89', { file_name: 'notes.txt' }))).toBeInTheDocument();
    expect(screen.getByText(msg('MSG89', { file_name: 'large.pdf' }))).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: /Title for/ })).not.toBeInTheDocument();
  });

  it('AC-004.20 extracts originals in order and shows editable rows, excerpts, counts and Remove', async () => {
    let names: string[] = [];
    server.use(
      getExtractWikiUploadsMockHandler(async ({ request }) => {
        names = (await request.formData())
          .getAll('files')
          .map((entry) => (typeof entry === 'string' ? entry : entry.name));
        return [preview, { ...preview, fileName: 'handbook.DoCx', title: 'handbook' }];
      }),
    );
    const { user, input } = await openUpload();
    await user.upload(input(), [file(), file('handbook.DoCx')]);
    const title = await screen.findByRole('textbox', { name: 'Title for guide.PDF' });
    expect(names).toEqual(['guide.PDF', 'handbook.DoCx']);
    expect(title).toHaveValue('guide');
    const table = screen.getByRole('table', { name: 'Documents to publish' });
    for (const name of ['File name', 'Visibility', 'Title', 'Extracted text', 'Words', 'Result'])
      expect(within(table).getByRole('columnheader', { name })).toBeInTheDocument();
    expect(within(table).getAllByText(preview.excerpt)).toHaveLength(2);
    expect(within(table).getAllByText(String(preview.wordCount))).toHaveLength(2);
    expect(within(table).getAllByText('Ready')).toHaveLength(2);
    await user.clear(title);
    await user.type(title, 'Edited title');
    await user.click(screen.getByRole('button', { name: 'Remove handbook.DoCx' }));
    expect(
      screen.queryByRole('textbox', { name: 'Title for handbook.DoCx' }),
    ).not.toBeInTheDocument();
    expect(title).toHaveValue('Edited title');
  });

  it('AC-004.21 disables Publish for unreadable, blank, overlong and removed rows', async () => {
    server.use(
      getExtractWikiUploadsMockHandler([
        { ...preview, result: 'UNREADABLE', wordCount: 0, excerpt: '' },
      ]),
    );
    const { user, input } = await openUpload();
    await user.upload(input(), file());
    expect(
      await screen.findByText(msg('MSG90', { file_name: preview.fileName })),
    ).toBeInTheDocument();
    const publish = screen.getByRole('button', { name: 'Publish' });
    expect(publish).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Remove guide.PDF' }));
    server.use(getExtractWikiUploadsMockHandler([preview]));
    await user.upload(input(), file());
    const title = await screen.findByRole('textbox', { name: 'Title for guide.PDF' });
    expect(publish).toBeEnabled();
    await user.clear(title);
    await user.type(title, '   ');
    expect(publish).toBeDisabled();
    await user.clear(title);
    await user.paste('a'.repeat(201));
    expect(title).toHaveValue('a'.repeat(201));
    expect(publish).toBeDisabled();
    expect(
      await screen.findByText(msg('MSG02', { field_name: 'Title', max_length: 200 })),
    ).toBeInTheDocument();
    await user.clear(title);
    await user.paste('a'.repeat(200));
    expect(publish).toBeEnabled();
    await user.click(screen.getByRole('button', { name: 'Remove guide.PDF' }));
    expect(publish).toBeDisabled();
    expect(screen.getByText(msg('MSG04'))).toBeInTheDocument();
  });

  it('AC-004.20 keeps earlier edits after another extraction and can retry a failed batch', async () => {
    const { user, input } = await addReadyFile();
    const title = screen.getByRole('textbox', { name: 'Title for guide.PDF' });
    await user.clear(title);
    await user.paste('Keep my title');
    server.use(
      http.post('*/api/v1/wiki/spaces/:spaceId/uploads/extract', () => problem(500, 'MSG06')),
    );
    await user.upload(input(), file('second.pdf'));
    expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
    expect(title).toHaveValue('Keep my title');
    server.use(getExtractWikiUploadsMockHandler([{ ...preview, fileName: 'second.pdf' }]));
    await user.upload(input(), file('second.pdf'));
    await screen.findByRole('textbox', { name: 'Title for second.pdf' });
    expect(title).toHaveValue('Keep my title');
  });

  it('AC-004.22 publishes the edited metadata and originals, refreshes lists and returns to the target folder', async () => {
    let body: FormData | undefined;
    server.use(
      getPublishWikiDocumentsMockHandler(async ({ request }) => {
        body = await request.formData();
        return [{ documentId: crypto.randomUUID(), title: 'Edited title' }];
      }),
    );
    const { user, router, queryClient } = await addReadyFile();
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    const title = screen.getByRole('textbox', { name: 'Title for guide.PDF' });
    await user.clear(title);
    await user.paste('Edited title');
    await user.click(screen.getByRole('combobox', { name: 'Visibility for guide.PDF' }));
    await user.click(screen.getByRole('option', { name: 'Company-wide' }));
    expect(
      screen.getByRole('combobox', { name: 'Visibility for guide.PDF' }).closest('.ant-select'),
    ).toHaveTextContent('Company-wide');
    await user.click(screen.getByRole('button', { name: 'Publish' }));
    expect(await screen.findByText(msg('MSG95', { count: 1 }))).toBeInTheDocument();
    await waitFor(() => expect(router.state.location.pathname).toBe(paths.wikiHome));
    expect(new URLSearchParams(router.state.location.search).get('folder')).toBe(GUIDES);
    expect(body?.get('metadata')).toBe(
      JSON.stringify({
        folderId: GUIDES,
        documents: [{ title: 'Edited title', visibility: 'COMPANY_WIDE' }],
      }),
    );
    const original = body?.get('files');
    expect(original).toBeInstanceOf(File);
    if (original instanceof File) expect(await original.text()).toContain('%PDF-1.7');
    for (const queryKey of [
      getListWikiDocumentsQueryKey(IT),
      getListCompanyWideDocumentsQueryKey(),
      getSearchWikiDocumentsQueryKey(),
    ])
      expect(invalidate).toHaveBeenCalledWith({ queryKey });
  });

  it.each(['MSG84', 'MSG01', 'MSG02'] as const)(
    'AC-004.22 keeps every row and shows %s on the affected title as "Title" (BUG-277)',
    async (code) => {
      server.use(
        http.post('*/api/v1/wiki/spaces/:spaceId/documents', () =>
          problem(code === 'MSG84' ? 409 : 400, code, {
            fieldErrors: [
              {
                field: 'documents[0].title',
                code,
                params: { field_name: 'documents[0].title', max_length: 200 },
              },
            ],
          }),
        ),
      );
      const { user, router } = await addReadyFile([
        preview,
        { ...preview, fileName: 'second.pdf', title: 'Second title' },
      ]);
      await user.click(screen.getByRole('button', { name: 'Publish' }));
      const title = screen.getByRole('textbox', { name: 'Title for guide.PDF' });
      const row = title.closest('tr');
      expect(row).not.toBeNull();
      if (row)
        expect(
          await within(row).findByText(msg(code, { field_name: 'Title', max_length: 200 })),
        ).toBeInTheDocument();
      expect(title).toHaveValue('guide');
      expect(screen.getByRole('textbox', { name: 'Title for second.pdf' })).toHaveValue(
        'Second title',
      );
      expect(router.state.location.pathname).toBe(paths.wikiUpload);
    },
  );

  it('AC-004.22 uses stateful mocks to publish and browse the new document (BUG-278)', async () => {
    const { user, input } = await openUpload();
    await user.upload(input(), file('new-guide.pdf'));
    await screen.findByRole('textbox', { name: 'Title for new-guide.pdf' });
    await user.click(screen.getByRole('button', { name: 'Publish' }));
    expect(await screen.findByText(msg('MSG95', { count: 1 }))).toBeInTheDocument();
    // Wiki Home is a lazy page and loads its Space, folders and documents in turn: give it more time (BUG-278).
    expect(
      await screen.findByRole('link', { name: 'new-guide' }, { timeout: 5000 }),
    ).toBeInTheDocument();
  });

  it('AC-004.23 cancels back to Wiki Home without publishing', async () => {
    const publish = vi.fn();
    server.use(
      http.post('*/api/v1/wiki/spaces/:spaceId/documents', () => {
        publish();
        return problem(500, 'MSG06');
      }),
    );
    const { user, router } = await addReadyFile();
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(router.state.location.pathname).toBe(paths.wikiHome));
    expect(publish).not.toHaveBeenCalled();
  });

  it('AC-004.19 accepts exactly 10 MB and extracts only valid files from a mixed selection', async () => {
    const { user, input } = await openUpload();
    const boundary = new File(['%PDF-', new Uint8Array(10 * 1024 * 1024 - 5)], 'boundary.pdf', {
      type: 'application/pdf',
    });
    await user.upload(input(), [file('notes.txt'), boundary]);
    expect(
      await screen.findByRole('textbox', { name: 'Title for boundary.pdf' }),
    ).toBeInTheDocument();
    expect(screen.getByText(msg('MSG89', { file_name: 'notes.txt' }))).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Publish' })).toBeEnabled();
  });

  it('AC-004.18 applies the chosen default visibility to new rows and publishes in the changed folder', async () => {
    const { user, input, router } = await openUpload('');
    await user.click(screen.getByRole('combobox', { name: 'Folder' }));
    const tree = await screen.findByRole('tree');
    expect(within(tree).getByText('VPN')).toBeInTheDocument();
    await user.click(within(tree).getByText('Network'));
    await user.click(screen.getByRole('combobox', { name: 'Visibility' }));
    await user.click(screen.getByRole('option', { name: 'Company-wide' }));
    await user.upload(input(), file('selected-folder.pdf'));
    await screen.findByRole('textbox', { name: 'Title for selected-folder.pdf' });
    expect(
      screen
        .getByRole('combobox', { name: 'Visibility for selected-folder.pdf' })
        .closest('.ant-select'),
    ).toHaveTextContent('Company-wide');
    await user.click(screen.getByRole('button', { name: 'Publish' }));
    await waitFor(() => expect(router.state.location.pathname).toBe(paths.wikiHome));
    expect(new URLSearchParams(router.state.location.search).get('folder')).toBe(NETWORK);
    expect(
      await within(await screen.findByRole('table', { name: 'Documents' })).findByRole('link', {
        name: 'selected-folder',
      }),
    ).toBeInTheDocument();
  });

  it('AC-004.22 prevents duplicate publishes and locks edits until the request completes', async () => {
    let complete: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      complete = resolve;
    });
    const submitted = vi.fn();
    server.use(
      getPublishWikiDocumentsMockHandler(async () => {
        submitted();
        await pending;
        return [{ documentId: crypto.randomUUID(), title: 'guide' }];
      }),
    );
    const { user } = await addReadyFile();
    const publish = screen.getByRole('button', { name: 'Publish' });
    await user.dblClick(publish);
    await waitFor(() => expect(submitted).toHaveBeenCalledTimes(1));
    expect(screen.getByRole('textbox', { name: 'Title for guide.PDF' })).toBeDisabled();
    expect(publish).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Remove guide.PDF' })).toBeDisabled();
    await act(async () => {
      complete?.();
      await pending;
    });
    expect(await screen.findByText(msg('MSG95', { count: 1 }))).toBeInTheDocument();
  });
});
