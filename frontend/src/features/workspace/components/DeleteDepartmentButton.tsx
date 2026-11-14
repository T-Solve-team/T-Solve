import { Button } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import type { DepartmentDetail } from '@/shared/api';
import { paths } from '@/shared/routing';
import { ConfirmDialog } from '@/shared/ui';
import { useRemoveDepartment } from '../hooks/useDepartmentMutations';

type DeleteDepartmentButtonProps = { department: DepartmentDetail };

/** 2.3 Delete button and 2.4 Delete Department popup, UC-14. */
export function DeleteDepartmentButton({ department }: DeleteDepartmentButtonProps) {
  const [isOpen, setIsOpen] = useState(false);
  const remove = useRemoveDepartment();
  const navigate = useNavigate();

  function handleConfirm() {
    if (remove.isPending) return;
    remove.mutate(
      { departmentId: department.departmentId },
      {
        onSuccess: () => void navigate(paths.workspaceSettings),
        onError: () => setIsOpen(false),
      },
    );
  }

  return (
    <>
      <Button danger onClick={() => setIsOpen(true)}>
        Delete
      </Button>
      {isOpen && (
        <ConfirmDialog
          isOpen
          code="MSG45"
          params={{ department_name: department.name, count: department.projectCount }}
          isDanger
          okText="Delete"
          isConfirming={remove.isPending}
          onConfirm={handleConfirm}
          onCancel={() => {
            if (!remove.isPending) setIsOpen(false);
          }}
        />
      )}
    </>
  );
}
