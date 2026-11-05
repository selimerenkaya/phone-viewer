// Framing and pairing math from docs/PROTOCOL.md.
import { createHash } from "node:crypto";

export const HELLO = 0x01, PAIR_REQUEST = 0x02, PAIR_RESULT = 0x03, PING = 0x04, PONG = 0x05;
export const PAIR_NONCE = 0x06, PAIR_REVEAL = 0x07;
export const MAX_LEN = 8 * 1024 * 1024;

export function encodeFrame(type: number, payload: Uint8Array): Buffer {
  const head = Buffer.alloc(5);
  head.writeUInt8(type, 0);
  head.writeUInt32BE(payload.length, 1);
  return Buffer.concat([head, payload]);
}

/** Splits a byte stream into frames. Throws on a length over MAX_LEN; the caller closes the connection. */
export class FrameReader {
  // shortcut: re-concats the pending bytes on every chunk, fine for frames up to a few MB; keep a chunk list if big frames get slow.
  private buf = Buffer.alloc(0);

  push(chunk: Buffer): { type: number; payload: Buffer }[] {
    this.buf = Buffer.concat([this.buf, chunk]);
    const frames = [];
    while (this.buf.length >= 5) {
      const len = this.buf.readUInt32BE(1);
      if (len > MAX_LEN) throw new Error(`frame too large: ${len}`);
      if (this.buf.length < 5 + len) break;
      frames.push({ type: this.buf[0], payload: this.buf.subarray(5, 5 + len) });
      this.buf = this.buf.subarray(5 + len);
    }
    return frames;
  }
}

export const sha256 = (...parts: Uint8Array[]): Buffer => createHash("sha256").update(Buffer.concat(parts)).digest();

/** 6-digit code both screens show: first 4 bytes of SHA-256(phoneFP ‖ pcFP ‖ phoneNonce ‖ pcNonce) as u32 BE, mod 1e6. */
export function pairingCode(phoneFp: Buffer, pcFp: Buffer, phoneNonce: Buffer, pcNonce: Buffer): string {
  return String(sha256(phoneFp, pcFp, phoneNonce, pcNonce).readUInt32BE(0) % 1_000_000).padStart(6, "0");
}
