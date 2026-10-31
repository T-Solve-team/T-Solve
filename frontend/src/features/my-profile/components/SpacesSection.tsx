import { FolderOpenOutlined } from '@ant-design/icons';
import { Flex, Typography } from 'antd';
import type { SpaceRef } from '@/shared/api';
import { SectionCard } from '@/shared/ui';

type SpacesSectionProps = { spaces: readonly SpaceRef[] };

/** "Spaces I manage" for a Knowledge Manager; nothing is shown when there are none. */
export function SpacesSection({ spaces }: SpacesSectionProps) {
  if (!spaces.length) return null;
  return (
    <SectionCard title="Spaces">
      <Typography.Text strong>Spaces I manage</Typography.Text>
      <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
        {spaces.map((space) => (
          <li key={space.spaceId} style={{ minWidth: 0 }}>
            <Flex gap="small" align="center" style={{ minWidth: 0 }}>
              <FolderOpenOutlined aria-hidden="true" />
              <Typography.Text ellipsis title={space.name} style={{ minWidth: 0 }}>
                {space.name}
              </Typography.Text>
            </Flex>
          </li>
        ))}
      </ul>
    </SectionCard>
  );
}
