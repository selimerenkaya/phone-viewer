import tls from "node:tls";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { randomBytes, X509Certificate } from "node:crypto";
import type { Feature, Send } from "../feature";
import { makeCert } from "./cert";
import { discover } from "./discovery";
import {
  backoffMs, encodeFrame, FrameReader, pairingCode, sha256,
  HELLO, PAIR_REQUEST, PAIR_RESULT, PING, PONG, PAIR_NONCE, PAIR_REVEAL,
} from "./wire";

const PORT = 27183;
const VERSION = 1;

/** The socket of the running session; null before pairing finishes and after disconnect. */
let session: tls.TLSSocket | null = null;

/** Real Send for the features. Frames sent while no session is up are dropped. */
export const send: Send = (type, payload) => {
  session?.write(encodeFrame(type, payload));
};

/** Our key, certificate and the phone fingerprints we trust, kept in the app's userData folder. */
type Store = { key: string; cert: string; pins: string[] };
const userData = process.argv.find((a) => a.startsWith("--user-data="))!.slice("--user-data=".length);
const storeFile = path.join(userData, "link.json");
const save = (s: Store) => fs.writeFileSync(storeFile, JSON.stringify(s));

function loadStore(): Store {
  if (fs.existsSync(storeFile)) return JSON.parse(fs.readFileSync(storeFile, "utf8"));
  const s = { ...makeCert(os.hostname()), pins: [] };
  fs.mkdirSync(userData, { recursive: true });
  save(s);
  return s;
}

/** link lane: TLS client, pairing, framing; routes each frame to the feature owning its type range. */
export function startLink(features: { video: Feature; control: Feature; notify: Feature }): void {
  const store = loadStore();
  const pcFp = sha256(new X509Certificate(store.cert).raw);
  const routes = [features.video, features.control, features.notify]; // 0x10, 0x20, 0x30
  const status = document.getElementById("status")!;
  const ip = document.getElementById("ip") as HTMLInputElement;
  let current: tls.TLSSocket | null = null;
  /** The phone of the last session, retried after a drop; null once the user connects by hand or trust is gone. */
  let last: { fp: string; name: string; host: string } | null = null;
  let attempt = 0, retry: NodeJS.Timeout | undefined;

  const userConnect = (host: string) => {
    clearTimeout(retry);
    last = null;
    localStorage.setItem("ip", host);
    current?.destroy();
    current = connect(host);
  };
  ip.value = localStorage.getItem("ip") ?? "";
  document.getElementById("connect")!.onclick = () => userConnect(ip.value.trim());

  // Phones found on the LAN, newest address per name, as one button each.
  const phones = new Map<string, string>();
  const list = document.createElement("div");
  document.getElementById("link")!.append(list);
  discover((name, addr) => {
    if (phones.get(name) === addr) return;
    phones.set(name, addr);
    list.replaceChildren(...[...phones].map(([n, a]) => {
      const b = document.createElement("button");
      b.textContent = `${n} (${a})`;
      b.onclick = () => userConnect((ip.value = a));
      return b;
    }));
  });

  /** Connects to `host`. With `expectFp` (a reconnect) it accepts only that already-paired phone and never pairs. */
  function connect(host: string, expectFp?: string): tls.TLSSocket {
    status.textContent = `${expectFp ? "Reconnecting" : "Connecting"} to ${host}…`;
    // Any certificate passes TLS; trust comes only from the pinned fingerprint checked below.
    const s = tls.connect({ host, port: PORT, key: store.key, cert: store.cert, minVersion: "TLSv1.3", rejectUnauthorized: false });
    s.setTimeout(20_000, () => fail("No answer from the phone")); // until TLS is up; the phone may still be dropping an old link
    const reader = new FrameReader();
    const write = (type: number, msg?: object) => s.write(encodeFrame(type, Buffer.from(msg ? JSON.stringify(msg) : "")));
    let state: "hello" | "nonce" | "result" | "session" = "hello";
    let phoneFp: Buffer, phoneHex: string, phoneName = "", pcNonce: Buffer;
    let reason = "Disconnected";
    let lastRx = 0, keepalive: NodeJS.Timeout | undefined;

    const fail = (why: string) => {
      reason = why;
      s.destroy();
    };

    const start = () => {
      write(HELLO, { version: VERSION, name: os.hostname() });
      state = "session";
      session = s;
      for (const f of routes) f.onConnected();
      status.textContent = `Connected to ${phoneName}`;
      last = { fp: phoneHex, name: phoneName, host };
      attempt = 0;
      keepalive = setInterval(() => (Date.now() - lastRx > 15_000 ? fail("Phone stopped responding") : write(PING)), 5_000);
    };

    const onFrame = (type: number, payload: Buffer) => {
      lastRx = Date.now();
      if (state === "session") {
        if (type === PING) write(PONG);
        else if (type >= 0x10 && type < 0x40) routes[(type >> 4) - 1].onMessage(type, payload);
        return; // other link frames and unknown types are ignored
      }
      const msg = JSON.parse(payload.toString("utf8")); // every pre-session frame is JSON; anything else throws and closes
      if (state === "hello" && type === HELLO) {
        if (msg.version !== VERSION) return fail("The phone runs a different app version: update the app");
        phoneName = String(msg.name);
        if (msg.paired === true && store.pins.includes(phoneHex)) return start();
        if (expectFp) {
          last = null; // never re-pair without the user
          return fail("The phone no longer trusts this PC. Click Connect to pair again.");
        }
        store.pins = store.pins.filter((p) => p !== phoneHex);
        save(store);
        pcNonce = randomBytes(32); // chosen before we see the phone's nonce
        write(PAIR_REQUEST, { pcName: os.hostname(), commit: sha256(pcNonce).toString("hex") });
        state = "nonce";
      } else if (state === "nonce" && type === PAIR_NONCE) {
        const code = pairingCode(phoneFp, pcFp, Buffer.from(String(msg.nonce), "hex"), pcNonce);
        write(PAIR_REVEAL, { nonce: pcNonce.toString("hex") });
        status.textContent = `Pairing code ${code}. Check that the phone shows the same code, then tap Allow there.`;
        state = "result";
      } else if (state === "result" && type === PAIR_RESULT) {
        if (msg.ok !== true) return fail("Pairing was denied on the phone");
        store.pins.push(phoneHex);
        save(store);
        start();
      } else fail("Unexpected message from the phone");
    };

    s.on("secureConnect", () => {
      phoneFp = sha256(s.getPeerCertificate().raw);
      phoneHex = phoneFp.toString("hex");
      s.setTimeout(0);
      if (expectFp && phoneHex !== expectFp) return fail(`A different phone answered at ${host}`);
      status.textContent = "Connected, waiting for the phone…";
    });
    s.on("data", (chunk: Buffer) => {
      try {
        for (const f of reader.push(chunk)) onFrame(f.type, f.payload);
      } catch (e) {
        fail(`Connection error: ${(e as Error).message}`);
      }
    });
    s.on("error", (e) => (reason = `Connection error: ${e.message}`));
    s.on("close", () => {
      clearInterval(keepalive);
      if (session === s) {
        session = null;
        for (const f of routes) f.onDisconnected();
      }
      if (current !== s) return;
      if (!last) return void (status.textContent = reason);
      const wait = backoffMs(attempt++);
      status.textContent = `Reconnecting… (${reason}; next try in ${wait / 1000} s)`;
      const phone = last;
      retry = setTimeout(() => (current = connect(phones.get(phone.name) ?? phone.host, phone.fp)), wait);
    });
    return s;
  }
}
