import { FolderOpenOutlined } from '@ant-design/icons';
import { Flex, Typography } from 'antd';
import type { ApiError, SpaceRef } from '@/shared/api';
import { msg } from '@/shared/messages';
import { ErrorState, LoadingState, SectionCard } from '@/shared/ui';

type SpacesSectionProps = {
  managed: readonly SpaceRef[];
  viewable: { spaces: readonly SpaceRef[] | undefined; loading: boolean; error: ApiError | null };
};

function SpaceList({ label, spaces }: { label: string; spaces: readonly SpaceRef[] }) {
  return (
    <Flex vertical gap="small">
      <Typography.Text strong>{label}</Typography.Text>
      <ul aria-label={label} style={{ listStyle: 'none', margin: 0, padding: 0 }}>
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
    </Flex>
  );
}

/** "Spaces I manage" and "Spaces I can view"; the card is always shown (AC-003.15). */
export function SpacesSection({ managed, viewable }: SpacesSectionProps) {
  const { spaces, loading, error } = viewable;
  return (
    <section aria-label="Spaces">
      <SectionCard title="Spaces">
        <Flex vertical gap="middle">
          {managed.length > 0 && <SpaceList label="Spaces I manage" spaces={managed} />}
          {loading && <LoadingState />}
          {error && <ErrorState error={error} />}
          {/* A failed refetch shows MSG06 instead of the stale list (AC-003.14). */}
          {!error && !!spaces?.length && <SpaceList label="Spaces I can view" spaces={spaces} />}
          {!error && spaces?.length === 0 && (
            <Flex vertical gap="small">
              <Typography.Text strong>Spaces I can view</Typography.Text>
              <Typography.Text type="secondary">{msg('MSG142')}</Typography.Text>
            </Flex>
          )}
        </Flex>
      </SectionCard>
    </section>
  );
}
