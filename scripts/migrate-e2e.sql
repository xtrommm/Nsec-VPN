-- Apply once against an EXISTING initialized database before deploying new backend.
-- Backup first. No endpoint, key or credential is fabricated or overwritten.
BEGIN;
LOCK TABLE "ServerNode", "PeerSession" IN ACCESS EXCLUSIVE MODE;
ALTER TABLE "ServerNode" ADD COLUMN IF NOT EXISTS "publicIpv4" TEXT;
ALTER TABLE "ServerNode" ADD COLUMN IF NOT EXISTS "dnsServers" TEXT[] NOT NULL DEFAULT ARRAY['1.1.1.1','1.0.0.1'];
ALTER TABLE "ServerNode" ADD COLUMN IF NOT EXISTS "mtu" INTEGER NOT NULL DEFAULT 1420;
ALTER TABLE "ServerNode" ADD COLUMN IF NOT EXISTS "maxPeers" INTEGER NOT NULL DEFAULT 250;
ALTER TABLE "ServerNode" ADD COLUMN IF NOT EXISTS "healthy" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "ServerNode" ADD COLUMN IF NOT EXISTS "healthCheckedAt" TIMESTAMP(3);
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM "PeerSession" WHERE active GROUP BY "serverId", "allocatedIp" HAVING count(*) > 1)
     OR EXISTS (SELECT 1 FROM "PeerSession" WHERE active GROUP BY "serverId", "deviceId" HAVING count(*) > 1)
  THEN RAISE EXCEPTION 'Duplicate active leases: reconcile with wg peers before migration'; END IF;
END $$;
DROP INDEX IF EXISTS "PeerSession_serverId_allocatedIp_active_key";
CREATE UNIQUE INDEX IF NOT EXISTS "PeerSession_active_ip_key" ON "PeerSession" ("serverId", "allocatedIp") WHERE active;
CREATE UNIQUE INDEX IF NOT EXISTS "PeerSession_active_device_node_key" ON "PeerSession" ("serverId", "deviceId") WHERE active;
CREATE UNIQUE INDEX IF NOT EXISTS "PeerSession_active_key_node_key" ON "PeerSession" ("serverId", "clientPublicKey") WHERE active;
ALTER TABLE "Device" ADD COLUMN IF NOT EXISTS "registrationSecretHash" TEXT;
CREATE TABLE IF NOT EXISTS "RewardGrant" ("transactionId" TEXT PRIMARY KEY, "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS "PurchaseClaim" ("tokenHash" TEXT PRIMARY KEY, "deviceId" TEXT NOT NULL, "expiresAt" TIMESTAMP(3) NOT NULL);
COMMIT;
-- Reversal requires stopping new backend, then dropping the three new indexes and
-- columns above; restore backup if old inactive-history duplicates prevent old index.
-- Do not reverse automatically while clients are connected.
