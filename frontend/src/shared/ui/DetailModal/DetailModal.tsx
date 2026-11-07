import { Button, Modal } from 'antd';
import type { ReactNode } from 'react';
import { ModalTitle } from '../ModalTitle';

type DetailModalProps = {
  isOpen: boolean;
  /** The SRS popup name, e.g. "Import Ticket Preview". */
  title: string;
  /** Figma frame width; antd's default is 520 (guideline 07, decision FE-35: the screen wins). */
  width?: number;
  onClose: () => void;
  children: ReactNode;
};

/**
 * A read-only popup (D15): Figma "Web/Modal Header" (Form kind) + content + one Close button.
 * For a popup that submits something, use FormModal.
 */
export function DetailModal({ isOpen, title, width, onClose, children }: DetailModalProps) {
  return (
    <Modal
      open={isOpen}
      centered
      title={<ModalTitle kind="form">{title}</ModalTitle>}
      width={width}
      onCancel={onClose}
      footer={<Button onClick={onClose}>Close</Button>}
      destroyOnHidden
    >
      {children}
    </Modal>
  );
}
