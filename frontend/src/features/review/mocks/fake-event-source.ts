import { vi } from 'vitest';

/** Test double for the browser EventSource: tests drive open / named events / error by hand. */
export class FakeEventSource {
  static instances: FakeEventSource[] = [];
  readonly listeners = new Map<string, Set<(event: Event) => void>>();
  closed = false;

  readonly url: string;
  readonly init: EventSourceInit | undefined;

  constructor(url: string, init?: EventSourceInit) {
    this.url = url;
    this.init = init;
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: (event: Event) => void) {
    const set = this.listeners.get(type) ?? new Set();
    set.add(listener);
    this.listeners.set(type, set);
  }

  removeEventListener(type: string, listener: (event: Event) => void) {
    this.listeners.get(type)?.delete(listener);
  }

  close() {
    this.closed = true;
  }

  emit(type: string, data = '') {
    const event = new MessageEvent(type, { data });
    this.listeners.get(type)?.forEach((listener) => listener(event));
    const handler = (this as unknown as Record<string, ((e: Event) => void) | null | undefined>)[
      `on${type}`
    ];
    handler?.(event);
  }

  static install() {
    FakeEventSource.instances = [];
    vi.stubGlobal('EventSource', FakeEventSource);
  }

  /** The most recently opened stream; fails the test when none was opened. */
  static get last(): FakeEventSource {
    const source = FakeEventSource.instances.at(-1);
    if (!source) throw new Error('no EventSource was opened');
    return source;
  }
}
