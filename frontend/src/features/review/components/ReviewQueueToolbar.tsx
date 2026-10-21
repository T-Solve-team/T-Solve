import { Button, Flex, Select, Typography } from 'antd';
import type { ProjectMembership } from '@/shared/api';
import styles from './ReviewQueueToolbar.module.css';

type ReviewQueueToolbarProps = {
  projects: ProjectMembership[];
  projectId: number | undefined;
  onProjectChange: (projectId: number | undefined) => void;
  selectedCount: number;
  isBusy: boolean;
  onReject: () => void;
  onApprove: () => void;
};

const ALL = 'ALL';

/**
 * Report 3 queue: Project filter, selection count and Approve/Reject actions.
 */
export function ReviewQueueToolbar({
  projects,
  projectId,
  onProjectChange,
  selectedCount,
  isBusy,
  onReject,
  onApprove,
}: ReviewQueueToolbarProps) {
  const hasSelection = selectedCount > 0;
  return (
    <Flex justify="space-between" align="end" gap="middle" wrap>
      <Flex align="end" gap="middle">
        <Flex vertical gap="small">
          <label htmlFor="review-queue-project">Project</label>
          <Select<number | typeof ALL>
            id="review-queue-project"
            className={styles.project}
            value={projectId ?? ALL}
            onChange={(value) => onProjectChange(value === ALL ? undefined : value)}
            options={[
              { value: ALL, label: 'All Projects' },
              ...projects.map((project) => ({
                value: project.projectId,
                label: project.jiraProjectKey,
              })),
            ]}
          />
        </Flex>
        <Typography.Text type="secondary" className={styles.count}>
          {selectedCount} selected
        </Typography.Text>
      </Flex>
      <Flex gap="middle">
        <Button disabled={!hasSelection || isBusy} onClick={onReject}>
          Reject
        </Button>
        <Button type="primary" disabled={!hasSelection || isBusy} onClick={onApprove}>
          Approve
        </Button>
      </Flex>
    </Flex>
  );
}
