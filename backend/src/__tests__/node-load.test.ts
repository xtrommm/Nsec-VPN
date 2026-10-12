import { peerCapacityPercent } from '../services/nodeLoad';
it('reflects occupied peer capacity, including a single peer on a large node', () => {
  expect(peerCapacityPercent(0, 250)).toBe(0);
  expect(peerCapacityPercent(1, 250)).toBe(1);
  expect(peerCapacityPercent(5, 10)).toBe(50);
  expect(peerCapacityPercent(300, 250)).toBe(100);
});
it('rejects invalid measurements instead of inventing load', () => {
  for (const [peers, capacity] of [[-1, 250], [1, 0], [NaN, 10], [1.5, 10]])
    expect(() => peerCapacityPercent(peers, capacity)).toThrow('INVALID_NODE_CAPACITY');
});
