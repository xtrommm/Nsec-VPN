import rateLimit from 'express-rate-limit';
import crypto from 'crypto';
const privacySalt = crypto.randomBytes(32);
const privateKey = (req: import('express').Request) => crypto.createHmac('sha256', privacySalt).update(req.ip || 'unavailable').digest('hex');

// Global API rate limit: 120 requests per minute per IP
export const globalLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 120,
  standardHeaders: true,
  keyGenerator: privateKey,
  legacyHeaders: false,
  message: {
    success: false,
    error: 'TOO_MANY_REQUESTS',
    message: 'Rate limit exceeded. Please wait before making more requests.'
  }
});

// Strict rate limit for device registration & tokens: 10 per minute
export const authLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 10,
  standardHeaders: true,
  keyGenerator: privateKey,
  legacyHeaders: false,
  message: {
    success: false,
    error: 'AUTH_RATE_LIMIT',
    message: 'Too many authentication attempts. Please try again shortly.'
  }
});

// Strict rate limit for admin login (brute force defense): 5 per 5 minutes
export const adminLoginLimiter = rateLimit({
  windowMs: 5 * 60 * 1000,
  max: 5,
  standardHeaders: true,
  keyGenerator: privateKey,
  legacyHeaders: false,
  message: {
    success: false,
    error: 'LOGIN_BLOCKED',
    message: 'Too many failed login attempts. Account temporarily locked.'
  }
});

// VPN Connection rate limit: 20 handshakes per minute
export const connectLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 20,
  standardHeaders: true,
  keyGenerator: req => (req as import('./auth').AuthenticatedDeviceRequest).device?.id || privateKey(req),
  legacyHeaders: false,
  message: {
    success: false,
    error: 'CONNECT_RATE_LIMIT',
    message: 'Too many connection attempts in a short period.'
  }
});
