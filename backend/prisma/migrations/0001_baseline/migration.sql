-- CreateEnum
CREATE TYPE "UserRole" AS ENUM ('SUPER_ADMIN', 'ADMIN');

-- CreateEnum
CREATE TYPE "SubscriptionTier" AS ENUM ('FREE', 'VIP', 'REWARDED_PASS');

-- CreateTable
CREATE TABLE "AdminUser" (
    "id" TEXT NOT NULL,
    "email" TEXT NOT NULL,
    "passwordHash" TEXT NOT NULL,
    "role" "UserRole" NOT NULL DEFAULT 'ADMIN',
    "totpSecret" TEXT,
    "totpEnabled" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "AdminUser_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Device" (
    "id" TEXT NOT NULL,
    "deviceId" TEXT NOT NULL,
    "deviceModel" TEXT,
    "osVersion" TEXT,
    "appVersion" TEXT,
    "tier" "SubscriptionTier" NOT NULL DEFAULT 'FREE',
    "passExpiresAt" TIMESTAMP(3),
    "isBlocked" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Device_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "RefreshToken" (
    "id" TEXT NOT NULL,
    "tokenHash" TEXT NOT NULL,
    "deviceId" TEXT NOT NULL,
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "revoked" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "RefreshToken_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ServerNode" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "countryCode" TEXT NOT NULL,
    "countryName" TEXT NOT NULL,
    "city" TEXT NOT NULL,
    "flagEmoji" TEXT NOT NULL,
    "endpoint" TEXT NOT NULL,
    "publicKey" TEXT NOT NULL,
    "subnetIpv4" TEXT NOT NULL DEFAULT '10.8.0.0/24',
    "agentUrl" TEXT NOT NULL,
    "agentSecretHash" TEXT NOT NULL,
    "currentLoad" INTEGER NOT NULL DEFAULT 0,
    "isPremiumOnly" BOOLEAN NOT NULL DEFAULT false,
    "isRecommended" BOOLEAN NOT NULL DEFAULT false,
    "active" BOOLEAN NOT NULL DEFAULT true,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ServerNode_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "PeerSession" (
    "id" TEXT NOT NULL,
    "peerId" TEXT NOT NULL,
    "deviceId" TEXT NOT NULL,
    "serverId" TEXT NOT NULL,
    "allocatedIp" TEXT NOT NULL,
    "clientPublicKey" TEXT NOT NULL,
    "active" BOOLEAN NOT NULL DEFAULT true,
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "PeerSession_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AppConfig" (
    "id" TEXT NOT NULL DEFAULT 'global_config',
    "minAppVersionCode" INTEGER NOT NULL DEFAULT 1,
    "latestAppVersionCode" INTEGER NOT NULL DEFAULT 1,
    "forceUpdate" BOOLEAN NOT NULL DEFAULT false,
    "maintenanceMode" BOOLEAN NOT NULL DEFAULT false,
    "announcementActive" BOOLEAN NOT NULL DEFAULT false,
    "announcementTitle" TEXT,
    "announcementBody" TEXT,
    "bannerAdsEnabled" BOOLEAN NOT NULL DEFAULT true,
    "interstitialAdsEnabled" BOOLEAN NOT NULL DEFAULT true,
    "interstitialIntervalSeconds" INTEGER NOT NULL DEFAULT 120,
    "rewardedAdsEnabled" BOOLEAN NOT NULL DEFAULT true,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "AppConfig_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AuditLog" (
    "id" TEXT NOT NULL,
    "adminId" TEXT NOT NULL,
    "action" TEXT NOT NULL,
    "targetResource" TEXT NOT NULL,
    "targetId" TEXT,
    "metadata" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "AuditLog_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AnonymousStat" (
    "id" TEXT NOT NULL,
    "date" DATE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "connectionsCount" INTEGER NOT NULL DEFAULT 0,
    "countryCode" TEXT NOT NULL,
    "appVersion" TEXT NOT NULL,
    "tier" TEXT NOT NULL,

    CONSTRAINT "AnonymousStat_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "AdminUser_email_key" ON "AdminUser"("email");

-- CreateIndex
CREATE INDEX "AdminUser_email_idx" ON "AdminUser"("email");

-- CreateIndex
CREATE UNIQUE INDEX "Device_deviceId_key" ON "Device"("deviceId");

-- CreateIndex
CREATE INDEX "Device_deviceId_idx" ON "Device"("deviceId");

-- CreateIndex
CREATE INDEX "Device_tier_idx" ON "Device"("tier");

-- CreateIndex
CREATE UNIQUE INDEX "RefreshToken_tokenHash_key" ON "RefreshToken"("tokenHash");

-- CreateIndex
CREATE INDEX "RefreshToken_deviceId_idx" ON "RefreshToken"("deviceId");

-- CreateIndex
CREATE INDEX "RefreshToken_tokenHash_idx" ON "RefreshToken"("tokenHash");

-- CreateIndex
CREATE INDEX "ServerNode_active_isPremiumOnly_idx" ON "ServerNode"("active", "isPremiumOnly");

-- CreateIndex
CREATE INDEX "ServerNode_countryCode_idx" ON "ServerNode"("countryCode");

-- CreateIndex
CREATE UNIQUE INDEX "PeerSession_peerId_key" ON "PeerSession"("peerId");

-- CreateIndex
CREATE INDEX "PeerSession_serverId_active_idx" ON "PeerSession"("serverId", "active");

-- CreateIndex
CREATE INDEX "PeerSession_deviceId_idx" ON "PeerSession"("deviceId");

-- CreateIndex
CREATE UNIQUE INDEX "PeerSession_serverId_allocatedIp_active_key" ON "PeerSession"("serverId", "allocatedIp", "active");

-- CreateIndex
CREATE INDEX "AuditLog_adminId_idx" ON "AuditLog"("adminId");

-- CreateIndex
CREATE INDEX "AuditLog_action_idx" ON "AuditLog"("action");

-- CreateIndex
CREATE INDEX "AuditLog_createdAt_idx" ON "AuditLog"("createdAt");

-- CreateIndex
CREATE INDEX "AnonymousStat_date_idx" ON "AnonymousStat"("date");

-- CreateIndex
CREATE INDEX "AnonymousStat_countryCode_idx" ON "AnonymousStat"("countryCode");

-- AddForeignKey
ALTER TABLE "RefreshToken" ADD CONSTRAINT "RefreshToken_deviceId_fkey" FOREIGN KEY ("deviceId") REFERENCES "Device"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "PeerSession" ADD CONSTRAINT "PeerSession_deviceId_fkey" FOREIGN KEY ("deviceId") REFERENCES "Device"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "PeerSession" ADD CONSTRAINT "PeerSession_serverId_fkey" FOREIGN KEY ("serverId") REFERENCES "ServerNode"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "AuditLog" ADD CONSTRAINT "AuditLog_adminId_fkey" FOREIGN KEY ("adminId") REFERENCES "AdminUser"("id") ON DELETE CASCADE ON UPDATE CASCADE;

