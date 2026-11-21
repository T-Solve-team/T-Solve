/** The original file of a Wiki document on 11.2 — wiki AC-002.19, AC-002.26 (decision FE-47). */
import { renderAsync } from 'docx-preview';
import { useEffect, useRef, useState } from 'react';
import { useDownloadWikiDocumentFile } from '@/shared/api';
import { LoadingState } from '@/shared/ui';
import styles from './WikiFileView.module.css';

interface WikiFileViewProps {
  documentId: string;
  fileName: string;
  title: string;
  /** Shown when the file cannot be loaded or rendered (AC-002.26). */
  fallback: React.ReactNode;
}

export function WikiFileView({ documentId, fileName, title, fallback }: WikiFileViewProps) {
  const file = useDownloadWikiDocumentFile(documentId, { query: { retry: false } });
  const isPdf = fileName.toLowerCase().endsWith('.pdf');
  const [failed, setFailed] = useState(false);
  const pdfRef = useRef<HTMLIFrameElement>(null);
  const docxRef = useRef<HTMLDivElement>(null);
  const blob = file.data;

  useEffect(() => {
    if (!blob) return;
    const pdf = pdfRef.current;
    if (pdf) {
      // The browser's own PDF viewer; the Caddy CSP allows frame-src blob: for it.
      const url = URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }));
      pdf.src = url;
      return () => URL.revokeObjectURL(url);
    }
    const docx = docxRef.current;
    if (!docx) return;
    renderAsync(blob, docx, undefined, { inWrapper: true, useBase64URL: true }).catch(() =>
      setFailed(true),
    );
    return () => docx.replaceChildren();
  }, [blob]);

  if (failed || file.isError || (file.isSuccess && !blob)) return fallback;
  if (file.isPending) return <LoadingState />;
  if (isPdf) return <iframe ref={pdfRef} title={title} className={styles.pdf} />;
  return <div ref={docxRef} aria-label={title} className={styles.docx} />;
}
