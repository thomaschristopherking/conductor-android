# Security policy

## Report a vulnerability

Do not open a public issue or discussion for a security problem. Use GitHub's
[private vulnerability reporting](https://github.com/thomaschristopherking/conductor-android/security/advisories/new)
for this repository.

Tell us what you found, which version or commit has the problem, and how to
reproduce it. A proof of concept helps. Do not run it against a system or an
account that is not yours.

We try to acknowledge a report in three working days, and to tell you our plan
in ten. We tell you about progress while we work on a fix. We credit you in the
advisory, unless you tell us not to.

## Supported versions

The app has no release branches. Fixes go to `main`, and only the latest
release gets them.

## Scope

The app holds a Conductor API key and uses it to call `api.conductor.build`.
These reports are the most useful:

- A way to read the API key from the device: from storage, logs, backups,
  screenshots, the clipboard, or another app.
- A way to send the API key, or an authenticated request, to a host other than
  `api.conductor.build`.
- Content from the API, for example a transcript or a deep link, that makes the
  app run code, open a URL without a tap, or write outside its own storage.
- A vulnerability in a dependency that the app is exposed to through the way it
  uses that dependency.

A problem in the Conductor service itself is not in scope here. Report it to
Conductor. A wrong display of a transcript is a bug, not a vulnerability.
Report it as a normal issue.
