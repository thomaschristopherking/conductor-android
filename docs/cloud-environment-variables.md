---
title: "Cloud workspace environment variables"
url: "/docs/cloud/environment-variables"
description: "Variables available to Cloud Computer builds, setup scripts, agents, and terminals"
---

# Cloud workspace environment variables



Conductor provides the variables below to cloud workspace setup scripts,
agents, run scripts, and cloud terminals. For variables shared with local
workspaces, see
[Conductor environment variables](/docs/reference/environment-variables).

## Conductor-provided variables [#conductor-provided-variables]

| Variable                   | Value in cloud workspaces                                                                                                                                                                                                             | Use it for                                                                                      |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| `CONDUCTOR_WORKSPACE_NAME` | Workspace display name, when one has been assigned.                                                                                                                                                                                   | Naming workspace-specific resources.                                                            |
| `CONDUCTOR_WORKSPACE_PATH` | Path to the repository checkout inside the sandbox.                                                                                                                                                                                   | Finding the workspace directory from setup scripts, agents, and terminals.                      |
| `CONDUCTOR_ROOT_PATH`      | The same path as `CONDUCTOR_WORKSPACE_PATH`.                                                                                                                                                                                          | Scripts that run in both local and cloud workspaces.                                            |
| `CONDUCTOR_BASE_DIR`       | Directory containing the workspace.                                                                                                                                                                                                   | Caches and scratch files outside the repository checkout.                                       |
| `CONDUCTOR_IS_LOCAL`       | `0` in cloud workspaces.                                                                                                                                                                                                              | Choosing local or cloud steps in one project script.                                            |
| `CONDUCTOR_API_URL`        | Base URL for the Conductor API.                                                                                                                                                                                                       | Calling the Conductor API from a workspace.                                                     |
| `CONDUCTOR_API_TOKEN`      | Workspace-scoped API token in new machine-launched cloud workspaces when automatic workspace API access is enabled; other cloud workspaces receive no automatic token.                                                                | Authenticating workspace API requests without a personal API key.                               |
| `CONDUCTOR_API_KEY`        | Your own Conductor API key when you configure one. When you don't supply your own, workspaces that receive an automatic token also set it here, and older cloud workspaces set their automatic workspace-scoped credential only here. | Overriding the workspace token with a personal API key.                                         |
| `CONDUCTOR_SESSION_ID`     | Current session ID. Available only in agent processes, not setup scripts or terminals.                                                                                                                                                | Passing the optional `X-Conductor-Session-Id` API header to attribute a request to its session. |

With `CONDUCTOR_API_URL` and `CONDUCTOR_API_TOKEN` (`CONDUCTOR_API_KEY` in
older workspaces), an agent or script can call the Conductor API from inside
the workspace. The provided token is scoped to the workspace. Each user can
configure automatic API access separately for each organization in **Settings
→ User → General**. Changes do not affect existing workspaces or tokens. A
user-supplied `CONDUCTOR_API_KEY` always takes precedence. See the
[Conductor API guide](/docs/api) for endpoints and authentication.

Cloud workspaces do not receive `CONDUCTOR_PORT`. See
[Forward a development port](/docs/cloud/working-with-cloud-workspaces#forward-a-development-port).

## Add your own variables [#add-your-own-variables]

Set organization-wide values, including secrets, in the
[Cloud Computer environment](/docs/cloud/cloud-computer#environment-variables-and-secrets).

For values that belong to one repository rather than the whole organization,
use `environment_variables.cloud` in `.conductor/settings.toml`:

```toml title=".conductor/settings.toml"
"$schema" = "https://conductor.build/schemas/settings.repo.schema.json"

[environment_variables]
API_BASE_URL = "https://api.staging.example.com"

[environment_variables.cloud]
CI = "1"
```

Repository or personal Cloud variables take precedence over an organization
Cloud Computer variable with the same name.

## Configure setup for local and Cloud [#configure-setup-for-local-and-cloud]

Cloud and local workspaces use separate setup scripts:

* Cloud workspaces run the repository's **Setup script** from organization
  settings.
* Local workspaces run `scripts.setup` from `.conductor/settings.toml` (or
  the legacy `conductor.json`).

To share the implementation, commit a script to the repository and call it
from both. Branch on `CONDUCTOR_IS_LOCAL` for steps that only make sense on
your Mac — cloud workspaces run Linux, so Homebrew, Xcode, and other Mac-only
tools need a cloud alternative or a local-only branch:

```bash
if [ "$CONDUCTOR_IS_LOCAL" = "1" ]; then
  # Local only: read from the separate root checkout on your Mac.
  ln -s "$CONDUCTOR_ROOT_PATH/.env" .env
fi

pnpm install
```

In a cloud workspace, `CONDUCTOR_ROOT_PATH` already points to the workspace
checkout. Conductor manages git and GitHub CLI authentication, so don't copy
GitHub tokens into Cloud Computer variables.

## Reserved names [#reserved-names]

Variable names must be valid shell identifiers. The names and prefixes below
belong to the sandbox runtime and cannot be overridden.

| Reserved name or prefix    | Reason                                             |
| -------------------------- | -------------------------------------------------- |
| `HOME`                     | Managed by the sandbox user environment.           |
| `PATH`                     | Managed by the sandbox shell environment.          |
| `PORT`                     | Reserved for platform runtime behavior.            |
| `NODE_PATH`                | Reserved for Node.js module resolution.            |
| `CONDUCTOR_API_URL`        | Set by Conductor for API routing.                  |
| `CONDUCTOR_BASE_DIR`       | Set by Conductor for the sandbox directory.        |
| `CONDUCTOR_IS_LOCAL`       | Set by Conductor to identify cloud workspaces.     |
| `CONDUCTOR_ROOT_PATH`      | Set by Conductor for the repository checkout path. |
| `CONDUCTOR_WORKSPACE_NAME` | Set by Conductor for the workspace name.           |
| `CONDUCTOR_WORKSPACE_PATH` | Set by Conductor for the repository checkout path. |
| `CONDUCTOR_INTERNAL_*`     | Reserved for Conductor runtime behavior.           |
| `CONDUCTOR_GIT_AUTH_*`     | Reserved for Conductor-managed Git authentication. |
