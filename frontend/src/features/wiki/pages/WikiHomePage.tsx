/** 11.1 Wiki Home — SRS III.11.1, Report3 UC-44. */
import { Button, Flex, Input, Space, Typography } from 'antd';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { ErrorState, FormField, LoadingState } from '@/shared/ui';
import { CompanyWideView } from '../components/CompanyWideView';
import { DocumentsTable } from '../components/DocumentsTable';
import { FolderTree } from '../components/FolderTree';
import { FolderModal } from '../components/FolderModal';
import { useWikiHome, WIKI_QUERY_MAX } from '../hooks/useWikiHome';
import styles from './WikiHomePage.module.css';

export function WikiHomePage() {
  const navigate = useNavigate();
  const home = useWikiHome();
  const [newFolderSpaceId, setNewFolderSpaceId] = useState<string | null>(null);
  const isManager = home.selectedSpace?.accessLevel === 'KNOWLEDGE_MANAGER';
  const uploadParams = new URLSearchParams({ space: home.spaceId });
  if (home.folderId) uploadParams.set('folder', home.folderId);
  const hasNoSpaces = home.spaces.isSuccess && home.spaces.data.length === 0;

  return (
    <div className={hasNoSpaces ? `${styles.explorer} ${styles.noSpaces}` : styles.explorer}>
      <div className={styles.company}>
        <CompanyWideView
          documents={home.companyWide.data}
          isLoading={home.companyWide.isPending}
          error={home.companyWide.isError ? home.companyWide.error : null}
          onRetry={() => void home.companyWide.refetch()}
        />
      </div>
      {!hasNoSpaces && (
        <div className={styles.navigator}>
          {home.spaces.isPending ? (
            <LoadingState />
          ) : home.spaces.isError ? (
            <ErrorState error={home.spaces.error} onRetry={() => void home.spaces.refetch()} />
          ) : (
            <section aria-label="Spaces" className={styles.list}>
              <span role="heading" aria-level={2} className={styles.label}>
                Spaces
              </span>
              {home.spaces.data.map((space) => (
                <Flex key={space.spaceId} vertical gap="small">
                  <Flex vertical>
                    <Link
                      to={{
                        pathname: paths.wikiHome,
                        search: new URLSearchParams({
                          space: space.spaceId,
                          ...(home.q ? { q: home.q } : {}),
                        }).toString(),
                      }}
                      className={styles.entry}
                      aria-current={space.spaceId === home.spaceId ? 'page' : undefined}
                      aria-describedby={`wiki-access-${space.spaceId}`}
                    >
                      {space.name}
                    </Link>
                    <span id={`wiki-access-${space.spaceId}`} className={styles.access}>
                      {space.accessLevel === 'KNOWLEDGE_MANAGER' ? 'Knowledge Manager' : 'View'}
                    </span>
                  </Flex>
                  {space.spaceId === home.selectedSpace?.spaceId && (
                    <div className={styles.folders}>
                      {home.folders.isPending ? (
                        <LoadingState />
                      ) : home.folders.isError ? (
                        <ErrorState
                          error={home.folders.error}
                          onRetry={() => void home.folders.refetch()}
                        />
                      ) : (
                        <FolderTree
                          key={home.spaceId}
                          folders={home.folders.data}
                          folderId={home.folderId}
                          onSelect={home.selectFolder}
                          managedSpaceId={isManager ? home.spaceId : undefined}
                        />
                      )}
                    </div>
                  )}
                </Flex>
              ))}
            </section>
          )}
        </div>
      )}
      <Flex vertical gap="large" className={styles.rest}>
        <Flex justify="space-between" align="end" gap="middle">
          <div className={styles.search}>
            <WikiSearch key={home.q} q={home.q} onSearch={home.search} />
          </div>
          {isManager && (
            <Space>
              <Button
                type="primary"
                onClick={() =>
                  void navigate({ pathname: paths.wikiUpload, search: uploadParams.toString() })
                }
              >
                Upload documents
              </Button>
              <Button
                disabled={!home.folders.isSuccess}
                onClick={() => setNewFolderSpaceId(home.spaceId)}
              >
                New folder
              </Button>
              <Button onClick={() => void navigate(paths.spaceSettings(home.spaceId))}>
                Space Settings
              </Button>
            </Space>
          )}
        </Flex>
        {home.isSearching && (
          <section aria-label="Search results">
            <Flex vertical gap="middle">
              <Typography.Title level={2}>Search results</Typography.Title>
              <DocumentsTable
                label="Search results"
                rows={home.results.data}
                isLoading={home.results.isPending}
                error={home.results.isError ? home.results.error : null}
                onRetry={() => void home.results.refetch()}
              />
            </Flex>
          </section>
        )}
        {home.selectedSpace && (
          <Flex vertical gap="middle">
            <Typography.Title level={2}>{home.selectedSpace.name}</Typography.Title>
            <DocumentsTable
              rows={home.documents.data}
              isLoading={home.documents.isPending}
              error={home.documents.isError ? home.documents.error : null}
              onRetry={() => void home.documents.refetch()}
            />
          </Flex>
        )}
      </Flex>
      {isManager && newFolderSpaceId === home.spaceId && home.folders.isSuccess && (
        <FolderModal
          spaceId={home.spaceId}
          folders={home.folders.data}
          onClose={() => setNewFolderSpaceId(null)}
        />
      )}
    </div>
  );
}

/** Only the unsubmitted draft is local; submitted Search is URL state. */
function WikiSearch({ q, onSearch }: { q: string; onSearch: (value: string) => void }) {
  const [draft, setDraft] = useState(q);
  const isTooLong = draft.length > WIKI_QUERY_MAX;
  return (
    <FormField
      label="Search"
      htmlFor="wiki-search"
      validateStatus={isTooLong ? 'error' : undefined}
      help={
        isTooLong ? msg('MSG02', { field_name: 'Search', max_length: WIKI_QUERY_MAX }) : undefined
      }
    >
      <Input.Search
        id="wiki-search"
        type="search"
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        onSearch={onSearch}
        enterButton="Search"
      />
    </FormField>
  );
}

export default WikiHomePage;
