import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';

describe('test setup', () => {
  it('lets a mocked request finish when its fetch is aborted, so its socket is not torn down (BUG-080)', async () => {
    let entered: () => void = () => {};
    let release: () => void = () => {};
    const inHandler = new Promise<void>((resolve) => {
      entered = resolve;
    });
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.get('*/api/v1/bug-080', async () => {
        entered();
        await gate;
        return HttpResponse.json({ ok: true });
      }),
    );
    let mocked = false;
    const onMocked = ({ request }: { request: Request }) => {
      if (request.url.endsWith('/bug-080')) mocked = true;
    };
    server.events.on('response:mocked', onMocked);

    // Like a page unmounted at the end of a test while its query is still in the mock handler.
    const controller = new AbortController();
    const aborted = fetch('/api/v1/bug-080', { signal: controller.signal });
    await inHandler;
    controller.abort();
    await expect(aborted).rejects.toMatchObject({ name: 'AbortError' });
    release();

    await new Promise((resolve) => setTimeout(resolve, 500));
    server.events.removeListener('response:mocked', onMocked);
    expect(mocked).toBe(true);
  });
});
