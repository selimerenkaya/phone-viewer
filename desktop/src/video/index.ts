import type { Feature, Send } from "../feature";

/** video lane: fill in the body, keep the signature. */
export function createVideoFeature(send: Send): Feature {
  return { onConnected() {}, onMessage() {}, onDisconnected() {} };
}
