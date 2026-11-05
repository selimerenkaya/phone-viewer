import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { encodeFrame, FrameReader, pairingCode, sha256 } from "./wire";

const vectors = JSON.parse(fs.readFileSync(path.join(__dirname, "../../../docs/protocol-vectors.json"), "utf8"));
const hex = (s: string) => Buffer.from(s, "hex");

test("frames match the shared vectors", () => {
  for (const v of vectors.frames) {
    const payload = v.payloadUtf8 !== undefined ? Buffer.from(v.payloadUtf8, "utf8") : hex(v.payloadHex);
    assert.equal(encodeFrame(v.type, payload).toString("hex"), v.frameHex);

    // Feed the frame one byte at a time to exercise reassembly.
    const reader = new FrameReader();
    const got = [...hex(v.frameHex)].flatMap((b) => reader.push(Buffer.from([b])));
    assert.deepEqual(got.map((f) => [f.type, f.payload.toString("hex")]), [[v.type, payload.toString("hex")]]);
  }
});

test("a frame over 8 MiB is rejected", () => {
  assert.throws(() => new FrameReader().push(hex("0100800001")));
});

test("commits and pairing codes match the shared vectors", () => {
  for (const v of vectors.pairingCodes) {
    assert.equal(sha256(hex(v.pcNonce)).toString("hex"), v.commit);
    assert.equal(pairingCode(hex(v.phoneFingerprint), hex(v.pcFingerprint), hex(v.phoneNonce), hex(v.pcNonce)), v.code);
  }
});
