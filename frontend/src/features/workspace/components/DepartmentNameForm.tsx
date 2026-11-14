import { Button, Descriptions, Flex, Form, Input } from 'antd';
import { useEffect, useRef, useState } from 'react';
import type { DepartmentRef, UpdateDepartmentRequest } from '@/shared/api';
import { applyApiErrors, rules } from '@/shared/forms';
import { showMessage } from '@/shared/messages';
import { AppForm, FormField } from '@/shared/ui';
import { useRenameDepartment } from '../hooks/useDepartmentMutations';

type DepartmentFormValues = Pick<UpdateDepartmentRequest, 'name'>;

type DepartmentNameFormProps = { department: DepartmentRef };

/** 2.3 Department Detail — the name and its inline rename form, UC-13. */
export function DepartmentNameForm({ department }: DepartmentNameFormProps) {
  const [form] = Form.useForm<DepartmentFormValues>();
  const [isEditing, setIsEditing] = useState(false);
  const rename = useRenameDepartment();
  const isSubmitting = useRef(false);
  const editButtonRef = useRef<HTMLButtonElement>(null);
  const shouldFocusEdit = useRef(false);

  useEffect(() => {
    if (!isEditing && shouldFocusEdit.current) {
      editButtonRef.current?.focus();
      shouldFocusEdit.current = false;
    }
  }, [isEditing]);

  function closeForm() {
    shouldFocusEdit.current = true;
    setIsEditing(false);
  }

  function handleSubmit(values: DepartmentFormValues) {
    if (isSubmitting.current) return;
    isSubmitting.current = true;
    rename.mutate(
      { departmentId: department.departmentId, data: { name: values.name.trim() } },
      {
        onSuccess: closeForm,
        onSettled: () => {
          isSubmitting.current = false;
        },
        onError: (error) => {
          if (!applyApiErrors(form, error)) showMessage(error.code, error.params);
        },
      },
    );
  }

  function handleCancel() {
    if (isSubmitting.current) return;
    form.resetFields();
    closeForm();
  }

  if (!isEditing) {
    return (
      <Flex vertical gap="middle" align="flex-start">
        <Descriptions
          column={1}
          items={[{ key: 'name', label: 'Name', children: department.name }]}
        />
        <Button ref={editButtonRef} onClick={() => setIsEditing(true)}>
          Edit
        </Button>
      </Flex>
    );
  }

  return (
    <AppForm<DepartmentFormValues>
      form={form}
      initialValues={{ name: department.name }}
      clearOnDestroy
      onFinish={handleSubmit}
    >
      <FormField<DepartmentFormValues>
        name="name"
        label="Department name"
        rules={[rules.required('Department name'), rules.maxLength('Department name', 50)]}
      >
        <Input autoFocus count={{ show: true, max: 50 }} />
      </FormField>
      <Flex gap="small">
        <Button
          type="primary"
          htmlType="submit"
          loading={rename.isPending}
          disabled={rename.isPending}
        >
          Save
        </Button>
        <Button onClick={handleCancel}>Cancel</Button>
      </Flex>
    </AppForm>
  );
}
