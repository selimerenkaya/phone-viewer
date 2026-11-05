# Wire protocol v1

The contract between `android/` and `desktop/`. **Only the lead changes this file.** An agent that needs a change asks the lead first, and both sides update in one PR.

## Transport

- The phone is the **server**: it listens on TCP **27183**. The PC is the **client**.
- Mutual TLS 1.3. Each side has a self-signed certificate. Neither side uses a CA. Trust comes only from pairing (below).
- One connection carries everything.

## Framing

Every message is one frame:

```
+--------+----------------+-----------------+
| type   | length         | payload         |
| u8     | u32 big-endian | `length` bytes  |
+--------+----------------+-----------------+
```

- Max `length` is 8 MiB. A larger value means a bug or an attack: close the connection.
- Unknown `type`: ignore the frame and keep going (lets a newer peer talk to an older one).
- Payloads marked **JSON** are UTF-8 JSON objects. Payloads marked **binary** are raw bytes.

## Message types

| type | Name | Direction | Payload | Owner lane |
|---|---|---|---|---|
| 0x01 | `HELLO` | both | JSON `{ "version": 1, "name": "Pixel 8" }` | link |
| 0x02 | `PAIR_REQUEST` | PC → phone | JSON `{ "pcName": "DESKTOP-1" }` | link |
| 0x03 | `PAIR_RESULT` | phone → PC | JSON `{ "ok": true }` | link |
| 0x04 | `PING` | both | empty | link |
| 0x05 | `PONG` | both | empty | link |
| 0x10 | `VIDEO_CONFIG` | phone → PC | JSON `{ "codec": "avc1.42E01F", "width": 1080, "height": 2400 }` | video |
| 0x11 | `VIDEO_FRAME` | phone → PC | binary: u8 flags (bit0 = key frame) · u64 BE timestamp µs · H.264 Annex B access unit | video |
| 0x12 | `VIDEO_START` | PC → phone | JSON `{ "maxSize": 1920, "bitrate": 8000000 }` | video |
| 0x13 | `VIDEO_STOP` | PC → phone | empty | video |
| 0x20 | `TOUCH` | PC → phone | JSON `{ "action": "down"\|"move"\|"up", "x": 0.0-1.0, "y": 0.0-1.0 }` (x, y are fractions of the screen) | control |
| 0x21 | `GLOBAL` | PC → phone | JSON `{ "action": "back"\|"home"\|"recents"\|"notifications"\|"power_dialog" }` | control |
| 0x22 | `TEXT` | PC → phone | JSON `{ "text": "hello" }` (inserted into the focused field) | control |
| 0x23 | `SCROLL` | PC → phone | JSON `{ "x": 0.5, "y": 0.5, "dy": -1.0 }` (dy in screen fractions) | control |
| 0x30 | `NOTIFICATION` | phone → PC | JSON `{ "id": "key", "app": "WhatsApp", "title": "…", "text": "…", "posted": 1730830000000 }` | notify |
| 0x31 | `NOTIFICATION_REMOVED` | phone → PC | JSON `{ "id": "key" }` | notify |
| 0x32 | `CLIPBOARD` | both | JSON `{ "text": "…" }` | notify |

Ranges: `0x01–0x0F` link, `0x10–0x1F` video, `0x20–0x2F` control, `0x30–0x3F` notify. A lane adds new types only inside its own range, and only after the lead updates this table.

## Pairing

1. PC connects. TLS completes with any certificate. Each side now knows both fingerprints: SHA-256 of the DER-encoded certificate.
2. If the phone has already pinned the PC's fingerprint **and** the PC has already pinned the phone's: send `HELLO` and go.
3. Otherwise the PC sends `PAIR_REQUEST`. Both sides show the **pairing code**:
   `code = (first 4 bytes of SHA-256(phoneFingerprint ‖ pcFingerprint), read as u32 BE) mod 1_000_000`, zero-padded to 6 digits.
4. The user checks the codes match and taps **Allow** on the phone. The phone pins the PC and sends `PAIR_RESULT {ok:true}`. The PC pins the phone.
5. Before pairing finishes, any frame other than `PAIR_REQUEST`, `PING` or `PONG` closes the connection.

Shared test vectors (framing bytes and pairing codes) live in `docs/protocol-vectors.json`. Both sides' unit tests must pass against them.
