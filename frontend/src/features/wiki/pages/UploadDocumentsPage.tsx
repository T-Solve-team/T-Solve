/** 11.3 Upload Documents — Report3 UC-48, AC-004.18–AC-004.23. */
import { Alert, Button, Flex, Select, Space, TreeSelect, type TreeSelectProps } from 'antd';
import { useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { ApiError, useListWikiFolders, useListWikiSpaces } from '@/shared/api';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { AppForm, ErrorState, FileDropzone, FormField, LoadingState } from '@/shared/ui';
import { WikiUploadTable, type WikiUploadRow } from '../components/WikiUploadTable';
import { useWikiUpload } from '../hooks/useWikiUpload';

const MAX_FILES = 10;
const MAX_BYTES = 10 * 1024 * 1024;

export function UploadDocumentsPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const spaceId = params.get('space') ?? '';
  const folderId = params.get('folder') ?? null;
  const spaces = useListWikiSpaces();
  const isManager = spaces.data?.some(
    (space) => space.spaceId === spaceId && space.accessLevel === 'KNOWLEDGE_MANAGER',
  );
  const folders = useListWikiFolders(spaceId, { query: { enabled: isManager === true } });
  const { extract, publish } = useWikiUpload(spaceId);
  const [rows, setRows] = useState<WikiUploadRow[]>([]);
  const [visibility, setVisibility] = useState<WikiUploadRow['visibility']>('DEPARTMENT');
  const [errors, setErrors] = useState<string[]>([]);
  const busy = useRef(false);
  const isBusy = extract.isPending || publish.isPending;
  const canPublish =
    rows.length > 0 &&
    rows.every((row) => row.result === 'READY' && row.title.trim() && row.title.length <= 200);

  function returnHome() {
    const search = new URLSearchParams({ space: spaceId });
    if (folderId) search.set('folder', folderId);
    void navigate({ pathname: paths.wikiHome, search: search.toString() });
  }

  function addFiles(files: File[]) {
    if (busy.current) return;
    if (rows.length + files.length > MAX_FILES) {
      setErrors([msg('MSG88')]);
      return;
    }
    const invalid = files.filter(
      (file) => !/\.(pdf|docx)$/i.test(file.name) || file.size > MAX_BYTES,
    );
    setErrors(invalid.map((file) => msg('MSG89', { file_name: file.name })));
    const accepted = files.filter((file) => !invalid.includes(file));
    if (!accepted.length) return;
    busy.current = true;
    extract.mutate(
      { spaceId, data: { files: accepted } },
      {
        onSuccess: (previews) => {
          const drafts = previews.flatMap((preview, index): WikiUploadRow[] => {
            const file = accepted[index];
            return file ? [{ ...preview, id: crypto.randomUUID(), file, visibility }] : [];
          });
          setRows((current) => [...current, ...drafts]);
        },
        onError: (error) => setErrors((current) => [...current, msg(error.code, error.params)]),
        onSettled: () => {
          busy.current = false;
        },
      },
    );
  }

  function publishFiles() {
    if (busy.current || !canPublish) return;
    busy.current = true;
    setErrors([]);
    setRows((current) => current.map((row) => ({ ...row, titleErrors: undefined })));
    publish.mutate(
      {
        spaceId,
        data: {
          files: rows.map((row) => row.file),
          metadata: {
            folderId,
            documents: rows.map(({ title, visibility }) => ({ title, visibility })),
          },
        },
      },
      {
        onSuccess: returnHome,
        onError: (error) => {
          const mapped = error.fieldErrors.flatMap((fieldError) => {
            const match = /^documents\[(\d+)\]\.title$/.exec(fieldError.field);
            return match?.[1] && rows[Number(match[1])]
              ? [
                  {
                    index: Number(match[1]),
                    // The server names the field by its path (documents[0].title); show the label.
                    text: msg(fieldError.code, { ...fieldError.params, field_name: 'Title' }),
                  },
                ]
              : [];
          });
          setRows((current) =>
            current.map((row, index) => ({
              ...row,
              titleErrors: mapped.filter((item) => item.index === index).map((item) => item.text),
            })),
          );
          if (!mapped.length) setErrors([msg(error.code, error.params)]);
        },
        onSettled: () => {
          busy.current = false;
        },
      },
    );
  }

  if (spaces.isPending) return <LoadingState />;
  if (spaces.isError)
    return <ErrorState error={spaces.error} onRetry={() => void spaces.refetch()} />;
  if (!isManager) return <ErrorState error={new ApiError({ status: 403, code: 'MSG08' })} />;
  if (folders.isPending) return <LoadingState />;
  if (folders.isError)
    return <ErrorState error={folders.error} onRetry={() => void folders.refetch()} />;
  if (folderId && !folders.data.some((folder) => folder.folderId === folderId))
    return <ErrorState error={new ApiError({ status: 404, code: 'MSG04' })} />;
  function children(parentId: string | null): NonNullable<TreeSelectProps['treeData']> {
    return (folders.data ?? [])
      .filter((folder) => folder.parentId === parentId)
      .map((folder) => ({
        value: folder.folderId,
        title: folder.name,
        children: children(folder.folderId),
      }));
  }

  return (
    <AppForm>
      <Flex vertical gap="middle">
        <Space align="start">
          <FormField label="Folder" htmlFor="wiki-folder">
            <TreeSelect
              id="wiki-folder"
              value={folderId ?? ''}
              disabled={isBusy}
              treeDefaultExpandAll
              virtual={false}
              treeData={[{ value: '', title: 'Top level', children: children(null) }]}
              onChange={(folder: string) =>
                setParams((current) => {
                  const next = new URLSearchParams(current);
                  if (folder) next.set('folder', folder);
                  else next.delete('folder');
                  return next;
                })
              }
            />
          </FormField>
          <FormField label="Visibility" htmlFor="wiki-visibility">
            <Select
              id="wiki-visibility"
              virtual={false}
              value={visibility}
              options={[
                { value: 'DEPARTMENT', label: 'Department' },
                { value: 'COMPANY_WIDE', label: 'Company-wide' },
              ]}
              disabled={isBusy}
              onChange={setVisibility}
            />
          </FormField>
        </Space>
        <FileDropzone
          label="Files"
          accept=".pdf,.docx"
          hint="PDF or DOCX, up to 10 files, at most 10 MB each."
          disabled={isBusy}
          onAdd={addFiles}
        />
        {errors.map((error) => (
          <Alert key={error} type="error" showIcon title={error} />
        ))}
        {extract.isPending && <LoadingState />}
        <WikiUploadTable
          rows={rows}
          isBusy={isBusy}
          onChange={(id, change) =>
            setRows((current) =>
              current.map((row) =>
                row.id === id ? { ...row, ...change, titleErrors: undefined } : row,
              ),
            )
          }
          onRemove={(id) => setRows((current) => current.filter((row) => row.id !== id))}
        />
        <Space>
          <Button
            type="primary"
            disabled={!canPublish || isBusy}
            loading={publish.isPending}
            onClick={publishFiles}
          >
            Publish
          </Button>
          <Button disabled={publish.isPending} onClick={returnHome}>
            Cancel
          </Button>
        </Space>
      </Flex>
    </AppForm>
  );
}

export default UploadDocumentsPage;
