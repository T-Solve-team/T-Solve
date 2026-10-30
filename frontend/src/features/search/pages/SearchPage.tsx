/** 6.1 Search — SRS III.6.1, Figma "6.1 Search" (a)–(c). */
import { Flex } from 'antd';
import { useSession } from '@/shared/auth';
import { SearchFilters } from '../components/SearchFilters';
import { SearchResultList } from '../components/SearchResultList';
import { useTicketSearch } from '../hooks/useTicketSearch';
import styles from './SearchPage.module.css';

export function SearchPage() {
  const { session } = useSession();
  const search = useTicketSearch();
  const { results } = search;

  return (
    <Flex vertical gap="large">
      <SearchFilters
        departments={session?.departments ?? []}
        q={search.q}
        departmentId={search.departmentId}
        source={search.source}
        resolvedFrom={search.resolvedFrom}
        resolvedTo={search.resolvedTo}
        onChange={search.setFilter}
        onResolvedChange={search.setResolved}
      />
      {/* No query: an empty results area with no message (decision Q3, spec AC-002.2). */}
      <section aria-label="Results" className={styles.results}>
        {search.q && (
          <SearchResultList
            results={results.data?.content}
            total={results.data?.page.totalElements ?? 0}
            isLoading={results.isPending}
            error={results.isError ? results.error : null}
            onRetry={() => void results.refetch()}
            page={search.page}
            onPageChange={search.setPage}
          />
        )}
      </section>
    </Flex>
  );
}

export default SearchPage;
