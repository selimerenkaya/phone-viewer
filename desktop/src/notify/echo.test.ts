import { test } from "node:test";
import assert from "node:assert/strict";
import { EchoGuard } from "./echo";

test("echo guard drops the value that came from the phone", () => {
  const guard = new EchoGuard();
  guard.received("from phone");
  assert.equal(guard.shouldSend("from phone"), false);
  assert.equal(guard.shouldSend("copied on pc"), true);
  assert.equal(guard.shouldSend("copied on pc"), false);
  assert.equal(guard.shouldSend("from phone"), true);
});
