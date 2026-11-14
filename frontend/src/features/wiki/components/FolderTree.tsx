import { Tree, type TreeDataNode } from 'antd';
import type { WikiFolder } from '@/shared/api';

type FolderTreeProps = {
  folders: WikiFolder[];
  folderId: string | undefined;
  onSelect: (folderId: string | undefined) => void;
};

/** Browse only. Folder mutations belong to Task 5. */
export function FolderTree({ folders, folderId, onSelect }: FolderTreeProps) {
  function children(parentId: string | null): TreeDataNode[] {
    return folders
      .filter((folder) => folder.parentId === parentId)
      .sort((a, b) => a.name.localeCompare(b.name))
      .map((folder) => ({
        key: folder.folderId,
        title: folder.name,
        children: children(folder.folderId),
      }));
  }

  return (
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
  );
}
