import { Modal } from 'antd';
import { msg, type MsgCode, type MsgParams } from '@/shared/messages';
import { ModalTitle } from '../ModalTitle';

type ConfirmDialogProps = {
  isOpen: boolean;
  /** The confirm message (SRS "Confirm dialog" type), e.g. MSG19. */
  code: MsgCode;
  params?: MsgParams;
  /** Figma "Danger confirm": destructive or final actions (guideline 09 §5). */
  isDanger?: boolean;
  okText?: string;
  /** The action is running: OK shows a spinner and cannot be clicked twice. */
  isConfirming?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
};

/** Figma "Web/Modal" Kind=Confirm / Danger confirm — a question before a hard-to-undo action. */
export function ConfirmDialog({
  isOpen,
  code,
  params,
  isDanger = false,
  okText = 'OK',
  isConfirming = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  return (
    <Modal
      open={isOpen}
      centered
      title={<ModalTitle kind={isDanger ? 'danger' : 'confirm'}>{msg(code, params)}</ModalTitle>}
      closable={false}
      okText={okText}
      cancelText="Cancel"
      okButtonProps={{ danger: isDanger }}
      confirmLoading={isConfirming}
      onOk={onConfirm}
      onCancel={onCancel}
    />
  );
}
