/** 11.5 Folder (popup) — SRS III.11.5, UC-41 and UC-42. */
import { Alert, Form, Input, TreeSelect, type TreeSelectProps } from 'antd';
import { useRef } from 'react';
import type { UpdateWikiFolderRequest, WikiFolder } from '@/shared/api';
import { applyApiErrors, rules } from '@/shared/forms';
import { msg, showMessage } from '@/shared/messages';
import { FormField, FormModal } from '@/shared/ui';
import { useWikiFolderMutations } from '../hooks/useWikiFolderMutations';

type FolderModalProps = {
  spaceId: string;
  folders: WikiFolder[];
  folderId?: string;
  onClose: () => void;
};

export function FolderModal({ spaceId, folders, folderId, onClose }: FolderModalProps) {
  const [form] = Form.useForm<UpdateWikiFolderRequest>();
  const { create, update } = useWikiFolderMutations(spaceId);
  const submitting = useRef(false);
  const folder = folders.find((item) => item.folderId === folderId);
  const mutation = folderId ? update : create;
  const error = mutation.error;
  const excluded = new Set<string>();
  if (folderId) excluded.add(folderId);
  // Three passes also cover an unordered response containing all three folder levels.
  for (let level = 1; level <= 3; level++) {
    for (const item of folders) {
      if (item.parentId && excluded.has(item.parentId)) excluded.add(item.folderId);
    }
  }
  function children(parentId: string | null): NonNullable<TreeSelectProps['treeData']> {
    return folders
      .filter(
        (item) => item.parentId === parentId && item.level < 3 && !excluded.has(item.folderId),
      )
      .sort((a, b) => a.name.localeCompare(b.name))
      .map((item) => ({
        value: item.folderId,
        title: item.name,
        children: children(item.folderId),
      }));
  }

  function handleSubmit(values: UpdateWikiFolderRequest) {
    if (submitting.current) return;
    submitting.current = true;
    const callbacks = {
      onSuccess: onClose,
      onError: (failure: NonNullable<typeof error>) => {
        if (failure.code !== 'MSG86' && !applyApiErrors(form, failure)) {
          if (failure.code === 'MSG85') form.setFields([{ name: 'name', errors: [msg('MSG85')] }]);
          else showMessage(failure.code, failure.params);
        }
      },
      onSettled: () => {
        submitting.current = false;
      },
    };
    const data = { ...values, parentId: values.parentId || null };
    if (folderId) update.mutate({ folderId, data }, callbacks);
    else create.mutate({ spaceId, data }, callbacks);
  }

  return (
    <FormModal<UpdateWikiFolderRequest>
      isOpen
      title="Folder"
      form={form}
      okText="Save"
      isSubmitting={mutation.isPending}
      onSubmit={handleSubmit}
      onCancel={() => {
        if (!submitting.current) onClose();
      }}
    >
      {error?.code === 'MSG86' && <Alert type="error" showIcon title={msg('MSG86')} />}
      <FormField<UpdateWikiFolderRequest>
        name="name"
        label="Name"
        initialValue={folder?.name ?? ''}
        rules={[rules.required('Name'), rules.maxLength('Name', 100)]}
      >
        <Input count={{ show: true, max: 100 }} />
      </FormField>
      <FormField<UpdateWikiFolderRequest>
        name="parentId"
        label="Parent folder"
        initialValue={folder?.parentId ?? ''}
      >
        <TreeSelect
          treeData={[{ value: '', title: 'Top level', children: children(null) }]}
          treeDefaultExpandAll
          virtual={false}
        />
      </FormField>
    </FormModal>
  );
}
