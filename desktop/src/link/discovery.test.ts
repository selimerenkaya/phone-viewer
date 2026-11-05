import { test } from "node:test";
import assert from "node:assert/strict";
import { parsePtrAnswers, ptrQuery, SERVICE } from "./discovery";
import { backoffMs } from "./wire";

const hex = (s: string) => Buffer.from(s.replace(/\s/g, ""), "hex");

test("ptrQuery asks for PTR _phoneviewer._tcp.local", () => {
  assert.equal(
    ptrQuery(SERVICE).toString("hex"),
    "000000000001000000000000" + "0c5f70686f6e65766965776572" + "045f746370" + "056c6f63616c" + "00" + "000c0001",
  );
});

test("parsePtrAnswers reads compressed PTR answers and skips other records", () => {
  const msg = hex(`
    0000 8400 0000 0002 0000 0000
    0c5f70686f6e65766965776572 045f746370 056c6f63616c 00   000c 0001 00001194 000a  07 506978656c2038 c00c
    c02f 0001 8001 00000078 0004 c0a80114
  `); // answer 1 at offset 12: PTR -> "Pixel 8" + pointer to the service name; answer 2: an A record named "Pixel 8...", ignored
  assert.deepEqual(parsePtrAnswers(msg, SERVICE), ["Pixel 8"]);
});

test("parsePtrAnswers ignores other services and rejects truncated or looping messages", () => {
  const other = hex("0000 8400 0000 0001 0000 0000  065f6f74686572 045f746370 056c6f63616c 00  000c 0001 00000078 0004 0178c00c");
  assert.deepEqual(parsePtrAnswers(other, SERVICE), []);
  assert.throws(() => parsePtrAnswers(hex("000084000000000100000000 c00c"), SERVICE)); // name points at itself
  assert.throws(() => parsePtrAnswers(hex("0000840000000001000000000c5f70"), SERVICE)); // cut short
});

test("reconnect backoff doubles from 1 s and caps at 30 s", () => {
  assert.deepEqual([0, 1, 2, 3, 4, 5, 6, 20].map(backoffMs), [1000, 2000, 4000, 8000, 16000, 30000, 30000, 30000]);
});
