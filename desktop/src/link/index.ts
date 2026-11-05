import type { Feature } from "../feature";

/** link lane: TLS client, pairing, framing; routes each frame to the feature owning its type range. */
export function startLink(features: { video: Feature; control: Feature; notify: Feature }): void {}
