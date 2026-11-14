/** 3.2 Project Detail — SRS III.3.2, UC-15 (Members tab; the Settings tab comes with KAN-127) */
import { Button, Flex, Tabs } from 'antd';
import { useState } from 'react';
import { useParams } from 'react-router';
import { ApiError, useGetProject, useListProjectMembers } from '@/shared/api';
import { ErrorState, LoadingState } from '@/shared/ui';
import { AddMemberModal } from '../components/AddMemberModal';
import { MembersTable } from '../components/MembersTable';
import { ProjectHeader } from '../components/ProjectHeader';

/** AC-001.6: 403 → MSG08, 404 → MSG04, anything else → MSG06. */
function screenError(error: ApiError): ApiError {
  const code = error.status === 403 ? 'MSG08' : error.status === 404 ? 'MSG04' : 'MSG06';
  return new ApiError({ status: error.status, code });
}

export function ProjectDetailPage() {
  const { projectId = '' } = useParams();
  const project = useGetProject(projectId);
  const members = useListProjectMembers(projectId);
  const [isAdding, setIsAdding] = useState(false);

  const error = project.error ?? members.error;
  if (error)
    return (
      <ErrorState
        error={screenError(error)}
        onRetry={() => {
          void project.refetch();
          void members.refetch();
        }}
      />
    );
  if (!project.data || !members.data) return <LoadingState />;

  return (
    <Flex vertical gap="middle">
      <ProjectHeader project={project.data} />
      <Tabs
        items={[
          {
            key: 'members',
            label: 'Members',
            children: (
              <Flex vertical gap="middle">
                {project.data.status === 'ACTIVE' && (
                  <Flex justify="end">
                    <Button type="primary" onClick={() => setIsAdding(true)}>
                      Add member
                    </Button>
                  </Flex>
                )}
                <MembersTable members={members.data} />
              </Flex>
            ),
          },
        ]}
      />
      {isAdding && <AddMemberModal projectId={projectId} onClose={() => setIsAdding(false)} />}
    </Flex>
  );
}

export default ProjectDetailPage;
