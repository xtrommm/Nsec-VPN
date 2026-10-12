#!/usr/bin/env bash
# Run in a maintenance window. Existing clients MUST reconnect with the new public key.
set -euo pipefail
umask 077
[[ $EUID == 0 ]] || { echo 'Run as root'; exit 1; }
iface=${WG_INTERFACE:-wg0}
[[ $iface =~ ^[a-zA-Z0-9_-]{1,15}$ ]] || exit 1
wg-quick save "$iface"
backup=$(mktemp -d /root/nsec-key-rotation.XXXXXX)
cp "/etc/wireguard/$iface.conf" "$backup/interface-before.conf"
wg genkey > "$backup/new-private.key"
wg set "$iface" private-key "$backup/new-private.key"
wg-quick save "$iface"
# Keep the installer's auxiliary key files consistent, without exposing them.
if [[ $iface == wg0 ]]; then
 cp "$backup/new-private.key" /etc/wireguard/server_private.key
 wg pubkey < "$backup/new-private.key" > /etc/wireguard/server_public.key
fi
rm "$backup/new-private.key"
printf 'New public key: '; wg show "$iface" public-key
echo 'Admin: Sync public key from node, Test node, then reconnect every client.'
echo 'The old private key was compromised: do not roll back to it.'
printf 'Root-only previous config retained at %s for forensic recovery; it contains the compromised key. Securely remove it after validation.\n' "$backup"
