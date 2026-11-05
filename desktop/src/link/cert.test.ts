import { test } from "node:test";
import assert from "node:assert/strict";
import { createPrivateKey, X509Certificate } from "node:crypto";
import { makeCert } from "./cert";

test("makeCert yields a valid self-signed certificate for its key", () => {
  const { key, cert } = makeCert("DESKTOP-1");
  const x = new X509Certificate(cert);
  assert.equal(x.subject, "CN=DESKTOP-1");
  assert.ok(x.verify(x.publicKey));
  assert.ok(x.checkPrivateKey(createPrivateKey(key)));
});
