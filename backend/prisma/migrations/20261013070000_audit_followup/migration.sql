BEGIN;
ALTER TABLE "Device" ADD COLUMN IF NOT EXISTS "appVersionCode" INTEGER;
ALTER TABLE "PurchaseClaim" ADD COLUMN IF NOT EXISTS "purchaseTokenCiphertext" TEXT;
ALTER TABLE "PurchaseClaim" ADD COLUMN IF NOT EXISTS "productId" TEXT;
ALTER TABLE "PurchaseClaim" ADD COLUMN IF NOT EXISTS "active" BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE "PurchaseClaim" ADD COLUMN IF NOT EXISTS "adminRevoked" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "PurchaseClaim" ADD COLUMN IF NOT EXISTS "verifiedAt" TIMESTAMP(3);
ALTER TABLE "PurchaseClaim" ADD COLUMN IF NOT EXISTS "protectedVipUntil" TIMESTAMP(3);
ALTER TABLE "PurchaseClaim" ADD COLUMN IF NOT EXISTS "protectedVipIndefinite" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "PurchaseClaim" ADD COLUMN IF NOT EXISTS "provenanceInitialized" BOOLEAN NOT NULL DEFAULT false;
-- Legacy source attribution is unknown. Preserve existing VIP exactly once;
-- never infer that its whole duration came from a refundable purchase.
UPDATE "PurchaseClaim" p SET
  "protectedVipUntil" = CASE WHEN d."tier" <> 'FREE' THEN d."passExpiresAt" ELSE NULL END,
  "protectedVipIndefinite" = d."tier" = 'VIP' AND d."passExpiresAt" IS NULL,
  "provenanceInitialized" = true
FROM "Device" d WHERE p."deviceId" = d."id" AND NOT p."provenanceInitialized";
CREATE INDEX IF NOT EXISTS "PurchaseClaim_deviceId_idx" ON "PurchaseClaim"("deviceId");
CREATE INDEX IF NOT EXISTS "PurchaseClaim_active_verifiedAt_idx" ON "PurchaseClaim"("active", "verifiedAt");
CREATE INDEX IF NOT EXISTS "RefreshToken_retention_expiry_idx" ON "RefreshToken"("expiresAt");
CREATE INDEX IF NOT EXISTS "RewardTicket_retention_expiry_idx" ON "RewardTicket"("expiresAt");
COMMIT;
