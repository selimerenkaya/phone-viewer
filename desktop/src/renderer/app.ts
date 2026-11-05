// Runs as the window's preload (Node + DOM). UI shell and wiring, lead-owned.
import { startLink } from "../link";
import { createVideoFeature } from "../video";
import { createControlFeature } from "../control";
import { createNotifyFeature } from "../notify";
import type { Send } from "../feature";

window.addEventListener("DOMContentLoaded", () => {
  const send: Send = () => {}; // replaced by the link lane in M1
  startLink({
    video: createVideoFeature(send),
    control: createControlFeature(send),
    notify: createNotifyFeature(send),
  });
});
