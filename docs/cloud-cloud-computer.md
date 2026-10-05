---
title: "Cloud Computer"
url: "/docs/cloud/cloud-computer"
description: "Configure the shared repositories, environment, software, and builds for your organization"
---

# Cloud Computer



The Cloud Computer is the shared development environment for a Conductor
organization. Its active build determines the repositories, environment
variables, secrets, and software a new cloud workspace starts with. Manage it
in `Settings → Organization → Cloud Computer`; changes affect the whole
organization.

## Specifications [#specifications]

Each cloud workspace runs in an isolated microVM with:

| Resource         | Specification                   |
| ---------------- | ------------------------------- |
| CPU              | 8 vCPUs                         |
| Memory           | 16 GB RAM                       |
| Storage          | 32 GB of ephemeral NVMe storage |
| Operating system | Amazon Linux 2023               |

## Edit settings with an agent [#edit-settings-with-an-agent]

Instead of configuring the computer by hand, choose **Configure with an
agent** to open an Admin workspace and tell the agent what you want to change.
It can inspect and test the repositories, update the install script and each
repository's setup script, and run builds — without asking you to confirm each
step.

The agent can't add or remove repositories, change environment variables or
secrets, manage members or credentials, or activate an older build; make those
changes directly in settings. It won't edit repository code unless you ask.

## Repositories [#repositories]

Add the GitHub repositories (up to 50; other Git hosts aren't supported yet)
you want available when creating workspaces. Each is cloned onto the computer
in the next build.

## Environment variables and secrets [#environment-variables-and-secrets]

Use **Environment** for values that every cloud workspace in the organization
needs, including secrets. Values are hidden after you save them: type a new
value to replace one, or remove the row to delete it in the next build.

The Admin workspace agent's tools can't read or change saved values, but the
values are present as environment variables inside workspaces — so don't ask
an agent to print secrets into chat, build logs, or command output.

See [Cloud workspace environment variables](/docs/cloud/environment-variables)
for Conductor-provided variables, precedence, and reserved names.

## Install software [#install-software]

Use the **Install software** script for slow, repeatable setup that every new
workspace should inherit: system packages, language runtimes, browser
binaries, shared caches. The base computer already includes Node.js 24, npm,
pnpm, Python 3, git, GitHub CLI, jq, Git LFS, ripgrep, tmux, Vim, and Google
Chrome. You have
`sudo`; use `dnf` for system packages:

```bash
sudo dnf install -y postgresql16
```

The script runs once per build from the sandbox home directory, with the
configured repositories below it, as Bash with `set -euo pipefail` — a failed
command fails the build.

### Make installed tools available in workspaces [#make-installed-tools-available-in-workspaces]

The install script is not a login shell, but workspace setup scripts, run
scripts, and cloud terminals are — they read `~/.bash_profile`, which sources
`~/.profile`. If an installer changes `PATH`, export the new path for the rest
of the build and append it to `~/.profile`:

```bash
curl -fsSL https://bun.sh/install | bash

export BUN_INSTALL="$HOME/.bun"
export PATH="$BUN_INSTALL/bin:$PATH"

printf '%s\n' \
  'export BUN_INSTALL="$HOME/.bun"' \
  'export PATH="$BUN_INSTALL/bin:$PATH"' \
  >> "$HOME/.profile"
```

If your install script replaces `~/.bash_profile`, keep the line that sources
`~/.profile` — otherwise tools installed during the build will be missing in
workspaces.

## Build the computer [#build-the-computer]

Choose **Build computer** to apply configuration changes, or to pick up new
commits and dependencies without any changes. During a build, Conductor clones
the repositories, adds the environment variables, and runs the install script.
A successful build automatically becomes **Active**: new workspaces start from
it, and existing workspaces keep the environment they were created with.

Select the build status to open its logs, or choose **Restart build** if you
edited the configuration mid-build. **History** lists past builds; choose
**Activate** on a previous successful build to use it for new workspaces. It
takes effect immediately, with no confirmation.

## Repository setup scripts [#repository-setup-scripts]

The install script prepares the shared computer once per build. A repository's
**Setup script** — edited under that repository in organization settings, or
by the Admin workspace agent — runs every time a new workspace is created. Use
it for dependency installs, generated files, and seeds.

Saving a setup script doesn't require a build; it applies to the next
workspace. Keep a dependency install in it even if the computer build warms
caches, so new workspaces pick up changes made after the build.

Cloud workspaces don't use the setup script in `.conductor/settings.toml` or
the legacy `conductor.json`; those still configure local workspaces. See
[sharing one setup script for local and Cloud](/docs/cloud/environment-variables#configure-setup-for-local-and-cloud).
