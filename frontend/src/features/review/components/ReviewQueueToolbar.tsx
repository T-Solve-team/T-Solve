import { Button, Flex, Select, Typography } from 'antd';
import styles from './ReviewQueueToolbar.module.css';

type ReviewQueueToolbarProps = {
  departments: { departmentId: string; name: string }[];
  departmentId: string | undefined;
  onDepartmentChange: (departmentId: string | undefined) => void;
  selectedCount: number;
  onRequestChanges: () => void;
  onReject: () => void;
  onApprove: () => void;
};

const ALL = 'ALL';

/**
 * Figma 7.1 toolbar: Department filter (callout 1) and the selection count on the left; Request Changes (6),
 * Reject (5) and Approve (4) on the right, enabled once a ticket is selected.
 */
export function ReviewQueueToolbar({
  departments,
  departmentId,
  onDepartmentChange,
  selectedCount,
  onRequestChanges,
  onReject,
  onApprove,
}: ReviewQueueToolbarProps) {
  const hasSelection = selectedCount > 0;
  return (
    <Flex justify="space-between" align="end" gap="middle" wrap>
      <Flex align="end" gap="middle">
        <Flex vertical gap="small">
          <label htmlFor="review-queue-department">Department</label>
          <Select<string>
            id="review-queue-department"
            className={styles.department}
            value={departmentId ?? ALL}
            onChange={(value) => onDepartmentChange(value === ALL ? undefined : value)}
            options={[
              { value: ALL, label: 'All Departments' },
              ...departments.map((d) => ({ value: d.departmentId, label: d.name })),
            ]}
          />
        </Flex>
        <Typography.Text type="secondary" className={styles.count}>
          {selectedCount} selected
        </Typography.Text>
      </Flex>
      <Flex gap="middle">
        <Button disabled={!hasSelection} onClick={onRequestChanges}>
          Request Changes
        </Button>
        <Button disabled={!hasSelection} onClick={onReject}>
          Reject
        </Button>
        <Button type="primary" disabled={!hasSelection} onClick={onApprove}>
          Approve
        </Button>
      </Flex>
    </Flex>
  );
}
