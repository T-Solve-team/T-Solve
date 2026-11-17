import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { server } from '@/mocks/server';
import type { WikiFolder } from '@/shared/api';
import {
  getCreateWikiFolderMockHandler,
  getListCompanyWideDocumentsMockHandler,
  getListWikiDocumentsMockHandler,
  getListWikiFoldersMockHandler,
  getListWikiSpacesMockHandler,
  getUpdateWikiFolderMockHandler,
} from '@/shared/api/generated/wiki/wiki.msw';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { renderApp } from '@/test/render';

const SPACE = '00000000-0000-4000-8000-0000000000a1';
const ROOT = '00000000-0000-4000-8000-0000000000f1';
const CHILD = '00000000-0000-4000-8000-0000000000f2';
const LEAF = '00000000-0000-4000-8000-0000000000f3';
const OTHER = '00000000-0000-4000-8000-0000000000f4';
let folders: WikiFolder[];

beforeEach(() => {
  folders = [
    { folderId: ROOT, parentId: null, name: 'Guides', level: 1 },
    { folderId: CHILD, parentId: ROOT, name: 'Network', level: 2 },
    { folderId: LEAF, parentId: CHILD, name: 'VPN', level: 3 },
    { folderId: OTHER, parentId: null, name: 'Procedures', level: 1 },
  ];
  server.use(
    getListWikiSpacesMockHandler([
      { spaceId: SPACE, name: 'IT Space', departmentName: 'IT', accessLevel: 'KNOWLEDGE_MANAGER' },
    ]),
    getListCompanyWideDocumentsMockHandler([]),
    getListWikiDocumentsMockHandler([]),
    getListWikiFoldersMockHandler(() => folders),
  );
});

async function openFolder(action = 'New folder', name = 'Guides') {
  const user = userEvent.setup();
  const view = renderApp(`${paths.wikiHome}?space=${SPACE}`, { role: 'KNOWLEDGE_MANAGER' });
  const tree = await screen.findByRole('tree', { name: 'Folders' }, { timeout: 5000 });
  if (action === 'New folder') {
    await user.click(screen.getByRole('button', { name: action }));
  } else {
    const row = within(tree).getByText(name).closest('[role="treeitem"]');
    if (!(row instanceof HTMLElement)) throw new Error('Folder row missing');
    await user.click(within(row).getByRole('button', { name: `${action} ${name}` }));
  }
  const dialog = await screen.findByRole('dialog', { name: 'Folder' });
  return { ...view, user, dialog, name: within(dialog).getByRole('textbox', { name: 'Name' }) };
}

async function parentChoices(user: ReturnType<typeof userEvent.setup>, dialog: HTMLElement) {
  await user.click(within(dialog).getByRole('combobox', { name: 'Parent folder' }));
  const tree = screen
    .getAllByRole('tree')
    .find((item) => item.getAttribute('aria-label') !== 'Folders');
  if (!tree) throw new Error('Parent tree missing');
  return within(tree);
}

