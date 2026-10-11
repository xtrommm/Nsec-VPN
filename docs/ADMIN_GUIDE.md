# Nsec VPN Administrator & Operations Guide

The Web Admin Console (`https://admin.nsecvpn.com` or local `http://localhost:5173`) provides centralized, end-to-end management for all WireGuard VPN server gateway nodes, client device VIP subscriptions, Remote Configuration & Google AdMob controls, and administrative audit logging.

---

## 1. Architecture & Tech Stack

- **Admin Web Console**: React 18, TypeScript 5.4, Vite 5.4, Tailwind CSS 3.4, Lucide Icons.
- **Backend API**: Node.js, Express, Prisma ORM, PostgreSQL, bcryptjs, JWT (`12h` expiration), rate-limiting.
- **Android App**: Kotlin, Jetpack Compose (M3), WireGuard Go backend, Google AdMob SDK.
- **Authentication**: JWT Bearer token with role-based access control (`SUPER_ADMIN` and `ADMIN`).

---

## 2. Environment Variables & Setup

### Backend (`backend/.env`):
```env
PORT=3000
DATABASE_URL="postgresql://nsec_user:nsec_password@localhost:5432/nsecvpn?schema=public"
JWT_SECRET="your_secure_random_jwt_secret_change_me_2026"
NODE_ENV="production"
```

### Admin Panel (`admin-panel/.env` or Vite proxy):
In dev mode, Vite proxies `/api/v1` to `http://localhost:3000`.

### Running the Services:

```bash
# 1. Install & Build Backend
cd backend
npm install
npx prisma generate
npx prisma migrate deploy
npm run build
npm start

# 2. Install & Build Admin Panel
cd ../admin-panel
npm install
npm run build
# Or run dev server:
npm run dev
```

---

## 3. Administrator Roles & Permissions

| Feature | `ADMIN` | `SUPER_ADMIN` |
|---|---|---|
| Sign In & Dashboard Stats | Full | Full |
| Add / Edit WireGuard Nodes | Full | Full |
| Ping Node Health Daemon | Full | Full |
| Toggle Node Active / Disabled | Full | Full |
| Delete Node | Restricted | Full (with confirmation modal) |
| Search & Filter Devices | Full | Full |
| Grant / Revoke VIP Tier | Full | Full (with confirmation modal) |
| Block / Unblock Client Device | Full | Full (with confirmation modal) |
| Export Anonymous CSV | Full | Full |
| Remote Config & Ads Editor | Full | Full |
| Publish Remote Config | Full | Full (with confirmation modal) |
| System Audit Trail Inspection | Hidden | Full |

---

## 4. Feature Testing Checklist

Use this checklist to verify each feature works end to end:

### A. Authentication & Session
- [ ] Sign in with valid admin credentials -> redirects to dashboard, profile pill shows email and role.
- [ ] Invalid credentials show clear error message with alert icon.
- [ ] Sign out button clears token, cleans up state, and displays sign out toast.
- [ ] If JWT token expires (HTTP 401), console prompts for re-authentication cleanly without unhandled rejections.

### B. WireGuard Nodes Management
- [ ] Top KPI metric cards display accurate live nodes count and network load.
- [ ] Filter by tier (`ALL`, `VIP`, `FREE`, `ACTIVE`, `DISABLED`) works instantly.
- [ ] Search input matches node name, city, country, and endpoint.
- [ ] Click **Add Gateway Node** -> modal validates endpoint format (`host:port`), Curve25519 44-char key, agent URL, and 16-char secret.
- [ ] Save node -> node immediately appears in table, audit log records `CREATE_SERVER`.
- [ ] Click **Status** toggle -> toggles between Active and Disabled, updating the node list.
- [ ] Click **Activity** ping icon -> daemon queries health and displays active peers and CPU load.
- [ ] Delete node (`SUPER_ADMIN` only) -> opens confirmation modal; confirming deletes node and updates UI.

### C. Registered Devices & Subscriptions
- [ ] Table renders device UUID, model, OS, tier, and status.
- [ ] Search by UUID filters the table with debounce.
- [ ] Filter by Tier (`ALL`, `VIP`, `FREE`, `REWARDED_PASS`) filters rows.
- [ ] Click **Grant VIP** / **Set Free** -> confirmation modal appears; confirming updates database and table.
- [ ] Click **Block** / **Unblock** -> confirmation modal appears; confirming blocks/unblocks client from establishing VPN sessions.
- [ ] Click **Export CSV** -> downloads `nsecvpn_stats_YYYY-MM-DD.csv`.
- [ ] Pagination controls advance and rewind through records cleanly.

### D. Remote Config & Ads Management
- [ ] Config loads current values from `/api/v1/admin/config` upon navigating to the tab.
- [ ] **Global Ads Kill Switch**: Click **Disable All Ads** -> immediately flips all placements off; click **Enable All Ads** -> flips all placements on.
- [ ] Individual toggles (Banner, Interstitial, Rewarded, Native) update draft state.
- [ ] Interstitial throttle slider and input update cooldown in seconds.
- [ ] Ad unit IDs for each placement are displayed clearly.
- [ ] Sub-tab **Version Policy** allows adjusting min version, latest version, force update, and maintenance mode.
- [ ] Sub-tab **Emergency Broadcast** allows setting announcement title and body; right column displays live Android client mockup preview of `HomeScreen.kt`.
- [ ] Unsaved changes badge ("DRAFT PENDING") appears whenever local edits differ from published values.
- [ ] Click **Discard Edits** -> reverts form to currently published server configuration.
- [ ] Click **Publish Changes** -> validation verifies constraints, confirmation modal summarizes action; confirming publishes to cloud via `PUT /api/v1/admin/config`.
- [ ] Last published timestamp updates immediately.
- [ ] Backend invalidates in-memory config cache so Android mobile clients receive new settings on next sync.

### E. Audit Trail (`SUPER_ADMIN` only)
- [ ] Navigating to **Audit Trail** shows timestamped administrative actions.
- [ ] Filter by action type (`CREATE_SERVER`, `UPDATE_CONFIG`, etc.) narrows down logs.
- [ ] Search by admin email or target ID filters results.
- [ ] Click **Inspect** on any row to open the JSON metadata inspector modal.
