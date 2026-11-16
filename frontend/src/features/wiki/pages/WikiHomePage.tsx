/** 11.1 Wiki Home — SRS III.11.1, Report3 UC-44. */
import { Button, Col, Flex, Input, Row, Space, Typography } from 'antd';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { ErrorState, FormField, LoadingState, SectionCard } from '@/shared/ui';
import { CompanyWideView } from '../components/CompanyWideView';
import { DocumentsTable } from '../components/DocumentsTable';
import { FolderTree } from '../components/FolderTree';
import { useWikiHome, WIKI_QUERY_MAX } from '../hooks/useWikiHome';

export function WikiHomePage() {
  const navigate = useNavigate();
  const home = useWikiHome();
  const isManager = home.selectedSpace?.accessLevel === 'KNOWLEDGE_MANAGER';
  const uploadParams = new URLSearchParams({ space: home.spaceId });
  if (home.folderId) uploadParams.set('folder', home.folderId);

  return (
    <Flex vertical gap="large">
      <CompanyWideView
        documents={home.companyWide.data}
        isLoading={home.companyWide.isPending}
        error={home.companyWide.isError ? home.companyWide.error : null}
        onRetry={() => void home.companyWide.refetch()}
      />
      {home.spaces.isPending ? (
        <LoadingState />
      ) : home.spaces.isError ? (
        <ErrorState error={home.spaces.error} onRetry={() => void home.spaces.refetch()} />
      ) : (
        home.spaces.data.length > 0 && (
          <section aria-label="Spaces">
            <SectionCard title="Spaces">
              <Flex gap="large" wrap>
                {home.spaces.data.map((space) => (
                  <Space key={space.spaceId}>
                    <Link
                      to={{
                        pathname: paths.wikiHome,
                        search: new URLSearchParams({
                          space: space.spaceId,
                          ...(home.q ? { q: home.q } : {}),
                        }).toString(),
                      }}
                      aria-current={space.spaceId === home.spaceId ? 'page' : undefined}
                    >
                      {space.name}
                    </Link>
                    <Typography.Text type="secondary">
                      {space.accessLevel === 'KNOWLEDGE_MANAGER' ? 'Knowledge Manager' : 'View'}
                    </Typography.Text>
                  </Space>
                ))}
              </Flex>
            </SectionCard>
          </section>
        )
      )}
      <WikiSearch key={home.q} q={home.q} onSearch={home.search} />
      {home.isSearching && (
        <section aria-label="Search results">
          <SectionCard title="Search results">
            <DocumentsTable
              label="Search results"
              rows={home.results.data}
              isLoading={home.results.isPending}
              error={home.results.isError ? home.results.error : null}
              onRetry={() => void home.results.refetch()}
            />
          </SectionCard>
        </section>
      )}
      {home.selectedSpace && (
        <SectionCard title={home.selectedSpace.name}>
          <Flex vertical gap="middle">
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
                <Button disabled>New folder</Button>
                <Button onClick={() => void navigate(paths.spaceSettings(home.spaceId))}>
                  Space Settings
                </Button>
              </Space>
            )}
            <Row gutter={24}>
              <Col span={6}>
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
                  />
                )}
              </Col>
              <Col span={18}>
                <DocumentsTable
                  rows={home.documents.data}
                  isLoading={home.documents.isPending}
                  error={home.documents.isError ? home.documents.error : null}
                  onRetry={() => void home.documents.refetch()}
                />
              </Col>
            </Row>
          </Flex>
        </SectionCard>
      )}
    </Flex>
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
