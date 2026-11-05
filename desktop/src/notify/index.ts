import type { Feature, Send } from "../feature";

/** notify lane: fill in the body, keep the signature. */
export function createNotifyFeature(send: Send): Feature {
  return { onConnected() {}, onMessage() {}, onDisconnected() {} };
}
