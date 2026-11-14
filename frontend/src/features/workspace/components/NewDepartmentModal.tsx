import { Form, Input } from 'antd';
import { useRef } from 'react';
import type { CreateDepartmentRequest } from '@/shared/api';
import { applyApiErrors, rules } from '@/shared/forms';
import { showMessage } from '@/shared/messages';
import { FormField, FormModal } from '@/shared/ui';
import { useAddDepartment } from '../hooks/useDepartmentMutations';

type DepartmentFormValues = Pick<CreateDepartmentRequest, 'name'>;

type NewDepartmentModalProps = {
  isOpen: boolean;
  onClose: () => void;
};

/** 2.2 Workspace Settings — New Department popup, UC-12. */
export function NewDepartmentModal({ isOpen, onClose }: NewDepartmentModalProps) {
  const [form] = Form.useForm<DepartmentFormValues>();
  const create = useAddDepartment();
  const submitting = useRef(false);

  function handleSubmit(values: DepartmentFormValues) {
    if (submitting.current) return;
    submitting.current = true;
    create.mutate(
      { data: { name: values.name.trim() } },
      {
        onSuccess: onClose,
        onSettled: () => {
          submitting.current = false;
        },
        onError: (error) => {
          if (!applyApiErrors(form, error)) showMessage(error.code, error.params);
        },
      },
    );
  }

  return (
    <FormModal<DepartmentFormValues>
      isOpen={isOpen}
      title="New Department"
      form={form}
      okText="Save"
      isSubmitting={create.isPending}
      onSubmit={handleSubmit}
      onCancel={() => {
        if (!submitting.current) onClose();
      }}
    >
      <FormField<DepartmentFormValues>
        name="name"
        label="Department name"
        rules={[rules.required('Department name'), rules.maxLength('Department name', 50)]}
      >
        <Input count={{ show: true, max: 50 }} />
      </FormField>
    </FormModal>
  );
}
