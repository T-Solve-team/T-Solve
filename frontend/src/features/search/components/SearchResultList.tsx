import { Flex, Pagination } from 'antd';
import type { ApiError, TicketSearchResult } from '@/shared/api';
import { PAGE_SIZE } from '@/shared/lib';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui';
import { SearchResultCard } from './SearchResultCard';

type SearchResultListProps = {
  results: TicketSearchResult[] | undefined;
  total: number;
  isLoading: boolean;
  error: ApiError | null;
  onRetry: () => void;
  page: number;
  onPageChange: (page: number) => void;
};

/** The results area of the Search: loading, error with Try again, MSG04, or the cards and their pages. */
export function SearchResultList({
  results,
  total,
  isLoading,
  error,
  onRetry,
  page,
  onPageChange,
}: SearchResultListProps) {
  if (isLoading) return <LoadingState />;
  if (error) return <ErrorState error={error} onRetry={onRetry} />;
  if (!results?.length) return <EmptyState />;
  return (
    <Flex vertical gap="middle">
      {results.map((result) => (
        <SearchResultCard key={result.ticketId} result={result} />
      ))}
      <Pagination
        align="end"
        current={page}
        pageSize={PAGE_SIZE}
        total={total}
        onChange={onPageChange}
        showSizeChanger={false}
        showTotal={(count) => `Total ${count} items`}
      />
    </Flex>
  );
}
