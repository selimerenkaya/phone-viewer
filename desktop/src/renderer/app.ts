// Runs as the window's preload (Node + DOM). UI shell and wiring, lead-owned.
import { startLink, send } from "../link";
import { createVideoFeature } from "../video";
import { createControlFeature } from "../control";
import { createNotifyFeature } from "../notify";

window.addEventListener("DOMContentLoaded", () => {
  startLink({
    video: createVideoFeature(send),
    control: createControlFeature(send),
    notify: createNotifyFeature(send),
  });
});
