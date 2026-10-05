---
name: conductor
description: Configure, operate, and troubleshoot Conductor — workspaces, repository settings.toml files, scripts, files to copy, managed settings, agent controls, and review workflows. Use when helping someone set up or operate Conductor.
license: Proprietary
compatibility: Conductor runs coding agents in isolated workspaces, either locally in git worktrees on the user's Mac or in Linux cloud sandboxes.
---

# Conductor

Conductor is an app for running multiple coding agents in parallel, each in an isolated workspace. It supports Claude Code, Codex, Cursor Agent, and OpenCode. The desktop app is Mac-only; cloud workspaces run on Linux and can be driven from the Mac app or the iOS app.

This file covers the facts that are easy to get wrong. For anything else, fetch the docs page linked in the relevant section.

## Know your environment

Check `CONDUCTOR_IS_LOCAL` before giving environment-specific guidance: `1` = you are running locally on the user's Mac; `0` = you are in a Conductor cloud sandbox (Linux).

- **Local**: your session runs in a git worktree on the user's Mac, with the user's local permissions. Each repository has a main root directory and one worktree per workspace. New workspaces are created from the repository's configured base branch; Conductor fetches from `origin` first, so a workspace starts from the latest remote commit even when the local checkout is behind (the fetch does not move the branch checked out in the root directory). Only the user can create or control local workspaces, from the Conductor app; there is no CLI or API for them, so ask the user instead of trying.
- **Cloud**: your session runs in a Linux sandbox with a single checkout of the repository. You have no access to the user's machine or its filesystem — unless the Conductor `RunLocalCommand` tool is available, which runs a one-shot shell command on the user's Mac. Cloud workspaces can be created from the Mac app, the iOS app, or the API, so do not assume a Mac client.

Docs:

- https://conductor.build/docs/concepts/workspaces-and-branches
- https://conductor.build/docs/concepts/workflow
- https://conductor.build/docs/concepts/parallel-agents

## Settings files

1. Machine-local, one repository: `<repo>/.conductor/settings.local.toml`.
2. Shared with the team: `<repo>/.conductor/settings.toml` — the Mac app reads it from the default branch on the remote, so merge it before it takes effect locally; cloud setup reads it from the branch the workspace is created from.
3. This user, all repositories: `~/.conductor/settings.toml`.
4. Organization-controlled: `~/.conductor/settings.managed.toml`.

Precedence: managed > repository local > repository shared > user > built-in defaults. Any TOML file outranks any legacy JSON file, regardless of layer; Conductor writes new settings as TOML.

Schemas:

- User settings: `https://conductor.build/schemas/settings.schema.json`
- Repository settings: `https://conductor.build/schemas/settings.repo.schema.json`
- Managed settings: `https://conductor.build/schemas/settings.toml.json`

Docs:

- https://conductor.build/docs/reference/settings
- https://conductor.build/docs/reference/scripts/share-with-teammates

## Repository settings and scripts

A typical `<repo>/.conductor/settings.toml`:

```toml
"$schema" = "https://conductor.build/schemas/settings.repo.schema.json"

[scripts]
setup = "pnpm install"
run_mode = "concurrent"

[scripts.run.dev]
available_in = [ "local" ]
command = "pnpm dev --port $CONDUCTOR_PORT"
default = true
icon = "play"

[scripts.run.test]
command = "pnpm test:watch"
icon = "test-tube"
```

The schema and settings docs list every key. Facts worth knowing:

- Setup, run, and archive scripts run from the workspace directory in non-interactive shells: `zsh` on the user's Mac (with the login shell environment captured), `bash` in cloud workspaces. Put required toolchain setup in the scripts themselves rather than relying on interactive shell startup.
- Local workspaces get ten ports, `CONDUCTOR_PORT` through `CONDUCTOR_PORT+9`. `CONDUCTOR_PORT` is unset in cloud workspaces, so mark run scripts that depend on it `available_in = [ "local" ]`.
- Use `run_mode = "concurrent"` only when multiple workspaces can run at the same time (separate ports, no shared database or Docker stack); otherwise `nonconcurrent`.
- `scripts.run.<id>.available_in` takes `local`, `cloud`, or both; omit it to show the script everywhere.
- `icon` values are lowercase-kebab Lucide names; invalid names fall back to `play`.
- Legacy single-string `scripts.run` is still read and is migrated to `[scripts.run.<id>]` when Conductor rewrites the file.
- When one run script starts multiple processes, keep them in the same process group (e.g. `concurrently`) instead of backgrounding with `&`.
- Use Spotlight testing when a project cannot run from a worktree and must execute from the repository root.
- Settings that are not repository-configurable include model defaults, reasoning defaults, tool approvals, and the default workspace location — those live in user settings.

