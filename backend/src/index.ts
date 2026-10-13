import express, { Request, Response, NextFunction } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import compression from 'compression';
import dotenv from 'dotenv';
import { bootstrapAdmin } from './services/bootstrapAdmin';
import pino from 'pino';
import { refreshNodeHealth } from './services/nodeHealth';
import { expirePeers } from './services/peerProvisioning';
import { prisma } from './db';
import { trustedProxyCidrs } from './services/proxyTrust';
import { retentionDays, runRetention } from './services/retention';
import { reconcilePurchases } from './services/purchaseReconciliation';
import { globalLimiter } from './middleware/rateLimit';

// Import Route Handlers
import authRoutes from './routes/auth';
import recoveryRoutes from './routes/recovery';
import vpnRoutes from './routes/vpn';
import serverRoutes from './routes/servers';
import configRoutes from './routes/config';
import billingRoutes from './routes/billing';
import adminMfaRoutes from './routes/adminMfa';
import adminRoutes from './routes/admin';
import { adminPromos, devicePromos } from './routes/promos';
import { adminRequests, deviceRequests } from './routes/serverRequests';

dotenv.config();

const logger = pino({
  level: process.env.LOG_LEVEL || (process.env.NODE_ENV === 'production' ? 'info' : 'debug'),
  redact: {
    paths: ['req.headers.authorization', 'req.body.password', 'req.body.agentSecret', 'req.body.purchaseToken'],
    censor: '[REDACTED]'
  }
});

const app = express();
app.set('trust proxy', trustedProxyCidrs(process.env.TRUSTED_PROXY_CIDRS));
const PORT = process.env.PORT || 4000;
let draining = false;
retentionDays();

// Security and Performance Middleware
app.use(helmet());
app.use(cors({
  origin: process.env.CORS_ORIGIN ? process.env.CORS_ORIGIN.split(',') : '*',
  methods: ['GET', 'POST', 'PUT', 'DELETE'],
  allowedHeaders: ['Content-Type', 'Authorization', 'X-Requested-With', 'X-App-Version-Code']
}));
app.use(compression());
app.use(express.json({ limit: '1mb' }));
// No HTTP access logging; source addresses, headers, URLs and bodies are never persisted.

// Health & Readiness Endpoints (Docker / Kubernetes)
app.get('/health', (_req: Request, res: Response) => {
  if (draining) return res.status(503).json({ status: 'DRAINING' });
  res.status(200).json({ status: 'OK', uptime: process.uptime(), timestamp: new Date().toISOString() });
});

app.get('/ready', async (_req: Request, res: Response) => {
  if (draining) return res.status(503).json({ status: 'DRAINING' });
  try {
    await prisma.$queryRaw`SELECT 1`;
    res.status(200).json({ status: 'READY', database: 'CONNECTED' });
  } catch (err: any) {
    res.status(503).json({ status: 'UNAVAILABLE', error: 'Database connection failed' });
  }
});

// Health probes must not share an exhausted user API quota.
app.use(globalLimiter);

// Authenticated metadata must never enter browser/shared caches.
app.use('/api/v1', (_req, res, next) => { res.setHeader('Cache-Control', 'no-store'); next(); });

// Mount Production API Routes
app.use('/api/v1/auth', authRoutes);
app.use('/api/v1/auth', recoveryRoutes);
app.use('/api/v1/vpn', vpnRoutes);
app.use('/api/v1/servers', serverRoutes);
app.use('/api/v1', configRoutes);
app.use('/api/v1/billing', billingRoutes);
app.use('/api/v1/admin/server-requests', adminRequests);
app.use('/api/v1/server-requests', deviceRequests);
app.use('/api/v1/admin/promos', adminPromos);
app.use('/api/v1/promos', devicePromos);
app.use('/api/v1/admin/mfa', adminMfaRoutes);
app.use('/api/v1/admin', adminRoutes);

// Global 404 Handler
app.use((_req: Request, res: Response) => {
  res.status(404).json({ success: false, error: 'NOT_FOUND', message: 'Resource does not exist' });
});

// Centralized Safe Error Handler (Zero stack traces exposed)
app.use((err: any, req: Request, res: Response, _next: NextFunction) => {
  logger.error('Unhandled request error');
  const statusCode = Number.isInteger(err.status) && err.status >= 400 && err.status <= 599 ? err.status : 500;
  res.status(statusCode).json({
    success: false,
    error: err.type === 'entity.parse.failed' ? 'INVALID_JSON' : statusCode === 413 ? 'PAYLOAD_TOO_LARGE' : 'INTERNAL_SERVER_ERROR',
    message: 'An unexpected error occurred'
  });
});

// Initialization completes before listening; optional services fail closed at their endpoint.
let server: import('http').Server | undefined;
const schedules = new Set<NodeJS.Timeout>();
function schedule(task: () => Promise<unknown>, delay: number, message: string) {
  const timer = setInterval(() => { if (!draining) void task().catch(() => logger.warn(message)); }, delay);
  timer.unref(); schedules.add(timer);
}
export async function startServer() {
  if (await bootstrapAdmin()) logger.info('Initial super admin initialized');
  if (draining) return;
  server = app.listen(PORT, () => {
    logger.info('Backend listener ready');
    void refreshNodeHealth().catch(() => logger.warn('Node health refresh failed'));
    schedule(refreshNodeHealth, 30000, 'Node health refresh failed');
    schedule(expirePeers, 60000, 'Lease cleanup failed');
    schedule(runRetention, 3600000, 'Retention cleanup failed');
    schedule(reconcilePurchases, 60000, 'Purchase reconciliation failed');
  });
  return server;
}
export async function shutdown() {
  if (draining) return;
  draining = true;
  for (const timer of schedules) clearInterval(timer);
  schedules.clear();
  const deadline = setTimeout(() => { logger.error('Shutdown drain deadline exceeded'); process.exit(1); }, 30000);
  deadline.unref();
  if (server) await new Promise<void>(resolve => { server!.close(() => resolve()); server!.closeIdleConnections(); });
  await prisma.$disconnect();
  clearTimeout(deadline);
}
if (require.main === module) {
  void startServer().catch(async () => { logger.error('Backend initialization failed; verify database and bootstrap configuration'); await prisma.$disconnect(); process.exit(1); });
  for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => {
    void shutdown().then(() => process.exit(0)).catch(() => process.exit(1));
  });
}
export default app;
