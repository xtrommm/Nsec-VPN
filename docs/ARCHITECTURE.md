# Nsec VPN Architecture & System Design

This document details the system design, network topologies, cryptographic handshakes, and data models of the **Nsec VPN** platform.

---

## 1. High-Level Architecture

The Nsec VPN ecosystem consists of four interconnected components:
1. **Android Client (`/app`)**: Native WireGuard® Go engine integrated with Jetpack Compose.
2. **Production Backend API (`/backend`)**: Node.js/TypeScript REST API with PostgreSQL and Prisma ORM for device registrations, dynamic peer IP allocations, and configuration.
3. **Web Admin Console (`/admin-panel`)**: React 18 / Tailwind single-page application for infrastructure oversight and remote config toggles.
4. **WireGuard Node Agent (`/agent`)**: Lightweight Linux daemon executing parameterized WireGuard kernel operations on distributed edge servers.

```mermaid
graph TD
    Client["Android App (com.nsec.vpn)"]
    Admin["Web Admin Panel (React/Vite)"]
    Nginx["Reverse Proxy (Nginx / SSL)"]
    API["Backend API (Node.js/Express)"]
    DB[("PostgreSQL Database (Prisma)")]
    Google["Google Play Billing API"]
    Agent["Node Agent Daemon (Port 51821)"]
    Kernel["Linux Kernel WireGuard (wg0)"]

    Client -->|HTTPS: Register / Config / Peer| Nginx
    Admin -->|HTTPS: Node & Config Management| Nginx
    Nginx --> API
    API --> DB
    API -->|Validate Subscriptions| Google
    API -->|HMAC-SHA256 REST| Agent
    Agent -->|wg set wg0 peer| Kernel
    Client -.->|Encrypted UDP 51820| Kernel
```

---

## 2. Dynamic WireGuard Peer Handshake Sequence

The client creates a local Curve25519 keypair and requests an encrypted session without exposing private credentials.

```mermaid
sequenceDiagram
    autonumber
    actor User as Android User
    participant App as Android Client
    participant API as Backend Gateway
    participant DB as PostgreSQL
    participant Agent as Gateway Agent
    participant Kernel as Linux WireGuard (wg0)

    User->>App: Tap Connect
    App->>App: Generate Curve25519 Keypair (Private / Public)
    App->>API: POST /api/v1/vpn/connect (Server ID, Client Public Key)
    API->>API: Verify Device JWT & Subscription Tier
    API->>DB: Serializable Tx: Allocate Unique IP (e.g. 10.8.0.42/32)
    API->>Agent: POST /api/v1/agent/peers (HMAC Signature, Client PubKey, IP)
    Agent->>Kernel: execFile("wg set wg0 peer <PubKey> allowed-ips <IP>")
    Agent-->>API: 200 OK (Peer Added)
    API-->>App: 200 OK (Tunnel Config: Gateway Endpoint, Server PubKey, Client IP, DNS)
    App->>Kernel: WireGuard UDP Handshake (Port 51820)
    Note over App,Kernel: Tunnel Established - Traffic Encrypted
```

---

## 3. Strict No-Logs Architecture

Nsec VPN is engineered with zero-logs guarantees across all tiers:
- **No IP Address Logging**: Client originating IP addresses are never written to disk, database, or log streams.
- **No Traffic Logging**: DNS queries, packet destinations, and throughput contents are never inspected or mirrored.
- **Volatile In-Memory Routing**: WireGuard cryptographic public keys exist only in server volatile RAM for active routing and are purged upon disconnection.
- **Hardware Keystore Authentication**: Authentication relies on client hardware UUIDs stored inside Android Keystore without email addresses or passwords.

---

## 4. Location Cluster Pools & Dynamic Load Balancing

To scale locations horizontally (e.g. 10 servers in Singapore, 20 servers in Frankfurt) without cluttering the mobile application interface, Nsec VPN implements **Two-Layer Location Cluster Load Balancing**:

```mermaid
graph TD
    subgraph Mobile Interface
        SG_Card["Singapore (🇸🇬 10 NODES)"]
    end

    subgraph Cluster Pool
        N1["SG Gateway 01 (Load: 65%)"]
        N2["SG Gateway 02 (Load: 14% ⭐ Optimal)"]
        N3["SG Gateway 03 (Load: 48%)"]
        N10["SG Gateway 10 (Load: 80%)"]
    end

    SG_Card -->|User Taps Singapore| Router{Client & Backend Load Balancer}
    Router -->|Selects lowest latency & load| N2
```

1. **Client-Side Aggregation (`/app`)**:
   - Multiple servers sharing the same `countryCode` and `city` (e.g. Singapore) are consolidated into a single card in `ServerListScreen`.
   - The card displays the location name ("Singapore"), average cluster load, lowest ping, and a badge indicating the pool size (`10 NODES`).
   - When the user taps Singapore, `VpnRepository.selectServer()` filters all 10 nodes and selects the optimal node minimizing `pingMs + (currentLoad * 2)`.

2. **Server-Side Cluster Routing (`/backend`)**:
   - In `POST /api/v1/vpn/connect`, if the requested server is part of a multi-node cluster, the backend verifies all sister nodes in that location and automatically routes the handshake to the least-loaded active gateway.
   - If a specific gateway reaches capacity or goes offline, the cluster automatically reroutes handshakes to the next available sister gateway in that same city.
update
