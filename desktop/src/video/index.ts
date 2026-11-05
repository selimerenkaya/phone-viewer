import type { Feature, Send } from "../feature";

const VIDEO_CONFIG = 0x10;
const VIDEO_FRAME = 0x11;
const VIDEO_START = 0x12;
const VIDEO_STOP = 0x13;

/** VIDEO_FRAME payload: u8 flags (bit0 = key) · u64 BE timestamp µs · Annex B access unit. */
export function parseFrame(payload: Uint8Array) {
  const view = new DataView(payload.buffer, payload.byteOffset, payload.byteLength);
  return { key: (payload[0] & 1) === 1, timestampUs: Number(view.getBigUint64(1)), data: payload.subarray(9) };
}

/** Video lane, PC side: WebCodecs decoder drawing onto `<canvas id="screen">`. */
export function createVideoFeature(send: Send): Feature & { onConnected(): void } {
  let decoder: VideoDecoder | undefined;
  let waitingForKey = true;

  function configure(payload: Uint8Array) {
    const { codec, width, height } = JSON.parse(new TextDecoder().decode(payload));
    const canvas = document.getElementById("screen") as HTMLCanvasElement;
    canvas.width = width;
    canvas.height = height;
    if (!decoder || decoder.state === "closed") {
      const ctx = canvas.getContext("2d")!;
      decoder = new VideoDecoder({
        output: (frame) => { ctx.drawImage(frame, 0, 0, canvas.width, canvas.height); frame.close(); },
        error: (e) => console.error("video decoder:", e), // closes the decoder; the next VIDEO_CONFIG makes a new one
      });
    }
    // No `description`: input is Annex B with SPS/PPS in front of every key frame.
    decoder.configure({ codec, optimizeForLatency: true });
    waitingForKey = true;
  }

  return {
    /** Called once the session is up (after HELLO). */
    onConnected() {
      send(VIDEO_START, new TextEncoder().encode(JSON.stringify({ maxSize: 1920, bitrate: 8_000_000 })));
      window.addEventListener("beforeunload", () => send(VIDEO_STOP, new Uint8Array(0)));
    },
    onMessage(type, payload) {
      if (type === VIDEO_CONFIG) configure(payload);
      if (type !== VIDEO_FRAME || decoder?.state !== "configured") return;
      const f = parseFrame(payload);
      if (waitingForKey && !f.key) return;
      waitingForKey = false;
      decoder.decode(new EncodedVideoChunk({ type: f.key ? "key" : "delta", timestamp: f.timestampUs, data: f.data }));
    },
    onDisconnected() {
      if (decoder && decoder.state !== "closed") decoder.close();
      decoder = undefined;
    },
  };
}
