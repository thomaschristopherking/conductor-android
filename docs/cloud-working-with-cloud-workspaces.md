---
title: "Work with cloud workspaces"
url: "/docs/cloud/working-with-cloud-workspaces"
description: "Use workspace status, SSH, file sync, terminals, and forwarded ports"
---

# Work with cloud workspaces



Cloud workspaces keep the chat, diff, git, terminal, and pull request workflow
you know from local workspaces. This page covers what's different: connecting
your Mac and local tools to the Linux sandbox where the workspace runs.

Click the workspace name in the top bar to open the workspace details:
sandbox status, CPU, and memory, plus **Restart**, **Open via SSH**, **Sync
to a local directory**, and **Forward ports**.

## Open the workspace over SSH [#open-the-workspace-over-ssh]

Use **Open via SSH** to point a local editor (through Remote SSH) or an SSH
terminal at the files in the sandbox. Choosing it wakes a sleeping workspace
automatically.

## Sync files to your Mac [#sync-files-to-your-mac]

Turn on **Sync to a local directory** to mirror the cloud workspace into a
local folder, for Mac tools that can't connect over SSH. Sync is one-way,
cloud to Mac: local changes don't sync back and may be overwritten. Use SSH
when a local tool needs to change workspace files.

Sync is per-member — turning it on doesn't affect teammates.

## Open terminals [#open-terminals]

Choose &#x2A;*+** in the workspace tab bar for a **Cloud terminal** (a shell inside
the sandbox, against the live checkout) or a **Local terminal** (a shell on
your Mac in the synced directory).

## Forward a development port [#forward-a-development-port]

Cloud workspaces don't receive `CONDUCTOR_PORT` — start your server on an
explicit port, listening on `127.0.0.1` or `0.0.0.0`:

```bash
npm run dev -- --host 0.0.0.0 --port 3000
```

Then add the port under **Forward ports** in the workspace details and click
`Open localhost:<local-port>`. The local port usually differs from the sandbox
port, so use the displayed mapping, such as `3000 → localhost:52143`. Port
`22` is reserved for SSH.

## Sleep and maximum lifetime [#sleep-and-maximum-lifetime]

Conductor puts a cloud workspace to sleep after four hours without agent or
terminal activity. (Keeping it open in a focused Conductor window counts as
activity.) Separately, every sandbox stops at its maximum lifetime of 23 hours
and 50 minutes, even if active — which can interrupt running processes and
agent turns.

Files and chat history survive sleep; running processes don't. Opening the
workspace wakes it automatically — then restart any interrupted server or
build, and resend an interrupted agent turn. While the sandbox is asleep or
unreachable, cloud terminals become read only, but cached git information and
synced local files remain available.

If a workspace doesn't wake or shows **Unresponsive**, click the workspace
name in the top bar to open the workspace details, then click **Restart**.
