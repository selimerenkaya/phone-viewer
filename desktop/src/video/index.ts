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
export function createVideoFeature(send: Send): Feature {
  let decoder: VideoDecoder | undefined;
  let waitingForKey = true;
  const stopOnClose = () => send(VIDEO_STOP, new Uint8Array(0));

  function configure(payload: Uint8Array) {
    const { codec, width, height } = JSON.parse(new TextDecoder().decode(payload));
    const canvas = document.getElementById("screen") as HTMLCanvasElement;
    canvas.width = width;
    canvas.height = height;
    if (!decoder || decoder.state === "closed") {
      const ctx = canvas.getContext("2d")!;
      decoder = new VideoDecoder({
        output: (frame) => {
          ctx.drawImage(frame, 0, 0, canvas.width, canvas.height);
          frame.close();
          document.body.dataset.video = "on"; // the UI shell hides its "waiting for the screen" hint
        },
        // The error closes the decoder. Restarting the stream makes the phone send a fresh VIDEO_CONFIG + key frame;
        // the 1 s delay keeps a decoder that always fails from spinning.
        error: (e) => { console.error("video decoder:", e); setTimeout(restart, 1000); },
      });
    }
    // No `description`: input is Annex B with SPS/PPS in front of every key frame.
    decoder.configure({ codec, optimizeForLatency: true });
    waitingForKey = true;
  }

  const start = () => send(VIDEO_START, new TextEncoder().encode(JSON.stringify({ maxSize: 1920, bitrate: 8_000_000 })));
  const restart = () => { stopOnClose(); start(); }; // the phone keeps its screen-share consent across STOP/START

  return {
    onConnected() {
      start();
      window.addEventListener("beforeunload", stopOnClose); // same function each time, so added once
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
      delete document.body.dataset.video;
    },
  };
}
