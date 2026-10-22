# Conductor for Android

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

A native Android client for [Conductor Cloud](https://conductor.build). Use it to watch and steer your cloud coding agents from a phone.

> **Verification status.** The build machine had no emulator and no device. I verified the app with `./gradlew lint testDebugUnitTest assembleRelease` only. Nobody has opened the app on a real device yet. See [Known limitations](#known-limitations).

## What the app does

- **Settings.** Paste a Conductor API key. The app tests the key with a read-only call (`GET /me`) before it saves the key. The key is encrypted with AES-GCM. The AES key stays in the Android Keystore. You can clear the key.
  - An optional GitHub token shows the pull requests and changes of a workspace. The app tests it with `GET /user` and encrypts it in the same way as the API key.
- **Colour scheme.** Settings has three colour schemes. "Default" uses your wallpaper colours on Android 12 and later. "Neapolitan" uses strawberry, vanilla and chocolate. "Spumoni" uses pistachio, fior di latte and cherry. Both ice cream schemes have a light and a dark version.
- **Workspaces (home screen).** All your workspaces, grouped by the sections that you made in the Conductor app. The sections come in the order that the API returns them. Workspaces with no section go under "Other workspaces". Each row shows the name, repository, status badge and last activity. Pull down to refresh. A filter chip shows archived workspaces. If the sections do not load, the list shows all workspaces with no headers and a message.
  - When the app starts, the list from the last start shows at once. A spinner at the top shows that the app loads the new list. With no connection, the old list stays and a message shows.
- **Projects.** The list of repositories. The list button on the home screen opens it. A project opens the workspaces of that repository. The "+" button at the top of the home screen asks for a project first.
- **Archive.** Swipe a workspace to the left on the home screen or on the list of a project. The workspace screen menu also has "Archive workspace". It goes back to the list.
  - An "Undo" button shows for 5 seconds. The app sends the archive request only when the button goes away.
  - The workspace leaves every list at once. If the request fails, the workspace comes back and a message shows.
  - An archive can take more than 10 seconds, because Conductor stops the machine first. The app waits up to 120 seconds. If the connection drops, the app reads the workspace, and an archived state counts as a success.
- **Workspaces of a project.** The same rows as the home screen, for one repository.
- **New workspace.** Name, branch, agent, model, effort and a first prompt. Your favourite models from the Conductor app are shortcuts.
- **Workspace.** A workspace opens directly at a session. Each session has a tab below the title bar, as in the Conductor app.
  - Tap a tab, or swipe left or right, to go to a different session.
  - A spinner on a tab shows that its agent works. A coloured dot shows a question for you. A red dot shows an error.
  - The "+" tab creates a session. The new session opens in a new tab.
  - The first tab to open is the session from a notification. If there is no notification, it is the session with a question, then a working session, then the first session.
  - When you open a workspace again, its tabs from the last visit show at once while the app reads them again.
  - The tabs show when the status of each session is known. The app then reads the transcripts of the other working sessions to find questions. It waits for those transcripts first only when two or more agents work, because then a question decides the first tab.
  - The menu renames the workspace, archives it, or copies and shares the Conductor link of the open session.
- **Pull request and stack (needs a GitHub token).** A bar under the title shows the pull request of the workspace: its state, number, title and the added and removed lines.
  - The app finds the pull request through the Conductor link of the workspace (`conductor.build/.../w/{number}`) in the pull request body. If no body has the link, it uses a pull request whose branch ends with the workspace name. It reads the 100 most recently updated pull requests of the repository.
  - If the pull request is part of a stack, the bar shows its position, for example "2/3". Tap the bar to open the stack menu, as in the Conductor desktop app. The menu lists the pull requests from the top of the stack down to the base branch. Choose one to show it in the bar and in the changes.
  - The app reads the stack from GitHub's stack endpoint, which `gh stack` uses. Without a stack on GitHub, open pull requests whose base is the branch of another one make the stack.
  - Tap the line numbers to open the changes. The changes show each file with its added and removed lines. "Folders" groups the files by folder, with the totals of each folder. "Files" is one list in path order. "Type" groups the files into added, modified, renamed and removed. Tap a folder to close or open it. Tap a file to see its diff.
  - Switching to a different pull request of the stack does not change the branch of the workspace.
- **Session.** The transcript as a chat, with markdown and code blocks. A composer at the bottom moves up with the keyboard. "Stop" cancels the agent's turn.
  - Touch and hold a message to select its text. Each message has a copy button below it.
  - Code blocks wrap long lines. Each code block has a copy button that copies only the code.
  - A picture from a web address (`https://...`) shows in the message. Tap it to open it in the browser.
  - A picture from a file on the cloud machine shows as a card with the file name. The public API cannot send workspace files, so the phone cannot show the picture.
  - The chat opens at the newest message. New messages stay in view while you are at the newest message.
  - The app keeps the 12 chats that you opened last in memory. When you open one of them again, it shows at once, and the app reads only the newer messages.
  - When you scroll back to older messages, new messages do not move the chat. A round arrow button takes you back to the newest message. When you send a prompt, the chat goes to the newest message.
  - When the agent asks you a question, the chat shows the question with its options. Choose the options, or type in "Other", then tap "Send answers".
  - While the agent works, the app polls every 3 seconds for new messages only.
  - The badge shows "Working", "Waiting for you" (the API status `idle`) or "Error".
- **Notifications (optional).** Tap the bell at the top of a workspace to subscribe to it. The app notifies you when an agent in that workspace stops working, stops with an error, or asks you a question. Tap the bell again to stop the notifications.
  - Conductor sends no push messages, so the app checks the workspaces itself. A background service does the checks while the app is closed. Android shows a small notification, "Watching subscribed workspaces", while the service runs. You can hide that notification in the system settings of the app.
  - While an agent works, the app checks every 15 seconds and keeps the phone awake between checks. When no agent works, the app checks every 60 seconds and lets the phone sleep.
  - Each check reads one workspace object. The app reads the sessions of a workspace only when its activity time changed or an agent worked at the last check.
  - The app also notifies you about a turn that started and ended between two checks. The status of a session gets a new time with each turn.
  - The service starts again after a restart of the phone and after an update of the app. A check every 15 minutes continues if Android stops the service.
  - An archived or deleted workspace stops its subscription.

## Install

Make sure that you can install apps from unknown sources on the device.

1. Get `dist/conductor-android.apk` from this repository, or from the prerelease on the Releases page.
2. Install it with one of these methods:
   - With USB debugging: `adb install -r dist/conductor-android.apk`
   - Without a computer: copy the APK to the phone, open it in a file manager, and accept the prompt.
3. Open **Conductor**.
4. Create an API key at <https://app.conductor.build/home/api-keys>.
5. Paste the key, then tap **Test and save**.

The app needs Android 8.0 (API 26) or later.

## Build

Requirements:

- JDK 21. The markdown library contains Java 21 classes, and the Robolectric tests load them.
- The Android SDK with `platforms;android-37.0` and `build-tools;37.0.0`
- `ANDROID_HOME` set to the SDK folder, or a `local.properties` file with `sdk.dir=...`

Commands:

```bash
./gradlew lint testDebugUnitTest assembleRelease
cp app/build/outputs/apk/release/app-release.apk dist/conductor-android.apk
```

The release build is smaller and faster than the debug build. R8 shrinks and optimises the code, and Android can compile the app ahead of time. The project has no release key, so the debug key of the build machine signs the release build.

Do not put the API key in the build. The app reads the key only from its encrypted storage on the device.

## Tests

The unit tests run on the JVM. They do not need a device or the network.

- `data/api/ConductorApiTest.kt` sends every request to a MockWebServer and parses the fixtures. It also tests the copies of a slow `GET`, and the transcript pages that the app reads 8 at a time.
- `data/api/HedgePolicyTest.kt` tests when the app sends a copy of a slow request, with virtual time.
- `data/transcript/TranscriptParserTest.kt` turns live transcript events into chat rows.
- `data/ModelCatalogTest.kt` compares the agent and model lists with `docs/openapi.json`. It fails when the spec changes.
- `ui/session/SessionViewModelTest.kt` tests polling, back-off, sending and cancelling with virtual time.
- `notify/AgentAlertsTest.kt` tests which status changes make a notification. `notify/WorkspaceWatcherTest.kt` tests the background check against the fake API. `notify/WorkspaceWatchServiceTest.kt` starts the service in Robolectric.
- `data/settings/WorkspaceSubscriptionTest.kt` tests how a check updates a saved subscription.
- `ui/home/WorkspaceGroupsTest.kt` and `ui/home/HomeViewModelTest.kt` test the grouping by section, and the list from the last start. `data/FileHomeSnapshotsTest.kt` tests the file that keeps that list. `ui/home/WorkspaceGroupListTest.kt` shows the grouped list in Robolectric.
- `ui/session/TranscriptScrollTest.kt` shows the chat list in Robolectric. It tests the scroll position and the button that goes to the newest message.
- `ui/session/MessageImageTest.kt` tests the card for a picture that is a workspace file.
- `ui/session/CodeBlockTest.kt` tests that a message shows in its first frame, that a long code line wraps, and that the copy buttons copy the code or the full message.
- `ui/workspace/SessionTabRowTest.kt` and `ui/workspace/WorkspaceDetailViewModelTest.kt` test the session tabs, which tab opens first, and which tabs the app scans for questions.

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
| `app/src/main/.../data/settings` | Encrypted key storage and workspace subscriptions |
| `app/src/main/.../ui` | Compose screens and view models |
| `app/src/main/.../notify` | The background service and the periodic check for subscribed workspaces |
| `scripts/capture_fixtures.py` | Fixture capture |
| `dist/` | The release APK |

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
17. **Archive by swipe.** The app waits until the "Undo" button goes away, then it sends the archive request. Archive stops the cloud machine, so an undo after the request would restart the machine. The app does not use `POST /v0/workspaces/{id}/unarchive`.
18. **Colour scheme.** The Neapolitan and Spumoni schemes are fixed palettes. They do not use the wallpaper colours. Each text colour meets the WCAG AA contrast ratio of 4.5 to 1 on its background.
19. **Answers to questions.** The public API cannot answer Conductor's question form. The app sends your answers as one message. The message closes the form, and the agent reads the answers from the message. See `docs/API_NOTES.md`.
20. **Slow requests.** The live API sometimes answers a request only after 2 to 8 seconds, at random. When a `GET` has no answer after 1 second, the app sends a copy. After 2 seconds, it sends a third copy. The first answer wins, and the app cancels the other copies. A `POST` is sent once.
21. **Long transcripts.** The app reads the first page of a transcript alone. If more pages follow, it reads 8 pages at a time.
22. **Background service.** The API has no push messages, webhooks or event stream, so true push is not possible. The app uses a foreground service of the type `specialUse`. Android 15 limits the type `dataSync` to 6 hours a day, and it does not let a `dataSync` service start after a restart of the phone. The type `specialUse` has neither limit.
23. **Subscriptions replace stars.** Build 9 and earlier notified about starred sessions. This build removes the stars. Subscribe to the workspace instead.

## Known limitations

- **Not run on a device.** The build machine had no emulator. Layout, keyboard behaviour, dynamic colour and notifications are not checked on a screen. Nobody has opened the R8 release build on a device yet.
- **Write calls are not checked against the live API.** I used the live API for read-only calls, and for one test message that answered a question. Create, rename, archive and cancel are checked against MockWebServer and the OpenAPI spec only.
- **Long transcripts.** The first time that a session tab opens, it loads the full transcript, 100 messages for each request.
- **Little offline cache.** Only the home screen keeps its last list on the phone. Workspaces and chats stay in memory only while the app runs.
- **Debug key.** The debug key of the build machine signs the APK. A build from a different machine has a different key, so Android does not install it over the earlier build. Uninstall the earlier build first.
- **Background checks.** A notification comes up to 15 seconds after the agent stops. If Android stops the service, the periodic check takes over, and a notification can come 15 minutes late or more. Some phone makers stop background services to save battery. On those phones, set the battery use of the app to "Unrestricted".
- **Battery.** While an agent works, the app keeps the phone awake and sends requests every 15 seconds. A long agent turn costs battery. Unsubscribe from a workspace that you do not need to follow.
- **Code blocks.** Code blocks have no syntax colours.
- **Pictures in the workspace.** An agent usually saves a screenshot as a file on the cloud machine. The API has no endpoint that reads a workspace file, and the workspace preview URL rejects an API key with HTTP 401. So the app shows the file name, not the picture.
- **Archive.** If Android stops the app during the 5 seconds of the "Undo" button, the app does not send the archive request. The workspace stays in the list.
- **Pull requests.** The Conductor API has no branch or pull request field, so the app needs a GitHub token and a link or a branch name to find the pull request. A pull request that is older than the 100 most recently updated ones is not found.
- **Finding a question costs requests.** The status stays "working" while a question waits, so the app reads the transcript to find it. The API returns messages oldest first, so the first look at a long session reads the whole transcript, 100 messages for each request. Later looks read only new messages.
- **Codex questions.** I tested questions from Claude sessions only.

## Contributing

[`CONTRIBUTING.md`](CONTRIBUTING.md) tells you how to set up the project and what a pull request needs. Each commit must have a [Developer Certificate of Origin](DCO) sign-off (`git commit -s`). You do not sign a contributor agreement.

- [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md): the Contributor Covenant, and how to report a problem.
- [`SECURITY.md`](SECURITY.md): how to report a vulnerability privately.

## License

Apache License 2.0. See [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE).
