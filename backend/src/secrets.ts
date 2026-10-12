import 'dotenv/config';
function requireJwtSecret(): string {
  const value = process.env.JWT_SECRET;
  if (!value || value.length < 32) throw new Error('Configure JWT_SECRET with at least 32 characters');
  return value;
}
export const JWT_SECRET = requireJwtSecret();
