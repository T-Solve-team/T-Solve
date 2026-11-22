import { MoreOutlined } from '@ant-design/icons';
import { Button, Dropdown, Tree, type TreeDataNode } from 'antd';
import { useState } from 'react';
import type { WikiFolder } from '@/shared/api';
import styles from '../pages/WikiHomePage.module.css';
import { DeleteFolderModal } from './DeleteFolderModal';
import { FolderModal } from './FolderModal';

type FolderTreeProps = {
  folders: WikiFolder[];
  folderId: string | undefined;
  onSelect: (folderId: string | undefined) => void;
  managedSpaceId?: string;
};

export function FolderTree({ folders, folderId, onSelect, managedSpaceId }: FolderTreeProps) {
  const [action, setAction] = useState<{ kind: 'edit' | 'delete'; folderId: string } | null>(null);
  const activeFolder = folders.find((folder) => folder.folderId === action?.folderId);
  // ponytail: re-filters all folders per node (O(n²)); fine for dozens, group by parentId if trees grow.
  function children(parentId: string | null): TreeDataNode[] {
    return folders
      .filter((folder) => folder.parentId === parentId)
      .sort((a, b) => a.name.localeCompare(b.name))
      .map((folder) => ({
        key: folder.folderId,
        title: (
          <span className={styles.folderTitle}>
            <span className={styles.folderName} title={folder.name}>
              {folder.name}
            </span>
            {managedSpaceId && (
              // Menu clicks bubble through the portal; keep them from selecting the folder.
              <span onClick={(event) => event.stopPropagation()}>
                <Dropdown
                  trigger={['click']}
                  menu={{
                    items: [
                      { key: 'edit', label: 'Rename' },
                      { key: 'move', label: 'Move' },
                      { key: 'delete', label: 'Delete', danger: true },
                    ],
                    onClick: ({ key }) =>
                      setAction({
                        kind: key === 'delete' ? 'delete' : 'edit',
                        folderId: folder.folderId,
                      }),
                  }}
                >
                  <Button
                    type="text"
                    size="small"
                    icon={<MoreOutlined />}
                    aria-label={`Actions for ${folder.name}`}
                  />
                </Dropdown>
              </span>
            )}
          </span>
        ),
        children: children(folder.folderId),
      }));
  }

  return (
    <>
      <Tree
        aria-label="Folders"
        treeData={[{ key: '', title: 'Top level', children: children(null) }]}
        defaultExpandAll
        selectedKeys={[folderId ?? '']}
        blockNode
        onSelect={(keys) => {
          const key = keys[0];
          if (typeof key === 'string') onSelect(key || undefined);
        }}
      />
      {managedSpaceId && activeFolder && action?.kind === 'edit' && (
        <FolderModal
          spaceId={managedSpaceId}
          folders={folders}
          folderId={activeFolder.folderId}
          onClose={() => setAction(null)}
        />
      )}
      {managedSpaceId && activeFolder && action?.kind === 'delete' && (
        <DeleteFolderModal
          spaceId={managedSpaceId}
          folder={activeFolder}
          onClose={() => setAction(null)}
          onDeleted={() => {
            if (folderId === activeFolder.folderId) onSelect(undefined);
          }}
        />
      )}
    </>
  );
}
