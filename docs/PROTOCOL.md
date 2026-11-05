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
| 0x01 | `HELLO` | both | JSON `{ "version": 1, "name": "Pixel 8", "paired": true }` (`paired` only from the phone) | link |
| 0x02 | `PAIR_REQUEST` | PC → phone | JSON `{ "pcName": "DESKTOP-1", "commit": "<hex SHA-256(pcNonce)>" }` | link |
| 0x03 | `PAIR_RESULT` | phone → PC | JSON `{ "ok": true }` | link |
| 0x04 | `PING` | both | empty | link |
| 0x05 | `PONG` | both | empty | link |
| 0x06 | `PAIR_NONCE` | phone → PC | JSON `{ "nonce": "<hex phoneNonce>" }` | link |
| 0x07 | `PAIR_REVEAL` | PC → phone | JSON `{ "nonce": "<hex pcNonce>" }` | link |
| 0x10 | `VIDEO_CONFIG` | phone → PC | JSON `{ "codec": "avc1.640028", "width": 1080, "height": 2400 }` | video |
| 0x11 | `VIDEO_FRAME` | phone → PC | binary: u8 flags (bit0 = key frame) · u64 BE timestamp µs · H.264 Annex B access unit (see Video rules) | video |
| 0x12 | `VIDEO_START` | PC → phone | JSON `{ "maxSize": 1920, "bitrate": 8000000 }` | video |
| 0x13 | `VIDEO_STOP` | PC → phone | empty | video |
| 0x20 | `TOUCH` | PC → phone | JSON `{ "action": "down"\|"move"\|"up", "x": 0.0-1.0, "y": 0.0-1.0 }` (x, y are fractions of the screen) | control |
| 0x21 | `GLOBAL` | PC → phone | JSON `{ "action": "back"\|"home"\|"recents"\|"notifications"\|"power_dialog" }` | control |
| 0x22 | `TEXT` | PC → phone | JSON `{ "text": "hello" }` (inserted into the focused field) | control |
| 0x23 | `SCROLL` | PC → phone | JSON `{ "x": 0.5, "y": 0.5, "dy": -1.0 }` (dy = finger travel in screen fractions, -1..1; wheel down → negative dy) | control |
| 0x24 | `KEY` | PC → phone | JSON `{ "key": "backspace"\|"enter" }` (acts on the focused field) | control |
| 0x30 | `NOTIFICATION` | phone → PC | JSON `{ "id": "key", "app": "WhatsApp", "title": "…", "text": "…", "posted": 1730830000000 }` | notify |
| 0x31 | `NOTIFICATION_REMOVED` | phone → PC | JSON `{ "id": "key" }` | notify |
| 0x32 | `CLIPBOARD` | both | JSON `{ "text": "…" }` | notify |

Ranges: `0x01–0x0F` link, `0x10–0x1F` video, `0x20–0x2F` control, `0x30–0x3F` notify. A lane adds new types only inside its own range, and only after the lead updates this table.

## Discovery

The phone advertises the DNS-SD service `_phoneviewer._tcp` on port 27183, with the phone name as the instance name and no TXT records. Discovery only finds addresses; trust still comes only from the pinned fingerprint.

## Video rules

- The phone puts the latest SPS and PPS (the H.264 setup blocks) in front of **every** key frame, inside the same `VIDEO_FRAME`. So the PC decoder can start or restart at any key frame. The encoder's separate codec-config buffer is never sent on its own.
- `VIDEO_CONFIG.codec` is built from the real SPS, not hard-coded: `"avc1." + hex(SPS bytes 1, 2, 3)` (profile, constraint flags, level), lowercase.
- The phone sends a new `VIDEO_CONFIG` before the first frame and whenever the size changes (e.g. rotation), and the next frame is a key frame. The PC reconfigures its decoder on every `VIDEO_CONFIG`.

## Connecting and pairing

A fingerprint is the SHA-256 of a peer's DER-encoded certificate. A nonce is 32 random bytes from a secure random source, sent as lowercase hex.

1. The PC connects. TLS completes with any certificate, and each side reads the other's fingerprint.
2. **The phone speaks first:** `HELLO { version, name, paired }`. `paired` is true only if the phone has pinned this PC's fingerprint.
   - If `version` differs from the PC's, the PC shows "update the app" and closes the connection.
3. If `paired` is true **and** the PC has pinned this phone's fingerprint, the PC replies `HELLO` and the session starts.
4. Otherwise the PC deletes any old pin for this phone and pairs:
   1. PC → `PAIR_REQUEST { pcName, commit = SHA-256(pcNonce) }`. The PC chooses its nonce **before** it sees the phone's.
   2. Phone → `PAIR_NONCE { phoneNonce }`.
   3. PC → `PAIR_REVEAL { pcNonce }`. The phone checks `SHA-256(pcNonce) == commit` and closes the connection if it doesn't match.
   4. Both sides show the **pairing code**:
      `code = (first 4 bytes of SHA-256(phoneFP ‖ pcFP ‖ phoneNonce ‖ pcNonce), as u32 BE) mod 1_000_000`, zero-padded to 6 digits. `‖` joins raw bytes, not hex.
   5. The user checks the two codes match and taps **Allow** on the phone. The phone pins the PC and sends `PAIR_RESULT { ok: true }`; the PC pins the phone and sends `HELLO`. If the user taps **Deny**, the phone sends `{ ok: false }` and closes.
5. Until the session starts, only link frames (`0x01–0x0F`) are allowed, and only in the order above. Anything else closes the connection.

Why the commit: without it, an attacker in the middle could try certificates until both screens show the same code. With it, each side's nonce is fixed before the other's is known, so an attacker wins only 1 time in 1,000,000 per attempt.

Shared test vectors (framing bytes, pairing codes, codec strings) live in `docs/protocol-vectors.json`. Both sides' unit tests must pass against them.
