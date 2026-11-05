// Contract between the link lane and each feature lane (see docs/AGENTS.md). Lead-owned.
export type Send = (type: number, payload: Uint8Array) => void;

export interface Feature {
  /** Called by the link router for every frame whose type is in this feature's range. */
  onMessage(type: number, payload: Uint8Array): void;
  onDisconnected(): void;
}
