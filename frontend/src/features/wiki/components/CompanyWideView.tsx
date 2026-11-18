import { Flex, Typography } from 'antd';
import type { ApiError, WikiDocumentSummary } from '@/shared/api';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui';
import { DocumentsTable } from './DocumentsTable';

type CompanyWideViewProps = {
  documents: WikiDocumentSummary[] | undefined;
  isLoading: boolean;
  error: ApiError | null;
  onRetry: () => void;
};

export function CompanyWideView({ documents, isLoading, error, onRetry }: CompanyWideViewProps) {
  const groups = new Map<string, WikiDocumentSummary[]>();
  for (const document of [...(documents ?? [])].sort(
    (a, b) => a.spaceName.localeCompare(b.spaceName) || a.title.localeCompare(b.title),
  )) {
    const group = groups.get(document.spaceId) ?? [];
    group.push(document);
    groups.set(document.spaceId, group);
  }

  return (
    <section aria-label="Company-wide">
      <Flex vertical gap="middle">
        <Typography.Title level={2}>Company-wide</Typography.Title>
        {isLoading ? (
          <LoadingState />
        ) : error ? (
          <ErrorState error={error} onRetry={onRetry} />
        ) : groups.size === 0 ? (
          <EmptyState />
        ) : (
          [...groups].map(([spaceId, rows]) => (
            <Flex key={spaceId} vertical gap="small">
              <Typography.Title level={3}>{rows[0]?.spaceName}</Typography.Title>
              <DocumentsTable label={`Company-wide ${rows[0]?.spaceName}`} rows={rows} />
            </Flex>
          ))
        )}
      </Flex>
    </section>
  );
}
