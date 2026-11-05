# Project plan

## Goal (v1)

From a Windows PC on the same Wi-Fi as an Android phone:

1. **View** the phone screen live.
2. **Control** the phone with mouse and keyboard.
3. **See notifications** from the phone, and **sync the clipboard** both ways.

Out of scope for v1: iPhone, internet/remote access (LAN only), audio, file transfer, multiple phones at once, rooted-only tricks.

## Stack

| Side | Choice | Why |
|---|---|---|
| Phone | Android app, Kotlin, minSdk 29 (Android 10) | Has the APIs below without root. |
| PC | Electron + TypeScript | Chromium's built-in `WebCodecs` decodes H.264 in hardware, so the PC side needs no video library. Node's `tls`/`net` handle the socket. |
| Link | One TLS-over-TCP connection, port 27183 | One socket, one security layer, simple framing. See [PROTOCOL.md](PROTOCOL.md). |

## How each feature works

| Feature | Phone (Android API) | PC |
|---|---|---|
| Screen | `MediaProjection` → `MediaCodec` H.264 encoder (Surface input) inside a foreground service | `VideoDecoder` (WebCodecs) → `<canvas>` |
| Control | `AccessibilityService`: `dispatchGesture` (tap, swipe, long press), `performGlobalAction` (back, home, recents), `ACTION_SET_TEXT` on the focused field | Mouse/keyboard events on the canvas, scaled to phone pixels |
| Notifications | `NotificationListenerService` | Windows toast via Electron `Notification` |
| Clipboard | `ClipboardManager` | Electron `clipboard` |
| Pairing | Self-signed cert in Android Keystore | Self-signed cert (`selfsigned` npm package) stored in app data |

## Known platform limits (accept, don't fight)

- **Screen consent:** Android asks the user to allow screen capture every session. Unavoidable without root.
- **Clipboard phone → PC:** Android 10+ blocks background apps from *reading* the clipboard. v1 sends it only while the app is in the foreground, plus a Quick Settings tile "Send clipboard to PC". PC → phone writes work anytime.
- **Typing:** Accessibility can set text in a field and press global buttons, but cannot inject raw key codes. Good enough for typing; games and shortcuts won't work.
- **Accessibility + notification access** must be switched on by the user in Android settings. The app opens the right settings screen.

## Security (not optional, because the PC can *control* the phone)

- Every connection is TLS. No plain-text mode.
- First connection: both sides show a 6-digit code made from both certificate fingerprints and two random nonces, exchanged commit-first so a man in the middle can't steer the code. The user checks they match and taps **Allow** on the phone. After that, each side remembers (pins) the other's fingerprint. Details: [PROTOCOL.md](PROTOCOL.md#connecting-and-pairing).
- An unpaired peer can send only `PAIR`. Everything else closes the connection.
- The phone can revoke a paired PC at any time.

## Milestones

| # | Milestone | Done when | Runs as |
|---|---|---|---|
| M0 | Scaffold | Empty Android app builds and installs; empty Electron window opens; manifest already declares every service and permission v1 needs | Lead, on `main` |
| M1 | Link | PC connects by typing the phone's IP, pairs with the 6-digit code, reconnects without asking again; framing unit-tested on both sides against the shared test vectors | One agent: `link` |
| M2 | Features | Each feature works end to end on a real phone | Three agents in parallel: `video`, `control`, `notify` |
| M3 | Polish | Auto-discovery (Android `NsdManager` + mDNS on PC) replaces typing the IP; reconnect after Wi-Fi drop; Windows installer (`electron-builder`) and a signed APK | Lead, or one agent per item |

Agent workflow: [AGENTS.md](AGENTS.md).

## Testing

- **Unit:** framing and pairing-code math on both sides, checked against `docs/protocol-vectors.json` so Kotlin and TypeScript can't drift apart. Android: JUnit. Desktop: `node --test`.
- **End to end:** done by hand on one real phone per milestone. Emulators cannot do `MediaProjection` well.
