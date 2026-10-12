import router from '../routes/config';

jest.mock('../db', () => ({ prisma: {} }));

test('public privacy response describes durable peer records and installation identity', () => {
  const layer = (router as any).stack.find((entry: any) => entry.route?.path === '/legal/:type');
  const json = jest.fn();
  layer.route.stack[0].handle({ params: { type: 'privacy' } }, { setHeader: jest.fn(), json });
  const response = json.mock.calls[0][0];
  expect(response.success).toBe(true);
  expect(response.data.contentMarkdown).toContain('persists peer configuration');
  expect(response.data.contentMarkdown).toContain('not a hardware identifier');
  expect(response.data.contentMarkdown).not.toContain('exist only in volatile');
});

test('public terms do not promise an unverified node bandwidth', () => {
  const layer = (router as any).stack.find((entry: any) => entry.route?.path === '/legal/:type');
  const json = jest.fn();
  layer.route.stack[0].handle({ params: { type: 'terms' } }, { setHeader: jest.fn(), json });
  expect(json.mock.calls[0][0].data.contentMarkdown).toContain('no fixed bandwidth is guaranteed');
});
