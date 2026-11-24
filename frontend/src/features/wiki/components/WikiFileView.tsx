/**
 * The original file of a Wiki document — on 11.2 (wiki AC-002.19, AC-002.26, decision FE-47) and,
 * as its first page at the pane's width, in the 11.1 preview pane (AC-002.24, AC-002.27).
 */
import { renderAsync } from 'docx-preview';
import { useEffect, useRef, useState } from 'react';
import { useDownloadWikiDocumentFile } from '@/shared/api';
import { LoadingState } from '@/shared/ui';
import styles from './WikiFileView.module.css';

interface WikiFileViewProps {
  documentId: string;
  fileName: string;
  title: string;
  /** Shown when the file cannot be loaded or rendered (AC-002.26, AC-002.27). */
  fallback: React.ReactNode;
  /** `preview`: the first page only, scaled to the container's width (11.1 preview pane). */
  variant?: 'full' | 'preview';
}

export function WikiFileView({
  documentId,
  fileName,
  title,
  fallback,
  variant = 'full',
}: WikiFileViewProps) {
  const file = useDownloadWikiDocumentFile(documentId, { query: { retry: false } });
  const isPdf = fileName.toLowerCase().endsWith('.pdf');
  const isPreview = variant === 'preview';
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
      // view=FitH for Chrome, zoom=page-width for Firefox's pdf.js.
      pdf.src = isPreview ? `${url}#page=1&view=FitH&zoom=page-width&toolbar=0` : url;
      return () => URL.revokeObjectURL(url);
    }
    const docx = docxRef.current;
    if (!docx) return;
    let observer: ResizeObserver | undefined;
    let cancelled = false;
    renderAsync(blob, docx, undefined, {
      inWrapper: true,
      useBase64URL: true,
      // Use the page breaks Word saved, so the first <section> is the first page.
      ignoreLastRenderedPageBreak: !isPreview,
    })
      .then(() => {
        if (cancelled) return;
        // docx-preview copies link targets as is; drop any scheme that could run script.
        for (const a of docx.querySelectorAll('a[href]')) {
          if (/^(https?:|mailto:|#)/i.test(a.getAttribute('href') ?? '')) {
            a.setAttribute('rel', 'noopener noreferrer');
          } else a.removeAttribute('href');
        }
        if (!isPreview) return;
        // docx-preview draws each page as a <section>; keep the first and zoom it to fit.
        const [first, ...rest] = docx.querySelectorAll<HTMLElement>('section');
        for (const page of rest) page.remove();
        if (!first) return;
        // Read the natural width once, before any zoom: a zoomed offsetWidth differs per browser.
        const naturalWidth = first.offsetWidth;
        const fit = () => {
          if (naturalWidth) first.style.zoom = String(Math.min(1, docx.clientWidth / naturalWidth));
        };
        fit();
        observer = new ResizeObserver(fit);
        observer.observe(docx);
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      });
    return () => {
      cancelled = true;
      observer?.disconnect();
      docx.replaceChildren();
    };
  }, [blob, isPreview]);

  if (failed || file.isError || (file.isSuccess && !blob)) return fallback;
  if (file.isPending) return <LoadingState />;
  if (isPdf)
    return (
      <iframe ref={pdfRef} title={title} className={isPreview ? styles.pdfPreview : styles.pdf} />
    );
  return (
    <div
      ref={docxRef}
      aria-label={title}
      className={isPreview ? styles.docxPreview : styles.docx}
    />
  );
}
