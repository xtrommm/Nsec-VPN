-- REVIEW FIRST. Apply only after migrations; psql --set=node_id=... --set=current_public_key=... --set=credential_ref=env:YOUR_CONFIGURED_VARIABLE (interactive psql; use \i scripts/correct-node.sql)
-- No guessed identity, current key, geography, agent URL or secret. The public key comes from `wg show wg0 public-key` AFTER rotation.
-- Stop backend node edits during this transaction. Snapshot contains public node data only.
\set ON_ERROR_STOP on
BEGIN;
CREATE TABLE IF NOT EXISTS "NsecNodeRepairBackup" (
  "nodeId" TEXT PRIMARY KEY, endpoint TEXT NOT NULL, "publicKey" TEXT NOT NULL,
  "publicIpv4" TEXT, healthy BOOLEAN NOT NULL, "healthCheckedAt" TIMESTAMP(3)
);
INSERT INTO "NsecNodeRepairBackup" SELECT id, endpoint, "publicKey", "publicIpv4", healthy, "healthCheckedAt"
FROM "ServerNode" WHERE id = :'node_id' ON CONFLICT ("nodeId") DO NOTHING;
UPDATE "ServerNode" SET endpoint='47.85.109.170:51820', "publicKey"=:'current_public_key',
 "agentSecretHash"=:'credential_ref', "publicIpv4"=NULL, healthy=false, "healthCheckedAt"=NULL, "updatedAt"=CURRENT_TIMESTAMP
WHERE id=:'node_id' AND :'credential_ref' ~ '^env:[A-Z][A-Z0-9_]{2,95}$' AND CASE WHEN :'current_public_key' ~ '^[A-Za-z0-9+/]{43}=$'
 THEN octet_length(decode(:'current_public_key','base64'))=32 AND decode(:'current_public_key','base64')<>decode(repeat('00',32),'hex') AND encode(decode(:'current_public_key','base64'),'base64')=:'current_public_key'
 ELSE false END;
-- Expect UPDATE 1. ROLLBACK instead if not exactly one row. Commit is explicit after reviewing output:
SELECT id,name,endpoint,"publicKey",healthy FROM "ServerNode" WHERE id=:'node_id';
-- This script deliberately leaves transaction open. Run interactively with \i, then COMMIT or ROLLBACK.
-- REVERSAL after stopping edits: UPDATE "ServerNode" s SET endpoint=b.endpoint,"publicKey"=b."publicKey",
-- "publicIpv4"=b."publicIpv4",healthy=false,"healthCheckedAt"=NULL FROM "NsecNodeRepairBackup" b WHERE s.id=b."nodeId" AND s.id=:'node_id';
-- Never restore a compromised key to a live node. After commit run admin Test node, then enable only if healthy.
