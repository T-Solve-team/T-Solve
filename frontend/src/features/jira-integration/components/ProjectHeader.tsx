import { Descriptions } from 'antd';
import type { ProjectSummaryResponse } from '@/shared/api';
import { SectionCard, StatusTag } from '@/shared/ui';

/** 3.2 Project Detail — read-only header (AC-001.5). */
export function ProjectHeader({ project }: { project: ProjectSummaryResponse }) {
  return (
    <SectionCard>
      <Descriptions
        column={2}
        items={[
          { key: 'key', label: 'Jira project key', children: project.jiraProjectKey },
          { key: 'department', label: 'Department', children: project.departmentName },
          { key: 'manager', label: 'Project Manager', children: project.projectManagerName ?? '' },
          { key: 'status', label: 'Status', children: <StatusTag status={project.status} /> },
        ]}
      />
    </SectionCard>
  );
}
