// Self-signed EC P-256 certificate built with Node crypto, so no cert package is needed.
// Its contents barely matter: peers trust it only by its pinned SHA-256 fingerprint.
import { generateKeyPairSync, randomBytes, sign } from "node:crypto";

/** One DER TLV. Lengths up to 64 KiB, plenty for this certificate. */
function der(tag: number, ...parts: Buffer[]): Buffer {
  const body = Buffer.concat(parts);
  const n = body.length;
  const len = n < 0x80 ? [n] : n < 0x100 ? [0x81, n] : [0x82, n >> 8, n & 0xff];
  return Buffer.concat([Buffer.from([tag, ...len]), body]);
}

export function makeCert(name: string): { key: string; cert: string } {
  const { privateKey, publicKey } = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const ecdsaSha256 = der(0x30, Buffer.from("06082a8648ce3d040302", "hex"));
  const cn = der(0x30, der(0x31, der(0x30, Buffer.from("0603550403", "hex"), der(0x0c, Buffer.from(name)))));
  const serial = randomBytes(8);
  serial[0] = (serial[0] & 0x3f) | 0x40; // positive and minimal, as DER requires
  const tbs = der(
    0x30,
    der(0xa0, der(0x02, Buffer.from([2]))), // v3
    der(0x02, serial),
    ecdsaSha256,
    cn,
    der(0x30, der(0x17, Buffer.from("250101000000Z")), der(0x18, Buffer.from("20991231235959Z"))),
    cn,
    publicKey.export({ type: "spki", format: "der" }),
  );
  const sig = sign("sha256", tbs, privateKey);
  const cert = der(0x30, tbs, ecdsaSha256, der(0x03, Buffer.from([0]), sig));
  const pem = `-----BEGIN CERTIFICATE-----\n${cert.toString("base64").match(/.{1,64}/g)!.join("\n")}\n-----END CERTIFICATE-----\n`;
  return { key: privateKey.export({ type: "pkcs8", format: "pem" }) as string, cert: pem };
}
