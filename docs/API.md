# Nsec VPN REST API Specification (v1.0.0)

Base URL: `https://api.nsecvpn.com/api/v1`

---

## 1. Authentication & Device Identity

### Register Device
- **Endpoint**: `POST /auth/register-device`
- **Auth**: None (Rate limited: 10/min)
- **Request Body**:
```json
{
  "deviceId": "nsec_device_9876543210_uuid",
  "deviceModel": "Pixel 8 Pro",
  "osVersion": "Android 15",
  "appVersion": "1.0.0",
  "appVersionCode": 1
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "4f8a12e3c09b...",
    "device": {
      "id": "e2c39d84-...",
      "tier": "FREE",
      "passExpiresAt": null
    }
  }
}
```

### Refresh Token
- **Endpoint**: `POST /auth/refresh-token`
- **Auth**: None (Rate limited: 10/min)
- **Request Body**:
```json
{
  "refreshToken": "4f8a12e3c09b..."
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "9d8a43b2c1..."
  }
}
```

---

## 2. Server Gateways

### List Available Locations
- **Endpoint**: `GET /servers`
- **Auth**: Bearer `<accessToken>`
- **Query Params**:
  - `includePremium` (boolean, optional): Set to `false` to exclude VIP servers.
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "servers": [
      {
        "id": "c1f76d90-...",
        "name": "US East (New York)",
        "countryCode": "US",
        "countryName": "United States",
        "city": "New York",
        "flagEmoji": "🇺🇸",
        "currentLoad": 18,
        "isPremiumOnly": false,
        "isRecommended": true
      }
    ]
  }
}
```

---

## 3. WireGuard VPN Session Management

### Negotiate Tunnel Handshake
- **Endpoint**: `POST /vpn/connect`
- **Auth**: Bearer `<accessToken>`
- **Request Body**:
```json
{
  "serverId": "c1f76d90-...",
  "clientPublicKey": "8J5aOQv4rTqK3F6mE5nP7sL9wY2zU1xV4bN7mQ0kP8M="
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "peerId": "peer_abc123456789",
    "tunnelConfig": {
      "serverPublicKey": "xT3kL9mN8vQ1wZ4pE6rY7uI2oA5sD8fG0hJ2kL4zX9c=",
      "serverEndpoint": "us.nsecvpn.com:51820",
      "clientAddress": "10.8.0.42/32",
      "dnsServers": ["1.1.1.1", "1.0.0.1"],
      "allowedIps": ["0.0.0.0/0", "::/0"],
      "persistentKeepalive": 25,
      "mtu": 1420
    }
  }
}
```

### Terminate VPN Session
- **Endpoint**: `POST /vpn/disconnect`
- **Auth**: Bearer `<accessToken>`
- **Request Body**:
```json
{
  "peerId": "peer_abc123456789",
  "clientPublicKey": "8J5aOQv4rTqK3F6mE5nP7sL9wY2zU1xV4bN7mQ0kP8M="
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Disconnected"
}
```

---

## 4. In-App Billing

### Verify Google Play Subscription
- **Endpoint**: `POST /billing/verify`
- **Auth**: Bearer `<accessToken>`
- **Request Body**:
```json
{
  "packageName": "com.nsec.vpn",
  "productId": "nsecvpn_premium_monthly",
  "purchaseToken": "play_token_xyz987654"
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "tier": "VIP",
    "status": "ACTIVE",
    "expiresAt": "2026-11-09T12:00:00.000Z"
  }
}
```

---

## 5. Remote Configuration & Legal

### Get Remote App Config
- **Endpoint**: `GET /config`
- **Auth**: None (Cached: 60s)
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "app": {
      "minAppVersionCode": 1,
      "latestAppVersionCode": 1,
      "forceUpdate": false
    },
    "announcement": {
      "active": false,
      "title": null,
      "body": null,
      "dismissible": true
    },
    "ads": {
      "bannerEnabled": true,
      "interstitialEnabled": true,
      "interstitialIntervalSeconds": 120,
      "rewardedEnabled": true
    }
  }
}
```

### Get Legal Documents
- **Endpoint**: `GET /legal/:type` (where `:type` is `privacy` or `terms`)
- **Auth**: None (Cached: 300s)
