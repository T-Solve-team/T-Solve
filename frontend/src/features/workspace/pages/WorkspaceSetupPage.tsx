/** 2.1 Workspace Setup — Report3 UC-10 (MSG60); SRS v3460 layout */
import { Alert, Flex } from 'antd';
import { msg } from '@/shared/messages';
import { WorkspaceSetupForm } from '../components/WorkspaceSetupForm';

export function WorkspaceSetupPage() {
  return (
    <Flex vertical gap="large">
      <Alert type="info" showIcon title={msg('MSG60')} />
      <WorkspaceSetupForm />
    </Flex>
  );
}

export default WorkspaceSetupPage;
