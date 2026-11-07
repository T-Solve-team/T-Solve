describe('test setup teardown', () => {
  // The check is the throw. This hook runs just before the afterAll of setup.ts: if that one does not cancel this
  // timer, it fires as the file ends and Vitest fails the run with an unhandled error.
  afterAll(() => {
    setTimeout(() => {
      throw new Error('BUG-112: a timer outlived its test file');
    }, 1);
  });

  it('cancels a timer left running at the end of a test file, and lets one fire before that (BUG-112)', async () => {
    await expect(
      new Promise((resolve) => {
        setTimeout(() => resolve('fired'), 1);
      }),
    ).resolves.toBe('fired');
  });
});