Environment variables available to scripts and agents:

- `CONDUCTOR_WORKSPACE_NAME`: Workspace name.
- `CONDUCTOR_WORKSPACE_ID`: Workspace ID in agent and terminal processes.
- `CONDUCTOR_WORKSPACE_PATH`: Workspace path.
- `CONDUCTOR_ROOT_PATH`: Repository root directory. In cloud workspaces this equals the workspace path.
- `CONDUCTOR_DEFAULT_BRANCH`: Default branch name. Local workspaces only.
- `CONDUCTOR_PORT`: First of the ten allocated ports. Local workspaces only.
- `CONDUCTOR_IS_LOCAL`: `1` on the user's Mac, `0` in a cloud workspace. Branch on this in `scripts.setup` for local-only steps.

Docs:

- https://conductor.build/docs/reference/scripts
- https://conductor.build/docs/reference/scripts/setup
- https://conductor.build/docs/reference/scripts/run
- https://conductor.build/docs/reference/scripts/spotlight-testing
- https://conductor.build/docs/reference/shells
- https://conductor.build/docs/reference/environment-variables

## Files to copy

For static gitignored files (`.env` files, local config, certificates) that every new workspace needs, use Files to copy rather than a setup script. Resolution order:

1. `.worktreeinclude` at the repository root.
2. `file_include_globs` in repository settings.
3. Default `.env*` pattern.

Use `scripts.setup` when the workspace needs commands run: dependency installs, generated files, symlinks.

Docs:

- https://conductor.build/docs/reference/files-to-copy
- https://conductor.build/docs/reference/worktreeinclude

## Managed settings

Organization-controlled settings live at `~/.conductor/settings.managed.toml` (legacy `settings.managed.json` is still read). Managed values override user and repository settings and disable the matching controls in Settings. Managed settings are provisional.

Docs:

- https://conductor.build/docs/reference/settings/managed
- https://conductor.build/docs/reference/privacy
- https://conductor.build/docs/reference/security-and-permissions

## Legacy conductor.json

The Mac client ignores repo-level `conductor.json` once `<repo>/.conductor/settings.toml` exists, but cloud setup still falls back to a `conductor.json` setup script when no settings TOML defines one — delete `conductor.json` once migrated. Migration map:

- `scripts.setup` -> `scripts.setup`
- `scripts.run` -> `scripts.run.<id>.command`
- `scripts.archive` -> `scripts.archive`
- `runScriptMode` -> `scripts.run_mode`
- `enterpriseDataPrivacy` -> `enterprise_data_privacy`

Docs:

- https://conductor.build/docs/reference/conductor-json

## Agent controls

Plan mode, fast mode, reasoning controls, Codex personality, checkpoints, MCP, slash commands, todos, and instruction files are covered in the docs:

- https://conductor.build/docs/reference/agent-behavior
- https://conductor.build/docs/concepts/agent-modes
- https://conductor.build/docs/reference/checkpoints
- https://conductor.build/docs/reference/mcp
- https://conductor.build/docs/reference/slash-commands
- https://conductor.build/docs/reference/todos

## Review and merge

When leaving review feedback from inside Conductor, use the Conductor `DiffComment` tool when it is available — comments appear in the app's Checks panel. Do not post review feedback to GitHub unless the user explicitly asks for GitHub comments.

Docs:

- https://conductor.build/docs/guides/review-and-merge
- https://conductor.build/docs/reference/diff-viewer
- https://conductor.build/docs/reference/checks

## Troubleshooting

Non-obvious causes to check first:

- Setup or run scripts that depend on interactive shell startup behavior (they run in non-interactive shells).
- Missing files that are gitignored and should be copied via Files to copy or `.worktreeinclude`.
- A shared resource (fixed port, single database, Docker stack) that needs `run_mode = "nonconcurrent"` or Spotlight testing.
- Managed settings in `~/.conductor/settings.managed.toml` overriding what the user set.
- A legacy `conductor.json` being ignored by the Mac client because `.conductor/settings.toml` exists.

Docs:

- https://conductor.build/docs/faq
- https://conductor.build/docs/troubleshooting/issues
- https://conductor.build/docs/reference/shells