describe('FolderModal', () => {
  it('AC-003.13 opens New folder with Name and a parent tree that omits level 3', async () => {
    const { user, dialog, name } = await openFolder();
    expect(name).toHaveValue('');
    const choices = await parentChoices(user, dialog);
    expect(choices.getByText('Top level')).toBeInTheDocument();
    expect(choices.getByText('Guides')).toBeInTheDocument();
    expect(choices.getByText('Network')).toBeInTheDocument();
    expect(choices.queryByText('VPN')).not.toBeInTheDocument();
  });

  it.each(['Rename', 'Move'])(
    'AC-003.13 %s prefills the folder and excludes itself and its entire sub-tree',
    async (action) => {
      const { user, dialog, name } = await openFolder(action);
      expect(name).toHaveValue('Guides');
      const choices = await parentChoices(user, dialog);
      expect(choices.getByText('Procedures')).toBeInTheDocument();
      for (const excluded of ['Guides', 'Network', 'VPN']) {
        expect(choices.queryByText(excluded)).not.toBeInTheDocument();
      }
    },
  );

  it.each([
    ['', 'MSG01'],
    ['   ', 'MSG01'],
    ['x'.repeat(101), 'MSG02'],
  ] as const)(
    'AC-003.14 rejects invalid Name %j with %s and sends no request',
    async (value, code) => {
      const submitted = vi.fn();
      server.use(
        getCreateWikiFolderMockHandler(() => {
          submitted();
          return folders[0] ?? { folderId: ROOT, name: 'Guides', parentId: null, level: 1 };
        }),
      );
      const { user, dialog, name } = await openFolder();
      if (value) {
        await user.click(name);
        await user.paste(value);
      }
      await user.click(within(dialog).getByRole('button', { name: 'Save' }));
      expect(
        await within(dialog).findByText(msg(code, { field_name: 'Name', max_length: 100 })),
      ).toBeInTheDocument();
      expect(name).toHaveAttribute('aria-invalid', 'true');
      expect(name).toHaveValue(value);
      expect(submitted).not.toHaveBeenCalled();
    },
  );

  it('AC-003.15 creates a 100-character name under the chosen parent, shows MSG05, closes and refreshes', async () => {
    const submitted = vi.fn();
    server.use(
      getCreateWikiFolderMockHandler(async ({ request, params }) => {
        const data: unknown = await request.json();
        submitted(params.spaceId, data);
        const created = {
          folderId: '00000000-0000-4000-8000-0000000000f5',
          parentId: CHILD,
          name: 'x'.repeat(100),
          level: 3,
        };
        folders = [...folders, created];
        return created;
      }),
    );
    const { user, dialog, name } = await openFolder();
    await user.click(name);
    await user.paste('x'.repeat(100));
    const choices = await parentChoices(user, dialog);
    await user.click(choices.getByText('Network'));
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(
      await within(screen.getByRole('tree', { name: 'Folders' })).findByText('x'.repeat(100)),
    ).toBeInTheDocument();
    expect(submitted).toHaveBeenCalledExactlyOnceWith(SPACE, {
      name: 'x'.repeat(100),
      parentId: CHILD,
    });
  });

  it.each(['Rename', 'Move'])(
    'AC-003.15 %s submits the existing folder id, name and parent and refreshes after 200 (BUG-263)',
    async (action) => {
      const submitted = vi.fn();
      server.use(
        getUpdateWikiFolderMockHandler(async ({ request, params }) => {
          submitted(params.folderId, await request.json());
          const updated = {
            folderId: CHILD,
            parentId: action === 'Move' ? null : ROOT,
            name: action === 'Rename' ? 'Connections' : 'Network',
            level: action === 'Move' ? 1 : 2,
          };
          folders = folders.map((folder) => (folder.folderId === CHILD ? updated : folder));
          return updated;
        }),
      );
      const { user, dialog, name } = await openFolder(action, 'Network');
      if (action === 'Rename') {
        await user.clear(name);
        await user.paste('Connections');
      } else {
        const choices = await parentChoices(user, dialog);
        await user.click(choices.getByText('Top level'));
      }
      await user.click(within(dialog).getByRole('button', { name: 'Save' }));
      expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
      expect(submitted).toHaveBeenCalledExactlyOnceWith(CHILD, {
        name: action === 'Rename' ? 'Connections' : 'Network',
        parentId: action === 'Move' ? null : ROOT,
      });
      const row = await within(screen.getByRole('tree', { name: 'Folders' })).findByText(
        action === 'Rename' ? 'Connections' : 'Network',
      );
      const item = row.closest('[role="treeitem"]');
      if (!(item instanceof HTMLElement)) throw new Error('Folder row missing');
      await user.click(
        within(item).getByRole('button', {
          name: `Move ${action === 'Rename' ? 'Connections' : 'Network'}`,
        }),
      );
      const reopened = await screen.findByRole('dialog', { name: 'Folder' });
      expect(within(reopened).getByRole('textbox', { name: 'Name' })).toHaveValue(
        action === 'Rename' ? 'Connections' : 'Network',
      );
      expect(
        within(reopened).getByText(action === 'Move' ? 'Top level' : 'Guides'),
      ).toBeInTheDocument();
    },
  );

  it.each([
    ['MSG85', 409, 'name'],
    ['MSG86', 400, 'parentId'],
  ] as const)(
    'AC-003.15 shows %s inside the popup and preserves input for retry',
    async (code, status, field) => {
      server.use(
        http.post('*/api/v1/wiki/spaces/:spaceId/folders', () =>
          problem(status, code, {
            fieldErrors: [{ field, code }],
          }),
        ),
      );
      const { user, dialog, name } = await openFolder();
      await user.click(name);
      await user.paste('New guide');
      await user.click(within(dialog).getByRole('button', { name: 'Save' }));
      expect(await within(dialog).findByText(msg(code))).toBeInTheDocument();
      expect(name).toHaveValue('New guide');
      expect(screen.getByRole('dialog')).toBe(dialog);
      const created = { folderId: OTHER, parentId: null, name: 'New guide', level: 1 };
      server.use(getCreateWikiFolderMockHandler(created));
      await user.clear(name);
      await user.paste('Another guide');
      await user.click(within(dialog).getByRole('button', { name: 'Save' }));
      expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    },
  );

  it.each(['New folder', 'Move'])(
    'AC-003.15 clears MSG86 when the parent changes in %s and preserves Name (BUG-268)',
    async (action) => {
      const submitted = vi.fn(() => problem(400, 'MSG86'));
      server.use(
        http.post('*/api/v1/wiki/spaces/:spaceId/folders', submitted),
        http.put('*/api/v1/wiki/folders/:folderId', submitted),
      );
      const { user, dialog, name } = await openFolder(action);
      await user.clear(name);
      await user.paste('Keep this name');
      await user.click(within(dialog).getByRole('button', { name: 'Save' }));
      expect(await within(dialog).findByText(msg('MSG86'))).toBeInTheDocument();
      const choices = await parentChoices(user, dialog);
      await user.click(choices.getByText('Procedures'));
      await waitFor(() => expect(within(dialog).queryByText(msg('MSG86'))).not.toBeInTheDocument());
      expect(name).toHaveValue('Keep this name');
      expect(screen.getByRole('dialog')).toBe(dialog);
      expect(submitted).toHaveBeenCalledTimes(1);
    },
  );

  it('AC-003.16 keeps the folder name and cancel behavior when extracting DeleteFolderModal (BUG-267)', async () => {
    const user = userEvent.setup();
    const submitted = vi.fn(() => new Response(null, { status: 204 }));
    server.use(http.delete('*/api/v1/wiki/folders/:folderId', submitted));
    renderApp(`${paths.wikiHome}?space=${SPACE}`, { role: 'KNOWLEDGE_MANAGER' });
    const tree = await screen.findByRole('tree', { name: 'Folders' }, { timeout: 5000 });
    const row = within(tree).getByText('Guides').closest('[role="treeitem"]');
    if (!(row instanceof HTMLElement)) throw new Error('Folder row missing');
    await user.click(within(row).getByRole('button', { name: 'Delete Guides' }));
    const dialog = await screen.findByRole('dialog', { name: 'Delete' });
    expect(within(dialog).getByText('Guides')).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(submitted).not.toHaveBeenCalled();
    expect(within(tree).getByText('Guides')).toBeInTheDocument();
  });

  it('AC-003.15 disables Save and prevents duplicate submissions while pending', async () => {
    let release = () => {};
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    const submitted = vi.fn();
    server.use(
      getCreateWikiFolderMockHandler(async () => {
        submitted();
        await pending;
        return { folderId: OTHER, parentId: null, name: 'New guide', level: 1 };
      }),
    );
    const { user, dialog, name } = await openFolder();
    await user.click(name);
    await user.paste('New guide');
    const save = within(dialog).getByRole('button', { name: 'Save' });
    try {
      await user.click(save);
      await waitFor(() => expect(save).toBeDisabled());
      await user.click(save);
      await user.keyboard('{Enter}');
      expect(submitted).toHaveBeenCalledTimes(1);
    } finally {
      await act(async () => {
        release();
        await pending;
      });
    }
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
  });

  it('AC-003.15 persists create, rename and move through the feature mocks and AC-003.16 deletes the refreshed folder (BUG-264, BUG-316)', async () => {
    server.resetHandlers();
    const { user, dialog, name } = await openFolder();
    await user.click(name);
    await user.paste('Operations');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    async function openAction(label: string, action: string) {
      const tree = screen.getByRole('tree', { name: 'Folders' });
      const text = await within(tree).findByText(label);
      const row = text.closest('[role="treeitem"]');
      if (!(row instanceof HTMLElement)) throw new Error('Folder row missing');
      await user.click(within(row).getByRole('button', { name: `${action} ${label}` }));
      return screen.findByRole('dialog', { name: action === 'Delete' ? 'Delete' : 'Folder' });
    }
    const rename = await openAction('Operations', 'Rename');
    const renamedName = within(rename).getByRole('textbox', { name: 'Name' });
    await user.clear(renamedName);
    await user.paste('Support');
    await user.click(within(rename).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    const move = await openAction('Support', 'Move');
    const choices = await parentChoices(user, move);
    await user.click(choices.getByText('Network'));
    await user.click(within(move).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    const moved = await openAction('Support', 'Move');
    expect(within(moved).getByText('Network')).toBeInTheDocument();
    await user.click(within(moved).getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    const deletion = await openAction('Support', 'Delete');
    await user.click(within(deletion).getByRole('button', { name: 'Delete' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await waitFor(() =>
      expect(screen.getByRole('tree', { name: 'Folders' })).not.toHaveTextContent('Support'),
    );
    expect(screen.getAllByText(msg('MSG05')).length).toBeGreaterThan(0);
  });
});
