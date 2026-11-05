import type { Feature, Send } from "../feature";

const TOUCH = 0x20, GLOBAL = 0x21, TEXT = 0x22, SCROLL = 0x23, KEY = 0x24;
const BURST_MS = 80; // typed characters / wheel ticks within this window go out as one frame

type Rect = { left: number; top: number; width: number; height: number };

/**
 * Pointer position → fractions of the drawn video, which is the canvas bitmap
 * (videoW × videoH) fitted "contain"-style into the element box. Not clamped.
 */
export function toFraction(clientX: number, clientY: number, box: Rect, videoW: number, videoH: number) {
  const s = Math.min(box.width / videoW, box.height / videoH);
  const w = videoW * s, h = videoH * s;
  return {
    x: (clientX - box.left - (box.width - w) / 2) / w,
    y: (clientY - box.top - (box.height - h) / 2) / h,
  };
}

const clamp = (v: number) => Math.min(1, Math.max(0, v));
const json = (o: object) => new TextEncoder().encode(JSON.stringify(o));

export function createControlFeature(sendRaw: Send): Feature {
  let down = false, connected = false;
  const send: Send = (t, p) => { if (connected) sendRaw(t, p); }; // input before the session starts is dropped
  const canvas = typeof document === "undefined" ? null : document.getElementById("screen") as HTMLCanvasElement | null;
  if (canvas) attach(canvas);

  function attach(canvas: HTMLCanvasElement) {
    const at = (e: MouseEvent) => toFraction(e.clientX, e.clientY, canvas.getBoundingClientRect(), canvas.width, canvas.height);
    const touch = (action: string, e: MouseEvent) => {
      const p = at(e);
      send(TOUCH, json({ action, x: clamp(p.x), y: clamp(p.y) }));
    };

    canvas.addEventListener("pointerdown", (e) => {
      const p = at(e);
      if (e.button !== 0 || p.x < 0 || p.x > 1 || p.y < 0 || p.y > 1) return; // ignore clicks on the letterbox
      canvas.setPointerCapture(e.pointerId);
      down = true;
      touch("down", e);
    });
    canvas.addEventListener("pointermove", (e) => { if (down) touch("move", e); });
    const release = (e: PointerEvent) => { if (down) { down = false; touch("up", e); } };
    canvas.addEventListener("pointerup", release);
    canvas.addEventListener("pointercancel", release);

    // Wheel: one swipe per burst. dy is the finger's travel in screen fractions (wheel down → finger up).
    let wheel: { x: number; y: number; dy: number } | null = null;
    canvas.addEventListener("wheel", (e) => {
      e.preventDefault();
      const p = at(e);
      const px = e.deltaMode === 1 ? 40 : e.deltaMode === 2 ? canvas.clientHeight : 1; // lines / pages → px
      const dy = -(e.deltaY * px) / (canvas.getBoundingClientRect().height || 1);
      if (wheel) { wheel.dy += dy; return; }
      wheel = { x: clamp(p.x), y: clamp(p.y), dy };
      setTimeout(() => { send(SCROLL, json({ ...wheel!, dy: Math.max(-1, Math.min(1, wheel!.dy)) })); wheel = null; }, BURST_MS);
    }, { passive: false });

    let text = "", flushTimer: ReturnType<typeof setTimeout> | undefined;
    const flush = () => { clearTimeout(flushTimer); if (text) send(TEXT, json({ text })); text = ""; };
    window.addEventListener("keydown", (e) => {
      if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return;
      if (e.key === "Escape") return send(GLOBAL, json({ action: "back" }));
      const key = e.key === "Backspace" ? "backspace" : e.key === "Enter" ? "enter" : null;
      if (key) { e.preventDefault(); flush(); return send(KEY, json({ key })); } // flush first: keep typing order
      if (e.key.length !== 1 || e.ctrlKey || e.altKey || e.metaKey) return;
      e.preventDefault();
      if (!text) flushTimer = setTimeout(flush, BURST_MS);
      text += e.key;
    });

    const bar = document.createElement("div");
    bar.style.cssText = "position:fixed;right:8px;bottom:8px;display:flex;gap:4px";
    for (const [label, action] of [["◀", "back"], ["●", "home"], ["■", "recents"], ["▼", "notifications"]]) {
      const b = document.createElement("button");
      b.textContent = label;
      b.title = action;
      b.onclick = () => { b.blur(); send(GLOBAL, json({ action })); }; // blur: Space must type, not re-click
      bar.append(b);
    }
    document.body.append(bar);
  }

  return {
    onConnected() { connected = true; },
    onMessage() {}, // control is PC → phone only
    onDisconnected() { connected = false; down = false; },
  };
}
