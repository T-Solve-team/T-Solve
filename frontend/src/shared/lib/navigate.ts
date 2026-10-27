/** Loads `url` as a new page (a full navigation). The one place that does it, so tests can replace it. */
export function navigateTo(url: string): void {
  window.location.assign(url);
}
