import { Modal, type FormInstance } from 'antd';
import type { ReactNode } from 'react';
import { AppForm } from '../AppForm';
import { ModalTitle, type ModalKind } from '../ModalTitle';

type FormModalProps<T> = {
  isOpen: boolean;
  /** The SRS popup name, e.g. "Reject Ticket". */
  title: string;
  /** Figma "Web/Modal Header" kind: Form (default, with close icon) or Danger (red icon, no close icon). */
  kind?: Exclude<ModalKind, 'confirm'>;
  /** Figma frame width; antd's default is 520 (guideline 07, decision FE-35: the screen wins). */
  width?: number;
  form: FormInstance<T>;
  okText: string;
  isOkDanger?: boolean;
  /** Required data is unavailable or the action cannot be submitted yet. */
  isOkDisabled?: boolean;
  /** The request is running: OK shows a spinner and cannot be clicked twice. */
  isSubmitting?: boolean;
  /** Called with valid values only. Keep the popup open on failure — the user's input stays. */
  onSubmit: (values: T) => void;
  onCancel: () => void;
  children: ReactNode;
};

/**
 * A popup with a form (guideline 09 §4): Figma "Web/Modal Header" + fields + "Web/Modal Footer"
 * (Cancel + main action). The form is cleared when the popup closes, so it always reopens empty.
 */
export function FormModal<T>({
  isOpen,
  title,
  kind = 'form',
  width,
  form,
  okText,
  isOkDanger = false,
  isOkDisabled = false,
  isSubmitting = false,
  onSubmit,
  onCancel,
  children,
}: FormModalProps<T>) {
  return (
    <Modal
      open={isOpen}
      centered
      title={<ModalTitle kind={kind}>{title}</ModalTitle>}
      width={width}
      closable={kind === 'form'}
      okText={okText}
      cancelText="Cancel"
      okButtonProps={{
        'aria-label': okText,
        'aria-busy': isSubmitting,
        htmlType: 'submit',
        danger: isOkDanger,
        disabled: isOkDisabled || isSubmitting,
      }}
      confirmLoading={isSubmitting}
      onCancel={onCancel}
      destroyOnHidden
      modalRender={(dialog) => (
        <AppForm<T> form={form} onFinish={onSubmit} clearOnDestroy>
          {dialog}
        </AppForm>
      )}
    >
      {children}
    </Modal>
  );
}
