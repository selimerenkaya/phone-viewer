import { test } from "node:test";
import assert from "node:assert/strict";
import { parseFrame } from ".";

test("VIDEO_FRAME header: flags, u64 BE timestamp, data", () => {
  // same bytes the Android test builds with VideoFeature.frame(true, 0x0102030405060708, 00 00 00 01 65)
  const bytes = Buffer.from("ffffffff" + "01" + "0102030405060708" + "0000000165", "hex");
  const f = parseFrame(bytes.subarray(4)); // non-zero byteOffset, like a slice of the socket buffer
  assert.equal(f.key, true);
  assert.equal(f.timestampUs, 0x0102030405060708);
  assert.deepEqual([...f.data], [0, 0, 0, 1, 0x65]);
  assert.equal(parseFrame(Buffer.from("00" + "0000000000000000", "hex")).key, false);
});
