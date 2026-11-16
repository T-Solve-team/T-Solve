import { Form, Typography } from 'antd';
import { useRef } from 'react';
import type { WikiFolder } from '@/shared/api';
import { ErrorState, FormModal } from '@/shared/ui';
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
      {remove.error && <ErrorState error={remove.error} />}
    </FormModal>
  );
}
