import express, { Request, Response, NextFunction } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import compression from 'compression';
import dotenv from 'dotenv';
import bcrypt from 'bcryptjs';
import pino from 'pino';
import pinoHttp from 'pino-http';
import { prisma } from './db';
import { globalLimiter } from './middleware/rateLimit';

// Import Route Handlers
import authRoutes from './routes/auth';
import vpnRoutes from './routes/vpn';
import serverRoutes from './routes/servers';
import configRoutes from './routes/config';
import billingRoutes from './routes/billing';
import adminRoutes from './routes/admin';

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
app.use(pinoHttp({ logger, autoLogging: { ignore: (req) => req.url === '/health' || req.url === '/ready' } }));
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
app.use('/api/v1/admin', adminRoutes);

// Global 404 Handler
app.use((_req: Request, res: Response) => {
  res.status(404).json({ success: false, error: 'NOT_FOUND', message: 'Resource does not exist' });
});

// Centralized Safe Error Handler (Zero stack traces exposed)
app.use((err: any, req: Request, res: Response, _next: NextFunction) => {
  logger.error({ err, path: req.path, method: req.method }, 'Unhandled request error');
  const statusCode = typeof err.status === 'number' ? err.status : 500;
  res.status(statusCode).json({
    success: false,
    error: err.code || 'INTERNAL_SERVER_ERROR',
    message: process.env.NODE_ENV === 'production' ? 'An unexpected error occurred' : err.message
  });
});

// Auto-seed Initial Super Admin on startup if none exists
async function seedDefaultAdmin() {
  try {
    const adminCount = await prisma.adminUser.count();
    if (adminCount === 0) {
      const defaultEmail = process.env.ADMIN_EMAIL || 'admin@nsecvpn.com';
      const defaultPass = process.env.ADMIN_PASSWORD || 'NsecAdmin2026!Secure';
      const passwordHash = await bcrypt.hash(defaultPass, 12);
      await prisma.adminUser.create({
        data: {
          email: defaultEmail,
          passwordHash,
          role: 'SUPER_ADMIN'
        }
      });
      logger.info(`[BOOTSTRAP] Initial super admin initialized: ${defaultEmail}`);
    }
  } catch (e: any) {
    logger.warn(`[BOOTSTRAP] Database not yet ready or migrating: ${e.message}`);
  }
}

// Start Server
const server = app.listen(PORT, async () => {
  logger.info(`🚀 Nsec VPN Production Backend running on port ${PORT}`);
  await seedDefaultAdmin();
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
