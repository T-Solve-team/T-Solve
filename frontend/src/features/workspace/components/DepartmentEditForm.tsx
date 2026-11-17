import { Button, Descriptions, Flex, Form, Input, Select } from 'antd';
import { useEffect, useRef, useState } from 'react';
import type { DepartmentDetail, UpdateDepartmentRequest } from '@/shared/api';
import { applyApiErrors, rules } from '@/shared/forms';
import { showMessage } from '@/shared/messages';
import { AppForm, FormField } from '@/shared/ui';
import { useSaveDepartment } from '../hooks/useDepartmentMutations';

/** "None" is `''` inside the form only; the request sends null (K121, decided in planning). */
type DepartmentFormValues = Pick<UpdateDepartmentRequest, 'name'> & { defaultProjectId: string };

type DepartmentEditFormProps = { department: DepartmentDetail };

/** 2.3 Department Detail — the name and default Project and their inline edit form, UC-13. */
export function DepartmentEditForm({ department }: DepartmentEditFormProps) {
  const [form] = Form.useForm<DepartmentFormValues>();
  const [isEditing, setIsEditing] = useState(false);
  const save = useSaveDepartment();
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
    save.mutate(
      {
        departmentId: department.departmentId,
        data: {
          name: values.name.trim(),
          defaultProjectId: values.defaultProjectId || null,
        },
      },
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
          items={[
            { key: 'name', label: 'Name', children: department.name },
            {
              key: 'defaultProject',
              label: 'Default Project',
              children: department.defaultProject?.jiraProjectKey ?? 'None',
            },
          ]}
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
      initialValues={{
        name: department.name,
        defaultProjectId: department.defaultProject?.projectId ?? '',
      }}
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
      <FormField<DepartmentFormValues>
        name="defaultProjectId"
        label="Default Project"
        extra="Leave it as None for a Department that does not handle support requests. Issues from the Knowledge Chatbot are created in this Project with the type Task."
      >
        <Select
          virtual={false}
          disabled={save.isPending}
          options={[
            { value: '', label: 'None' },
            ...department.projects.map((p) => ({ value: p.projectId, label: p.jiraProjectKey })),
          ]}
        />
      </FormField>
      <Flex gap="small">
        <Button type="primary" htmlType="submit" loading={save.isPending} disabled={save.isPending}>
          Save
        </Button>
        <Button onClick={handleCancel}>Cancel</Button>
      </Flex>
    </AppForm>
  );
}
