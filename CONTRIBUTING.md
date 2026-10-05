# Contribute to conductor-android

This guide tells you how to set up the project, what a change needs, and how a
change gets merged.

When you take part, you agree to the [Code of Conduct](CODE_OF_CONDUCT.md).

## What the app is

conductor-android is a native Android client for the Conductor Cloud API. It
lists projects, workspaces and sessions, shows a session transcript as a chat,
and sends prompts to the agent.

The app does not replace the Conductor desktop app. A feature that the public
API at `api.conductor.build/v0` does not support is not in scope.

## Set up

You need:

- JDK 17
- The Android SDK, with `platforms;android-37.0` and `build-tools;37.0.0`
- `ANDROID_HOME` set to the SDK folder, or a `local.properties` file with
  `sdk.dir=...`

Clone the repository and run the checks:

```bash
git clone https://github.com/thomaschristopherking/conductor-android.git
cd conductor-android
./gradlew lint testDebugUnitTest assembleDebug
```

`docs/API_NOTES.md` describes the API and the places where the live API is
different from its spec. Read it before you change the data layer.

## Report a bug

Search the open issues first. If the bug is new, open an issue with:

- What you did, what happened, and what you expected.
- The app version or commit, the Android version, and the device.
- The steps to reproduce it.

Do not paste an API key, a full transcript or a screenshot that shows private
repository names. Describe the content instead.

For a security problem, do not open an issue. See [SECURITY.md](SECURITY.md).

## Propose a change

You can send a small fix directly as a pull request. For a new screen, a new
dependency or a change to the storage of the key, open an issue first, and
agree the approach.

## Make the change

### Workflow

Fork the repository, make a branch from `main`, and open a pull request to
`main`. Keep each pull request to one subject. Rebase on `main`. Do not merge
`main` into your branch.

### Sign off your commits

Each commit must have a `Signed-off-by` line. With it, you state that you wrote
the change, or that you have the right to submit it under the licence of this
project. The full text is the [Developer Certificate of Origin](DCO) 1.1. You do
not sign a separate contributor agreement.

The licence is the [Apache License 2.0](LICENSE). Your contribution uses the same
licence as the rest of the project. You keep the copyright in your work.

`git commit -s` adds the line with your configured name and e-mail address:

```
Signed-off-by: Jane Developer <jane@example.com>
```

Use your real name and an address that you read. CI checks each commit in a pull
request, and fails when a commit has no sign-off. To add the line to a branch
that you already wrote, run `git rebase --signoff main`. To run the same check
on your computer, run `scripts/check_dco.sh`.

### Commit messages

The subject line tells what the change does. Use the imperative, in about 70
characters or fewer, for example `Stop polling when the session screen is
hidden`. The body tells why.

### Code standards

- Kotlin, with the official code style.
- Name a function with a verb, for example `loadSession`. Start a Boolean name
  with `is`, `has`, `can` or `should`.
- Write comments and documentation in plain English, in short sentences.
- Never write the API key to a log, a file, a fixture or a build field.

### Tests

Unit tests run on the JVM, with no device and no network.

- Test the API layer with MockWebServer and the fixtures in
  `app/src/test/resources/fixtures/`.
- Test view models with `MainDispatcherRule` and virtual time. Do not use a real
  sleep.

To capture new fixtures from the live API, run
`CONDUCTOR_API_KEY=... python3 scripts/capture_fixtures.py`. The script sends
only `GET` requests, and it removes personal data. Read each fixture before you
commit it.

## Before you open the pull request

Run the checks:

```bash
./gradlew lint testDebugUnitTest assembleDebug
scripts/check_dco.sh
```

## Review and merge

CI runs the DCO check and a secret scan. Both must pass. A maintainer then
reviews the change.

Reply to each review comment when you address it. Pull requests are
squash-merged, so the pull request title becomes the commit subject on `main`.
