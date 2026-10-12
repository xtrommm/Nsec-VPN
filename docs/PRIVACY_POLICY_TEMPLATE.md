# Privacy policy review draft

This draft describes the repair candidate. Review provider practices, retention,
deletion and actual deployment before publishing a policy or Play declaration.

The app, backend and agent do not persist browsing history, DNS queries, traffic
content or originating client IP addresses. Current WireGuard endpoints,
handshakes and transfer counters necessarily exist in kernel memory while a
peer is active. Request and proxy access logging is disabled in the supplied
configuration; external CDN/cloud/provider behavior requires separate review.

A random installation ID, hashed registration proof, hashed refresh tokens,
entitlements and operational peer records are persisted. Peer records contain
the public key, assigned private tunnel address, node and lease expiry; node
configuration and protected agent lease metadata persist across reboots.
These service records are not browsing logs. Do not claim peers exist only in RAM.

The device generates its WireGuard private key locally and never sends it to the
backend or node. Android Keystore protects the vault encryption key; hardware
backing varies by device. An installation ID is not a hardware UUID or a promise
that records can never be associated with an individual.

Google Play handles payment information; the backend verifies purchases and
stores hashed purchase ownership and entitlement expiry. Google AdMob supplies
ads and its signed reward confirmations. Review their data practices and consent
requirements separately. Ad traffic from this app intentionally bypasses the VPN.

Clearing local data resets local credentials; it does not delete backend device,
billing or audit records. A tested server-side deletion process, support contact
and explicit retention policy must be established before publication.
