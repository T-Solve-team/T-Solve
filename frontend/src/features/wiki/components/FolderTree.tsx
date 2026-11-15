import { Button, Space, Tree, type TreeDataNode } from 'antd';
import { useState } from 'react';
import type { WikiFolder } from '@/shared/api';
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
  function children(parentId: string | null): TreeDataNode[] {
    return folders
      .filter((folder) => folder.parentId === parentId)
      .sort((a, b) => a.name.localeCompare(b.name))
      .map((folder) => ({
        key: folder.folderId,
        title: (
          <Space>
            <span>{folder.name}</span>
            {managedSpaceId && (
              <Space onClick={(event) => event.stopPropagation()}>
                {(['Rename', 'Move', 'Delete'] as const).map((label) => (
                  <Button
                    key={label}
                    type="text"
                    size="small"
                    danger={label === 'Delete'}
                    onClick={() =>
                      setAction({
                        kind: label === 'Delete' ? 'delete' : 'edit',
                        folderId: folder.folderId,
                      })
                    }
                  >
                    {label}
                  </Button>
                ))}
              </Space>
            )}
          </Space>
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
