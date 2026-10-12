ALTER TABLE "PromoCode"
 ADD COLUMN IF NOT EXISTS "codeCiphertext" TEXT,
 ADD COLUMN IF NOT EXISTS "passDays" DECIMAL(24,16),
 ADD COLUMN IF NOT EXISTS "startsAt" TIMESTAMP(3),
 ADD COLUMN IF NOT EXISTS "deletedAt" TIMESTAMP(3);
UPDATE "PromoCode" SET "passDays" = "passMinutes"::numeric / 1440 WHERE "passDays" IS NULL;
ALTER TABLE "PromoCode" ALTER COLUMN "passDays" SET NOT NULL, ALTER COLUMN "passDays" SET DEFAULT 1;
CREATE OR REPLACE FUNCTION nsec_promo_days_sync() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 NEW."passDays" := NEW."passMinutes"::numeric / 1440;
 RETURN NEW;
END $$;
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='nsec_promo_days_sync' AND tgrelid='"PromoCode"'::regclass) THEN
  CREATE TRIGGER nsec_promo_days_sync BEFORE INSERT OR UPDATE OF "passMinutes", "passDays" ON "PromoCode" FOR EACH ROW EXECUTE FUNCTION nsec_promo_days_sync();
 END IF;
END $$;
ALTER TABLE "PromoRedemption" ADD COLUMN IF NOT EXISTS "grantExpiresAt" TIMESTAMP(3), ADD COLUMN IF NOT EXISTS "durationMinutes" INTEGER;
ALTER TABLE "AppConfig" ADD COLUMN IF NOT EXISTS "adsEnabled" BOOLEAN NOT NULL DEFAULT true, ADD COLUMN IF NOT EXISTS "supportEmail" TEXT;
ALTER TABLE "ServerNode" ADD COLUMN IF NOT EXISTS "sortOrder" INTEGER NOT NULL DEFAULT 0;
DO $$ BEGIN
 CREATE TYPE "ServerRequestStatus" AS ENUM ('PENDING', 'APPROVED', 'REJECTED', 'DONE');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
CREATE TABLE IF NOT EXISTS "ServerRequest" (
 "id" TEXT PRIMARY KEY, "deviceId" TEXT NOT NULL REFERENCES "Device"("id") ON DELETE CASCADE ON UPDATE CASCADE,
 "country" TEXT NOT NULL, "city" TEXT NOT NULL, "note" TEXT, "appVersion" TEXT,
 "status" "ServerRequestStatus" NOT NULL DEFAULT 'PENDING',
 "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, "updatedAt" TIMESTAMP(3) NOT NULL
);
CREATE INDEX IF NOT EXISTS "ServerRequest_deviceId_createdAt_idx" ON "ServerRequest"("deviceId", "createdAt");
CREATE INDEX IF NOT EXISTS "ServerRequest_status_createdAt_idx" ON "ServerRequest"("status", "createdAt");
CREATE INDEX IF NOT EXISTS "PromoCode_deletedAt_createdAt_idx" ON "PromoCode"("deletedAt", "createdAt");
CREATE INDEX IF NOT EXISTS "PromoRedemption_deviceId_redeemedAt_idx" ON "PromoRedemption"("deviceId", "redeemedAt");
CREATE INDEX IF NOT EXISTS "ServerNode_active_healthy_healthCheckedAt_idx" ON "ServerNode"("active", "healthy", "healthCheckedAt");
