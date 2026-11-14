import type { DepartmentSummary } from '@/shared/api';
import { ConfirmDialog } from '@/shared/ui';
import { useRestoreDepartmentAction } from '../hooks/useDepartmentMutations';

type RestoreDepartmentDialogProps = { department: DepartmentSummary; onClose: () => void };

/** 2.2 Restore confirmation (MSG51), UC-15. Errors go to the app's handler (AC-008.8). */
export function RestoreDepartmentDialog({ department, onClose }: RestoreDepartmentDialogProps) {
  const restore = useRestoreDepartmentAction();

  function handleConfirm() {
    if (restore.isPending) return;
    restore.mutate({ departmentId: department.departmentId }, { onSettled: onClose });
  }

  return (
    <ConfirmDialog
      isOpen
      code="MSG51"
      params={{ department_name: department.name }}
      okText="Restore"
      isConfirming={restore.isPending}
      onConfirm={handleConfirm}
      onCancel={() => {
        if (!restore.isPending) onClose();
      }}
    />
  );
}
