# Nsec VPN Deployment & Operations Guide

This guide covers production deployment of the **Backend API**, **Web Admin Console**, **PostgreSQL Database**, and **WireGuard Server Nodes**.

---

## 1. Prerequisites
- **Control Server (API & Admin)**: Ubuntu 22.04 / 24.04 LTS (2 vCPU, 4GB RAM).
- **Domain Names**:
  - `api.nsecvpn.com` ➔ Pointing to Control Server IP.
  - `admin.nsecvpn.com` ➔ Pointing to Control Server IP.
- **WireGuard Node Servers**: One or more Ubuntu 22.04 / 24.04 nodes (1 Gbps+ port recommended).
- **Software**: Docker & Docker Compose (`apt install docker.io docker-compose`).

---

## 2. Deploying Control Plane (Docker Compose)

### Step 1: Clone Repository
```bash
git clone https://github.com/your-username/Nsec-VPN.git /opt/nsec-vpn
cd /opt/nsec-vpn/deployment
```

### Step 2: Configure Environment Variables
Create `.env` file in `/opt/nsec-vpn/deployment`:
```bash
cat << 'EOF' > .env
DB_PASSWORD=YourStrongDatabasePassword2026!
JWT_SECRET=$(openssl rand -hex 32)
ADMIN_EMAIL=admin@nsecvpn.com
ADMIN_PASSWORD=YourSecureAdminPassword2026!
CORS_ORIGIN=https://admin.nsecvpn.com
EOF
```

### Step 3: SSL Certificates (Certbot)
```bash
apt install -y certbot
certbot certonly --standalone -d api.nsecvpn.com -d admin.nsecvpn.com
```

### Step 4: Launch Stack
```bash
docker-compose up -d --build
```

Verify containers are running:
```bash
docker-compose ps
```

---

## 3. Deploying WireGuard Server Nodes

Run the automated installer on each server node (e.g. US, Germany, Singapore):

```bash
curl -fsSL https://raw.githubusercontent.com/your-username/Nsec-VPN/main/deployment/install-wireguard-node.sh | sudo bash
```

The script will output:
```text
==========================================================
    WireGuard Node & Agent Configured Successfully!       
==========================================================
WireGuard Endpoint: 198.51.100.24:51820
Server Public Key : xT3kL9mN8vQ1wZ4pE6rY7uI2oA5sD8fG0hJ2kL4zX9c=
Agent URL         : http://198.51.100.24:51821
Agent Shared Secret: 4f8a12e3c09b78d65412a9c8e7b6d5f4
==========================================================
```

### Step 5: Register Node in Admin Panel
1. Open `https://admin.nsecvpn.com`.
2. Log in using `admin@nsecvpn.com`.
3. Click **Add New Node**.
4. Enter the Endpoint, Public Key, Agent URL, and Shared Secret.
5. Click **Save & Register Node**.
update
