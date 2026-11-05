import type { Feature, Send } from "../feature";

/** control lane: fill in the body, keep the signature. */
export function createControlFeature(send: Send): Feature {
  return { onConnected() {}, onMessage() {}, onDisconnected() {} };
}
