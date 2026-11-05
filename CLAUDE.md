# phone-viewer — rules for every agent

Android phone ↔ Windows PC over LAN: screen view, remote control, notifications, clipboard. Plan: `docs/PLAN.md`. Protocol: `docs/PROTOCOL.md`.

1. **Know your lane.** Your branch name tells you your role (`agent/<lane>`, or `main` = lead). Edit only the paths your lane owns in `docs/AGENTS.md`.
2. **The protocol is the contract.** Never change `docs/PROTOCOL.md` or the frame format on your own. If you need a change, stop and ask the user/lead.
3. **Never weaken security.** Always TLS, always pairing, no debug bypass, no plain-text fallback. The PC can control the phone.
4. **Test against the shared vectors.** Framing and pairing-code tests must use `docs/protocol-vectors.json`.
5. **Commands:** `android/` → `./gradlew assembleDebug test`. `desktop/` → `npm install && npm test && npm start`. Android SDK: `C:\Android\Sdk`. JDK 17.
6. **Finish:** merge `origin/main`, push your branch, open a PR to `main`. Never push to `main` from a lane.
