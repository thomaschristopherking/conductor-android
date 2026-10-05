# Conductor for Android

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

A native Android client for [Conductor Cloud](https://conductor.build). Use it to watch and steer your cloud coding agents from a phone.

> **Verification status.** The build machine had no emulator and no device. I verified the app with `./gradlew lint testDebugUnitTest assembleDebug` only. Nobody has opened the app on a real device yet. See [Known limitations](#known-limitations).

## What the app does

- **Settings.** Paste a Conductor API key. The app tests the key with a read-only call (`GET /me`) before it saves the key. The key is encrypted with AES-GCM. The AES key stays in the Android Keystore. You can clear the key.
- **Workspaces (home screen).** All your workspaces, grouped by the sections that you made in the Conductor app. The sections come in the order that the API returns them. Workspaces with no section go under "Other workspaces". Each row shows the name, repository, status badge and last activity. Pull down to refresh. A filter chip shows archived workspaces. If the sections do not load, the list shows all workspaces with no headers and a message.
- **Projects.** The list of repositories. The list button on the home screen opens it. A project opens the workspaces of that repository. "New workspace" on the home screen asks for a project first.
- **New workspace.** Name, branch, agent, model, effort and a first prompt. Your favourite models from the Conductor app are shortcuts.
- **Workspace detail.** The sessions of a workspace, each with its agent status. Create a session, rename the workspace, archive the workspace, or copy and share its Conductor link.
- **Session.** The transcript as a chat, with markdown and code blocks. A composer at the bottom moves up with the keyboard. "Stop" cancels the agent's turn.
  - The chat opens at the newest message. New messages stay in view while you are at the newest message.
  - When you scroll back to older messages, new messages do not move the chat. A round arrow button takes you back to the newest message. When you send a prompt, the chat goes to the newest message.
  - While the agent works, the app polls every 3 seconds for new messages only.
  - The badge shows "Working", "Waiting for you" (the API status `idle`) or "Error".
- **Notifications (optional).** Tap the star on a session. The app checks starred sessions every 15 minutes. It notifies you when an agent stops working.

## Install

Make sure that you can install apps from unknown sources on the device.

1. Get `dist/conductor-android-debug.apk` from this repository, or from the prerelease on the Releases page.
2. Install it with one of these methods:
   - With USB debugging: `adb install -r dist/conductor-android-debug.apk`
   - Without a computer: copy the APK to the phone, open it in a file manager, and accept the prompt.
3. Open **Conductor**.
4. Create an API key at <https://app.conductor.build/home/api-keys>.
5. Paste the key, then tap **Test and save**.

The app needs Android 8.0 (API 26) or later.

## Build

Requirements:

- JDK 17
- The Android SDK with `platforms;android-37.0` and `build-tools;37.0.0`
- `ANDROID_HOME` set to the SDK folder, or a `local.properties` file with `sdk.dir=...`

Commands:

```bash
./gradlew lint testDebugUnitTest assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk dist/conductor-android-debug.apk
```

Do not put the API key in the build. The app reads the key only from its encrypted storage on the device.

## Tests

The unit tests run on the JVM. They do not need a device or the network.

- `data/api/ConductorApiTest.kt` sends every request to a MockWebServer and parses the fixtures.
- `data/transcript/TranscriptParserTest.kt` turns live transcript events into chat rows.
- `data/ModelCatalogTest.kt` compares the agent and model lists with `docs/openapi.json`. It fails when the spec changes.
- `ui/session/SessionViewModelTest.kt` tests polling, back-off, sending and cancelling with virtual time.
- `ui/home/WorkspaceGroupsTest.kt` and `ui/home/HomeViewModelTest.kt` test the grouping by section. `ui/home/WorkspaceGroupListTest.kt` shows the grouped list in Robolectric.
- `ui/session/TranscriptScrollTest.kt` shows the chat list in Robolectric. It tests the scroll position and the button that goes to the newest message.

The fixtures in `app/src/test/resources/fixtures/` are scrubbed copies of live API responses. To capture them again, run:

```bash
CONDUCTOR_API_KEY=... python3 scripts/capture_fixtures.py
```

The script sends only `GET` requests. It replaces names, e-mail addresses, repository URLs and free text. It stops if the key appears in a fixture.

## Project layout

| Path | Contents |
| --- | --- |
| `docs/` | The OpenAPI spec, the Conductor API and Cloud pages, and `API_NOTES.md` |
| `app/src/main/.../data/api` | Retrofit interface, models, error mapping, retry |
| `app/src/main/.../data/transcript` | The parser from transcript events to chat rows |
| `app/src/main/.../data/settings` | Encrypted key storage and starred sessions |
| `app/src/main/.../ui` | Compose screens and view models |
| `app/src/main/.../notify` | The background check for starred sessions |
| `scripts/capture_fixtures.py` | Fixture capture |
| `dist/` | The debug APK |

## Assumptions

I made these decisions without a person to ask.

1. **Package name.** `build.conductor.android.client`. It conflicts with nothing in this repository.
2. **SDK levels.** `minSdk` 26. `compileSdk` and `targetSdk` 37, the newest stable platform on 2026-10-05.
3. **Workspaces of a project.** The app calls `GET /v0/workspaces?repo={projectId}`, not `GET /v0/projects/{id}/workspaces`. The second endpoint mixes archived workspaces into the results and has no archive filter. See `docs/API_NOTES.md`.
4. **Branch.** No workspace endpoint returns a branch. The workspace list shows the repository instead.
5. **Session status.** The session object has no status. The app calls `GET /v0/sessions/{id}/status` for each session, six calls at a time.
6. **Transcript.** The app shows prompts, assistant text, tool calls, tool results and turn ends. It hides thinking, system events, rate-limit events and subagent events. An unknown event with text shows as a grey note. I had no Codex or Cursor session to test with.
7. **Polling.** The first wait is 3 seconds. A poll with no new messages makes the next wait 1.5 times longer, up to 15 seconds. A failed poll doubles the wait, up to 60 seconds. New messages reset the wait to 3 seconds.
8. **Polling after a send.** A queued message reports `idle` until Conductor delivers it. After a send, the app continues to poll for up to 10 polls while the status is `idle`. It stops early when it sees `working` or the end of a turn.
9. **When polling stops.** Polling stops when the status is `idle` or `error`, or when the screen is not visible. The refresh button polls once.
10. **Agents and models.** The pickers list the agent, model and effort values from the OpenAPI spec. The `acp` agent is left out, because the spec lists no models for it. Your favourite models appear first as shortcuts.
11. **Create forms.** The create-workspace form sends `projectId`, `name`, `branch`, `agent`, `model`, `effort` and `message`. It does not offer `repositoryUrl`, `env`, `access` or `fastMode`. The create-session form does not offer `fastMode`.
12. **Retries.** The app retries a `GET` two times after HTTP 429, 502, 503 or 504. It never retries a `POST` automatically, because creating a workspace is not idempotent.
13. **Rejected key.** After HTTP 401, the app opens Settings with a notice. The saved key stays until you replace it or clear it.
14. **Deep links.** Android cannot open `conductor://` links. "Open in Conductor" copies or shares the link, so you can open it on a computer.
15. **Backup.** App data is not backed up. A restored key cannot be decrypted without the Keystore key.
16. **Scope of actions.** Rename and archive apply to workspaces, as the task describes. The app does not rename or archive sessions, and it does not show archived sessions.

## Known limitations

- **Not run on a device.** The build machine had no emulator. Layout, keyboard behaviour, dynamic colour and notifications are not checked on a screen.
- **Write calls are not checked against the live API.** I used the live API for read-only calls only. Create, rename, archive, send and cancel are checked against MockWebServer and the OpenAPI spec only.
- **Long transcripts.** The session screen loads the full transcript when it opens, 100 messages for each request.
- **No offline cache.** Each screen loads from the network.
- **Debug build only.** The APK is signed with the debug key.
- **Background checks.** Android runs periodic work at most every 15 minutes, and it can delay the work to save battery. The session screen records each status that it sees, so a short turn that you start from the app is caught. A turn that starts and ends between two checks while the app is closed is not caught.
- **Code blocks.** Code blocks have no syntax colours.

## Contributing

[`CONTRIBUTING.md`](CONTRIBUTING.md) tells you how to set up the project and what a pull request needs. Each commit must have a [Developer Certificate of Origin](DCO) sign-off (`git commit -s`). You do not sign a contributor agreement.

- [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md): the Contributor Covenant, and how to report a problem.
- [`SECURITY.md`](SECURITY.md): how to report a vulnerability privately.

## License

Apache License 2.0. See [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE).
