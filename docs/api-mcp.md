---
title: "Conductor MCP server"
url: "/docs/api/mcp"
description: "Connect an MCP client to create and manage Conductor cloud workspaces"
---

# Conductor MCP server



Conductor's hosted Model Context Protocol (MCP) server lets ChatGPT, Claude,
Codex, and other MCP clients manage your cloud workspaces. Connect once, then
ask your client to create workspaces, delegate tasks to coding agents, follow
their progress, and read the results.

**Server URL:** `https://api.conductor.build/mcp`

The server uses Streamable HTTP. Connect with OAuth when your client supports
it; your client opens Conductor in a browser so you can sign in, choose an
organization, and approve access. You do not need to create or paste an API
key.

The Conductor API and hosted MCP server are in beta. Tools and input shapes
may change.

## Connect with an agent [#connect-with-an-agent]

Copy this prompt into an agent that can manage MCP servers:




## Connect manually [#connect-manually]

### ChatGPT [#chatgpt]

1. Open **Settings → Plugins**.
2. Open the **Add** menu and select **Add MCP server**.
3. Enter `Conductor` as the server name.
4. Switch the server type from **STDIO** to **Streamable HTTP**.
5. Enter `https://api.conductor.build/mcp` as the server URL and select
   **Save**.
6. Sign in to Conductor, choose an organization, and approve access.

### Cursor [#cursor]

1. Open **Customize**.
2. Select **MCPs**.
3. Select **New MCP Server**. Cursor opens `~/.cursor/mcp.json`.
4. Paste this configuration into the file and save it:

```json title="~/.cursor/mcp.json"
{
    "mcpServers": {
        "conductor": {
            "url": "https://api.conductor.build/mcp"
        }
    }
}
```

5. Select **Authenticate** for the `conductor` server, then sign in to
   Conductor, choose an organization, and approve access.

See the [Cursor MCP documentation](https://cursor.com/docs/mcp) for other
configuration options.

### Claude Code [#claude-code]

Add the server to your user configuration, then complete OAuth in your browser:

```bash
claude mcp add --transport http --scope user conductor \
  https://api.conductor.build/mcp
claude mcp login conductor
```

Use `--scope local` instead of `--scope user` when the connection should apply
only to the current project. Run `claude mcp list` to check the connection.

### Codex [#codex]

Add the server and sign in with OAuth:

```bash
codex mcp add conductor --url https://api.conductor.build/mcp
codex mcp login conductor
```

Codex stores the connection in `~/.codex/config.toml`. The Codex CLI, IDE
extension, and ChatGPT desktop app share this configuration. Run
`codex mcp list` to check the connection. See the
[Codex MCP documentation](https://developers.openai.com/codex/mcp) for other
configuration options.

### Other MCP clients [#other-mcp-clients]

Configure a remote Streamable HTTP server with this URL:

```text
https://api.conductor.build/mcp
```

For browser sign-in, the client must support OAuth for protected MCP servers.
Conductor supports Dynamic Client Registration, PKCE authorization codes, and
refresh tokens. The OAuth grant is limited to the `mcp:tools` scope and the
organization you choose during sign-in.

## Connect with an API key [#connect-with-an-api-key]

Use an API key when your MCP client supports bearer authentication but cannot
complete browser OAuth. Create a key at
[app.conductor.build/home/api-keys](https://app.conductor.build/home/api-keys)
and treat it like a password.

For Codex, keep the key in an environment variable instead of putting it in
`config.toml`:

```bash
export CONDUCTOR_API_KEY="YOUR_API_KEY"
codex mcp add conductor \
  --url https://api.conductor.build/mcp \
  --bearer-token-env-var CONDUCTOR_API_KEY
```

For a client with a generic HTTP configuration, send the key on every request:

```text
Authorization: Bearer YOUR_API_KEY
```

## Example prompts [#example-prompts]

After connecting, try:

```text
List my Conductor projects and the models I can use.
```

```text
Create a workspace in the my most recent project and ask me what I want to work on and what model to use, then start the workspace.
```

```text
Find the Conductor workspaces active in the last 24 hours and summarize what
each agent accomplished. Include a link to each workspace.
```

## Tool reference [#tool-reference]

| Tool                      | Description                                                                                   |
| ------------------------- | --------------------------------------------------------------------------------------------- |
| `whoami`                  | Get the authenticated Conductor identity and organization.                                    |
| `list_models`             | List supported agents, model IDs, effort levels, defaults, and fast-mode support.             |
| `list_projects`           | List projects visible to the connected account.                                               |
| `get_project`             | Get a project by ID.                                                                          |
| `list_project_workspaces` | List the workspaces in a project.                                                             |
| `create_workspace`        | Create an isolated cloud workspace and its first session from a project ID or repository URL. |
| `get_workspace`           | Get a workspace by ID.                                                                        |
| `get_workspace_status`    | Get a workspace's lifecycle status.                                                           |
| `rename_workspace`        | Rename a workspace.                                                                           |
| `archive_workspace`       | Archive a workspace.                                                                          |
| `unarchive_workspace`     | Restore an archived workspace.                                                                |
| `list_workspace_sessions` | List the sessions in a workspace.                                                             |
| `create_session`          | Create another agent session in an existing workspace.                                        |
| `get_session`             | Get a session by ID.                                                                          |
| `get_session_status`      | Get a session's current agent status.                                                         |
| `cancel_session`          | Cancel the current agent turn and discard queued messages.                                    |
| `send_message`            | Send or queue a prompt for a session.                                                         |
| `list_messages`           | List transcript messages, optionally only those after a message cursor.                       |
| `get_message`             | Get one transcript message by ID.                                                             |
| `run_sql`                 | Run a read-only query against `session_transcripts_view`.                                     |

List tools are paginated. Ask the client to continue while `hasMore` is true
when you need a complete result. For transcripts, use `list_messages` with the
last message ID as `after` instead of repeatedly paging from the beginning.

MCP tools return the same response objects as the `/v0` API. Workspace tools
include `creatorId` and `creatorName`; `creatorName` falls back to the creator's
email when they have not set a name. `whoami` includes the authenticated user's
optional `name` alongside their `email`.

## Access and safety [#access-and-safety]

OAuth access is bound to the organization selected during sign-in. The tools
act with your Conductor permissions and include write actions such as creating
workspaces, sending prompts, canceling sessions, and archiving workspaces.
Review tool calls before approving them, especially when a client proposes an
action you did not request.

Disconnect the server in your MCP client's settings to stop that client from
using it. If you connected with an API key, delete or rotate the key from the
[API keys page](https://app.conductor.build/home/api-keys).
