import express, { Request, Response, NextFunction } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import compression from 'compression';
import dotenv from 'dotenv';
import bcrypt from 'bcryptjs';
import pino from 'pino';
import { refreshNodeHealth } from './services/nodeHealth';
import { expirePeers } from './services/peerProvisioning';
import { prisma } from './db';
import { globalLimiter } from './middleware/rateLimit';

// Import Route Handlers
import authRoutes from './routes/auth';
import vpnRoutes from './routes/vpn';
import serverRoutes from './routes/servers';
import configRoutes from './routes/config';
import billingRoutes from './routes/billing';
import adminRoutes from './routes/admin';
import { adminPromos, devicePromos } from './routes/promos';

dotenv.config();

const logger = pino({
  level: process.env.LOG_LEVEL || (process.env.NODE_ENV === 'production' ? 'info' : 'debug'),
  redact: {
    paths: ['req.headers.authorization', 'req.body.password', 'req.body.agentSecret', 'req.body.purchaseToken'],
    censor: '[REDACTED]'
  }
});

const app = express();
const PORT = process.env.PORT || 4000;

// Security and Performance Middleware
app.use(helmet());
app.use(cors({
  origin: process.env.CORS_ORIGIN ? process.env.CORS_ORIGIN.split(',') : '*',
  methods: ['GET', 'POST', 'PUT', 'DELETE'],
  allowedHeaders: ['Content-Type', 'Authorization', 'X-Requested-With']
}));
app.use(compression());
app.use(express.json({ limit: '1mb' }));
// No HTTP access logging; source addresses, headers, URLs and bodies are never persisted.
app.use(globalLimiter);

// Health & Readiness Endpoints (Docker / Kubernetes)
app.get('/health', (_req: Request, res: Response) => {
  res.status(200).json({ status: 'OK', uptime: process.uptime(), timestamp: new Date().toISOString() });
});

app.get('/ready', async (_req: Request, res: Response) => {
  try {
    await prisma.$queryRaw`SELECT 1`;
    res.status(200).json({ status: 'READY', database: 'CONNECTED' });
  } catch (err: any) {
    res.status(503).json({ status: 'UNAVAILABLE', error: 'Database connection failed' });
  }
});

// Mount Production API Routes
app.use('/api/v1/auth', authRoutes);
app.use('/api/v1/vpn', vpnRoutes);
app.use('/api/v1/servers', serverRoutes);
app.use('/api/v1', configRoutes);
app.use('/api/v1/billing', billingRoutes);
app.use('/api/v1/admin/promos', adminPromos);
app.use('/api/v1/promos', devicePromos);
app.use('/api/v1/admin', adminRoutes);

// Global 404 Handler
app.use((_req: Request, res: Response) => {
  res.status(404).json({ success: false, error: 'NOT_FOUND', message: 'Resource does not exist' });
});

// Centralized Safe Error Handler (Zero stack traces exposed)
app.use((err: any, req: Request, res: Response, _next: NextFunction) => {
  logger.error('Unhandled request error');
  const statusCode = typeof err.status === 'number' ? err.status : 500;
  res.status(statusCode).json({
    success: false,
    error: err.code || 'INTERNAL_SERVER_ERROR',
    message: 'An unexpected error occurred'
  });
});

// Auto-seed Initial Super Admin on startup if none exists
async function seedDefaultAdmin() {
  try {
    const adminCount = await prisma.adminUser.count();
    if (adminCount === 0) {
      const defaultEmail = process.env.ADMIN_EMAIL;
      if (!defaultEmail || !process.env.ADMIN_PASSWORD || process.env.ADMIN_PASSWORD.length < 12) throw new Error('Configure admin bootstrap credentials');
      const defaultPass = process.env.ADMIN_PASSWORD!;
      const passwordHash = await bcrypt.hash(defaultPass, 12);
      await prisma.adminUser.create({
        data: {
          email: defaultEmail,
          passwordHash,
          role: 'SUPER_ADMIN'
        }
      });
      logger.info('Initial super admin initialized');
    }
  } catch (e: any) {
    logger.warn('Admin bootstrap failed');
  }
}

// Start Server
const server = app.listen(PORT, async () => {
  logger.info(`🚀 Nsec VPN Production Backend running on port ${PORT}`);
  await seedDefaultAdmin();
  void refreshNodeHealth().catch(() => logger.warn("Node health refresh failed"));
  setInterval(() => { void refreshNodeHealth().catch(() => logger.warn("Node health refresh failed")); void expirePeers().catch(() => logger.warn("Lease cleanup failed")); }, 60000).unref();
});

// Graceful Process Teardown
const shutdown = async (signal: string) => {
  logger.info(`[SHUTDOWN] Received ${signal}. Draining HTTP listener and DB connections...`);
  server.close(async () => {
    try {
      await prisma.$disconnect();
      logger.info('[SHUTDOWN] Database connections released. Exiting gracefully.');
      process.exit(0);
    } catch (_err) {
      process.exit(1);
    }
  });
};

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));

export default app;
