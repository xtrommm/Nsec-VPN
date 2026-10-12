/** Occupied WireGuard peer slots, rounded up; not CPU or bandwidth usage. */
export function peerCapacityPercent(peers: number, capacity: number): number {
  if (!Number.isInteger(peers) || peers < 0 || !Number.isInteger(capacity) || capacity < 1)
    throw new Error('INVALID_NODE_CAPACITY');
  return Math.min(100, Math.ceil(peers / capacity * 100));
}
