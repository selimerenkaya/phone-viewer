import { test } from "node:test";
import assert from "node:assert/strict";
import { createVideoFeature } from "./video";
import { createControlFeature } from "./control";
import { createNotifyFeature } from "./notify";

test("every lane exposes the Feature contract", () => {
  for (const create of [createVideoFeature, createControlFeature, createNotifyFeature]) {
    const f = create(() => {});
    assert.equal(typeof f.onConnected, "function");
    assert.equal(typeof f.onMessage, "function");
    assert.equal(typeof f.onDisconnected, "function");
  }
});
