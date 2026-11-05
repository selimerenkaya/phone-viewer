// Finds phones advertising SERVICE over mDNS (the phone registers it with Android NsdManager).
// Hand-written on dgram: one PTR query, and we read only the PTR answers; the phone's address is the packet's source.
import dgram from "node:dgram";

export const SERVICE = "_phoneviewer._tcp.local";
const GROUP = "224.0.0.251", PORT = 5353;

/** A DNS query for PTR records of `name`. */
export function ptrQuery(name: string): Buffer {
  const labels = name.split(".").map((l) => Buffer.concat([Buffer.from([Buffer.byteLength(l)]), Buffer.from(l)]));
  // header: id 0, flags 0, 1 question; then the name, type PTR (12), class IN (1)
  return Buffer.concat([Buffer.from([0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0]), ...labels, Buffer.from([0, 0, 12, 0, 1])]);
}

/** Reads a possibly compressed DNS name at `off`; returns its labels and the offset just past it. */
function readName(msg: Buffer, off: number): { labels: string[]; end: number } {
  const labels: string[] = [];
  let end = -1;
  for (let jumps = 0; jumps < 32; ) {
    const len = msg[off];
    if (len === undefined) break;
    if (len === 0) return { labels, end: end < 0 ? off + 1 : end };
    if (len >= 0xc0) {
      if (end < 0) end = off + 2;
      off = msg.readUInt16BE(off) & 0x3fff;
      jumps++;
    } else {
      labels.push(msg.toString("utf8", off + 1, off + 1 + len));
      off += 1 + len;
    }
  }
  throw new Error("bad DNS name");
}

/** Instance names (e.g. "Pixel 8") from the PTR answers for `service` in an mDNS message. Throws on a malformed message. */
export function parsePtrAnswers(msg: Buffer, service: string): string[] {
  const names: string[] = [];
  let off = 12;
  for (let i = msg.readUInt16BE(4); i > 0; i--) off = readName(msg, off).end + 4; // skip questions
  const records = msg.readUInt16BE(6) + msg.readUInt16BE(8) + msg.readUInt16BE(10);
  for (let i = 0; i < records; i++) {
    const { labels, end } = readName(msg, off);
    const type = msg.readUInt16BE(end);
    const rdata = end + 10;
    if (type === 12 && labels.join(".").toLowerCase() === service) names.push(readName(msg, rdata).labels[0]);
    off = rdata + msg.readUInt16BE(end + 8);
  }
  return names;
}

/** Asks the LAN for phones every 5 s and calls `found(name, ip)` for each answer. */
export function discover(found: (name: string, ip: string) => void): void {
  const sock = dgram.createSocket({ type: "udp4", reuseAddr: true });
  sock.on("message", (msg, from) => {
    try {
      for (const name of parsePtrAnswers(msg, SERVICE)) found(name, from.address);
    } catch {
      // not a DNS message we understand; other mDNS traffic shares this port
    }
  });
  sock.on("error", () => sock.close()); // shortcut: no discovery if port 5353 can't be shared; the IP field still works
  sock.bind(PORT, () => {
    sock.addMembership(GROUP); // shortcut: default interface only; join every IPv4 interface if PCs with several NICs miss phones
    const ask = () => sock.send(ptrQuery(SERVICE), PORT, GROUP);
    ask();
    const timer = setInterval(ask, 5_000);
    sock.on("close", () => clearInterval(timer));
  });
}
