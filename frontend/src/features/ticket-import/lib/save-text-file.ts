/** Lets the browser save `text` as a file named `fileName` (a Blob URL on a hidden link, revoked after the click). */
export function saveTextFile(text: string, fileName: string): void {
  const url = URL.createObjectURL(new Blob([text], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  // Some browsers start the download after click() returns: revoke on the next task, not at once.
  setTimeout(() => URL.revokeObjectURL(url));
}
