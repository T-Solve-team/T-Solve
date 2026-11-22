import { Alert, Form, Typography } from 'antd';
import { useRef } from 'react';
import type { WikiFolder } from '@/shared/api';
import { msg } from '@/shared/messages';
import { FormModal } from '@/shared/ui';
import { useWikiFolderMutations } from '../hooks/useWikiFolderMutations';

type DeleteFolderModalProps = {
  spaceId: string;
  folder: WikiFolder;
  onClose: () => void;
  onDeleted: () => void;
};

export function DeleteFolderModal({ spaceId, folder, onClose, onDeleted }: DeleteFolderModalProps) {
  const [form] = Form.useForm();
  const { remove } = useWikiFolderMutations(spaceId);
  const submitting = useRef(false);
  return (
    <FormModal
      isOpen
      title="Delete"
      kind="danger"
      form={form}
      okText="Delete"
      isOkDanger
      // AC-003.16: MSG87 means the folder is not empty; Delete cannot succeed until the popup reopens.
      isOkDisabled={remove.error?.code === 'MSG87'}
      isSubmitting={remove.isPending}
      onCancel={() => {
        if (!submitting.current) onClose();
      }}
      onSubmit={() => {
        if (submitting.current) return;
        submitting.current = true;
        remove.mutate(
          { folderId: folder.folderId },
          {
            onSuccess: () => {
              onDeleted();
              onClose();
            },
            onSettled: () => {
              submitting.current = false;
            },
          },
        );
      }}
    >
      <Typography.Text>{folder.name}</Typography.Text>
      {remove.error && (
        <Alert type="error" showIcon title={msg(remove.error.code, remove.error.params)} />
      )}
    </FormModal>
  );
}
