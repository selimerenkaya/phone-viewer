import { test } from "node:test";
import assert from "node:assert/strict";
import { toFraction } from "./index";

test("pointer maps to the full box when aspect ratios match", () => {
  const box = { left: 10, top: 20, width: 540, height: 1200 };
  assert.deepEqual(toFraction(10, 20, box, 1080, 2400), { x: 0, y: 0 });
  assert.deepEqual(toFraction(550, 1220, box, 1080, 2400), { x: 1, y: 1 });
});

test("letterboxed: portrait video centred in a wide box", () => {
  // 1000x1000 box, 500x1000 video → drawn 500 wide, starting at x=250
  const box = { left: 0, top: 0, width: 1000, height: 1000 };
  assert.deepEqual(toFraction(250, 0, box, 500, 1000), { x: 0, y: 0 });
  assert.deepEqual(toFraction(500, 500, box, 500, 1000), { x: 0.5, y: 0.5 });
  assert.ok(toFraction(100, 500, box, 500, 1000).x < 0); // on the bar, outside the video
});

test("letterboxed: landscape video in a tall box", () => {
  const box = { left: 0, top: 0, width: 400, height: 800 };
  // 2400x1080 → drawn 400x180, top at (800-180)/2 = 310
  const p = toFraction(400, 490, box, 2400, 1080);
  assert.equal(p.x, 1);
  assert.equal(p.y, 1);
});
