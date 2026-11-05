import { clipboard } from "electron";
import type { Feature, Send } from "../feature";
import { EchoGuard } from "./echo";

const NOTIFICATION = 0x30;
const NOTIFICATION_REMOVED = 0x31;
const CLIPBOARD = 0x32;
const MAX_TOASTS = 50;
const POLL_MS = 1000;

/** notify lane: phone notifications as Windows toasts, clipboard both ways. */
export function createNotifyFeature(send: Send): Feature {
  const toasts = new Map<string, Notification>();
  const echo = new EchoGuard();
  let primed = false;
  // One queue for clipboard reads and writes, so a poll never sees the value from before a write.
  let queue = Promise.resolve();
  const enqueue = (job: () => Promise<void>) => (queue = queue.then(job).catch(() => {}));

  // Polls for the app's whole life; link's send drops frames while no session is up.
  setInterval(() => enqueue(async () => {
    const text = await clipboard.readText();
    if (!primed) {
      primed = true;
      echo.received(text); // only send changes made after start-up
    } else if (text && echo.shouldSend(text)) {
      send(CLIPBOARD, new TextEncoder().encode(JSON.stringify({ text })));
    }
  }), POLL_MS).unref();

  return {
    onMessage(type, payload) {
      let msg: any;
      try {
        msg = JSON.parse(new TextDecoder().decode(payload));
      } catch {
        return;
      }
      if (type === CLIPBOARD && typeof msg.text === "string") {
        const text: string = msg.text;
        enqueue(async () => {
          echo.received(text);
          await clipboard.writeText(text);
        });
      } else if (type === NOTIFICATION && typeof msg.id === "string") {
        toasts.get(msg.id)?.close();
        const title = [msg.app, msg.title].filter(Boolean).join(" · ");
        const toast = new Notification(title, { body: String(msg.text ?? ""), tag: msg.id });
        toast.onclose = () => toasts.get(msg.id) === toast && toasts.delete(msg.id);
        toasts.set(msg.id, toast);
        if (toasts.size > MAX_TOASTS) toasts.delete(toasts.keys().next().value!);
      } else if (type === NOTIFICATION_REMOVED && typeof msg.id === "string") {
        toasts.get(msg.id)?.close();
        toasts.delete(msg.id);
      }
    },
    onDisconnected() {
      toasts.clear(); // removals made while offline never arrive
    },
  };
}
