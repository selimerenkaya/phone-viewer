# Agent architecture (git worktrees)

Several Claude Code agents build this project at the same time. Each agent works in its own **git worktree**: a separate folder with its own branch, sharing one `.git`. So agents never overwrite each other's files, and each one's work lands on `main` through its own pull request.

## Roles

| Agent | Branch | Worktree folder | Owns (may edit freely) | Milestone |
|---|---|---|---|---|
| **lead** | `main` | the main checkout | `docs/`, `CLAUDE.md`, every file in "Shared files" below, merging PRs | all |
| **link** | `agent/link` | `.worktrees/link` | `android/…/link/`, `desktop/src/link/` | M1 |
| **video** | `agent/video` | `.worktrees/video` | `android/…/video/`, `desktop/src/video/` | M2 |
| **control** | `agent/control` | `.worktrees/control` | `android/…/control/`, `desktop/src/control/` | M2 |
| **notify** | `agent/notify` | `.worktrees/notify` | `android/…/notify/`, `desktop/src/notify/` | M2 |

`android/…` = `android/app/src/main/java/io/github/selimerenkaya/phoneviewer/` (tests: the same path under `src/test/`).

Each lane is **vertical**: one agent owns one feature on *both* the phone and the PC. It can then test its feature end to end alone, and no two agents have to agree on a half-finished interface.

## Shared files (lead only)

These are the files every lane would want to touch. The lead creates them in M0 with every lane's needs already filled in, so lanes rarely need them:

- `docs/PROTOCOL.md`, `docs/protocol-vectors.json`
- `android/app/src/main/AndroidManifest.xml` (every service and permission for v1 is declared in M0)
- `android/**/build.gradle.kts`, `android/settings.gradle.kts`, `desktop/package.json`
- `android/…/MainActivity.kt`, `desktop/src/main.ts`, `desktop/src/renderer/` (UI shell and wiring)

A lane that truly needs a change here (a new dependency, a new permission) **adds lines only, never reorders or reformats**, and lists the change at the top of its PR description. The lead resolves any conflict at merge.

## Contract between lanes

In M0 the lead creates one stub per lane with a fixed shape. Lanes fill in the body, not the signature:

```kotlin
// android/…/video/VideoFeature.kt (same for control/, notify/)
class VideoFeature(private val send: (type: Int, payload: ByteArray) -> Unit) {
    fun onMessage(type: Int, payload: ByteArray) { /* lane fills in */ }
    fun onDisconnected() {}
}
```

```ts
// desktop/src/video/index.ts (same for control/, notify/)
export function createVideoFeature(send: (type: number, payload: Uint8Array) => void) {
  return { onMessage(type: number, payload: Uint8Array) {}, onDisconnected() {} };
}
```

The `link` lane routes each incoming frame to the feature that owns its type range (see PROTOCOL.md). Lanes talk to each other **only** through the protocol. They never import each other's code.

## Order

```
M0 lead: scaffold + stubs + manifest ──► M1 link ──► M2 video ┐
                                                     control ├──► M3 lead: discovery, reconnect, installers
                                                     notify  ┘
```

M2 lanes start from `main` **after** `agent/link` is merged, so they can run against a real connection.

## Commands

The lead creates a lane (from the main checkout):

```bash
git worktree add .worktrees/video -b agent/video main
```

Start an agent in it (a separate terminal, or a new Claude Code session opened on that folder):

```bash
cd .worktrees/video && claude "You are the video agent. Read CLAUDE.md and docs/AGENTS.md, then build milestone M2 for your lane."
```

The agent finishes its lane:

```bash
git fetch origin && git merge origin/main
git push -u origin agent/video
gh pr create --base main --title "video: screen mirroring" --body "..."
```

After the lead merges, clean up:

```bash
git worktree remove .worktrees/video && git branch -d agent/video
```

## Definition of done (every lane)

1. Only owned paths changed (plus any listed shared-file lines).
2. Unit tests pass: `./gradlew test` in `android/`, `npm test` in `desktop/`.
3. The feature was tried by hand on a real phone, and the PR says what was tried.
4. `main` merged into the branch, with no conflicts left.
