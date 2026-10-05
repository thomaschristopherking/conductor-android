---
title: "Conductor API"
url: "/docs/api"
description: "Drive Conductor cloud workspaces programmatically over HTTP"
---

# Conductor API



The Conductor API lets you manage [cloud workspaces](/docs/cloud) programmatically. Use it to do things like:

* Create workspaces, send prompts to the coding agent, and read its replies.
* Build bots that kick off agent work.
* Run and manage Conductor workspaces from your OpenClaw.

Endpoints live under `https://api.conductor.build/v0`. This page covers setup and the typical flow; for the full contract check out our [OpenAPI spec](https://api.conductor.build/v0/openapi.json).

The API is in beta. Request and response shapes may change. Send feedback to
[humans@conductor.build](mailto:humans@conductor.build).

## Try it [#try-it]

Create an API key at [app.conductor.build/home/api-keys](https://app.conductor.build/home/api-keys), then paste it here to test it end to end: this creates a **real cloud workspace**:




## Authentication [#authentication]

Pass your API key as a bearer token: `Authorization: Bearer <your API key>`.

## Connect with MCP [#connect-with-mcp]

Connect an MCP-compatible client to Conductor to manage cloud workspaces with
tools instead of direct REST requests. See the [Conductor MCP server](/docs/api/mcp)
for setup instructions and the complete tool list.

## Quick start (for AI) [#quick-start-for-ai]

Paste this into your Claude Code / Codex / OpenClaw agent:




## Quick start (for humans) [#quick-start-for-humans]

List projects:

```bash
curl https://api.conductor.build/v0/projects \
  -H "Authorization: Bearer YOUR_API_KEY"
```

Create a workspace using a project ID from that response:

```bash
curl -X POST https://api.conductor.build/v0/workspaces \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "PROJECT_ID",
    "name": "my-workspace",
    "agent": "codex",
    "model": "gpt-5.5"
  }'
```

Then read it using the `id` returned above:

```bash
curl https://api.conductor.build/v0/workspaces/WORKSPACE_ID \
  -H "Authorization: Bearer YOUR_API_KEY"
```

## Endpoints [#endpoints]

| Endpoint                               | What it does                                                            |
| -------------------------------------- | ----------------------------------------------------------------------- |
| `GET /v0/projects`                     | List the repositories you can create workspaces in.                     |
| `GET /v0/projects/{id}`                | Get a project.                                                          |
| `GET /v0/projects/{id}/workspaces`     | List a project's workspaces.                                            |
| `POST /v0/workspaces`                  | Create a workspace and its first session.                               |
| `GET /v0/workspaces/{id}`              | Get a workspace.                                                        |
| `POST /v0/workspaces/{id}/rename`      | Rename a workspace.                                                     |
| `GET /v0/workspaces/{id}/sessions`     | List a workspace's sessions (`includeArchived=true` includes archived). |
| `GET /v0/workspaces/{id}/status`       | Workspace lifecycle: `initializing`, `ready`, `sleeping`, `archived`, … |
| `POST /v0/workspaces/{id}/archive`     | Stop the workspace's machine and hide it in the app (restorable later). |
| `POST /v0/workspaces/{id}/unarchive`   | Restore an archived workspace and start its machine.                    |
| `POST /v0/workspaces/{id}/sleep`       | Put the workspace to sleep (it stays visible and resumable).            |
| `POST /v0/sessions`                    | Add another agent chat to a workspace.                                  |
| `GET /v0/sessions/{id}`                | Get a session.                                                          |
| `POST /v0/sessions/{id}/rename`        | Rename a session.                                                       |
| `POST /v0/sessions/{id}/messages`      | Send a prompt to the agent.                                             |
| `GET /v0/sessions/{id}/messages`       | Read the session transcript.                                            |
| `GET /v0/sessions/{id}/status`         | Whether the agent is `idle`, `working`, or errored.                     |
| `POST /v0/sessions/{id}/cancel`        | Stop the agent's current turn and drop queued messages.                 |
| `GET /v0/messages/{id}`                | Get a single transcript message.                                        |
| `POST /v0/sql`                         | Search transcripts of workspaces you can access with read-only SQL.     |
| `POST /v0/routines`                    | Create a routine that runs a prompt when its webhook URL is called.     |
| `GET /v0/routines`                     | List your routines (without webhook URLs).                              |
| `POST /v0/routines/{id}/rotate-secret` | Replace a routine's webhook URL and return the new one.                 |

List endpoints paginate with `limit`/`offset` and return `{ data, offset, hasMore }`. When polling a transcript, pass `after=<last message id>` instead of stepping `offset` — it returns only new messages. Errors from the API include a human-readable `userMessage`.

Workspace responses include `creatorId` and `creatorName`. `creatorName` is the
creator's name, or their email address when they have not set a name. The
`GET /me` response also includes the authenticated user's optional `name`
alongside their `email`.

### Routines [#routines]

A routine is a saved prompt that Conductor runs in a fresh workspace each time its webhook URL receives a JSON POST. The routine endpoints require a user API key from [app.conductor.build/home/api-keys](https://app.conductor.build/home/api-keys); the workspace-scoped `CONDUCTOR_API_TOKEN` is rejected with a `403`. Routines belong to the user who created them and run with that user's Git and agent credentials.

Routines created through this API have exactly one webhook trigger. `GET /v0/routines` also lists your existing routines, which may have cron, GitHub, or multiple triggers. `rotate-secret` requires exactly one trigger, and it must be a webhook; other trigger configurations return `409`.

The webhook URL is the trigger's only credential: anyone who can POST to it can run the routine, and no other authentication is checked. The URL appears only in the `POST /v0/routines` and `rotate-secret` responses — `GET /v0/routines` omits it — so store it as a secret. If it leaks, call `rotate-secret` to replace it. Revoking your API key does not revoke the URL.

Create a routine, then call its webhook:

```bash
curl -X POST https://api.conductor.build/v0/routines \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Triage customer report",
    "projectId": "PROJECT_ID",
    "agent": "codex",
    "model": "gpt-5.6-sol",
    "prompt": "Investigate the attached customer report and open a PR with a fix."
  }'
# → { "id": "...", "triggers": [{ "type": "webhook", "webhookUrl": "https://api.conductor.build/automations/hooks/..." }], ... }

curl -X POST "WEBHOOK_URL" \
  -H "Content-Type: application/json" \
  -d '{"report": "Login fails with a 500 on Safari"}'
```

The webhook returns `202` and the JSON body is attached to the prompt. `enabled` defaults to `true`; a disabled routine keeps its URL but answers `409`. `model` and `effort` follow the same rules as workspace creation.

The same flow with the `conductor` CLI (set `CONDUCTOR_API_KEY` to your user API key first):

```bash
conductor routine create \
  --name "Triage customer report" \
  --project-id PROJECT_ID \
  --agent codex \
  --model gpt-5.6-sol \
  --prompt-file ./triage-prompt.md
conductor routine list
conductor routine rotate-secret ROUTINE_ID
```

`routine create` and `routine rotate-secret` print the webhook URL once; pass `--json` to capture the full response in a script.

## Cookbook [#cookbook]

Machine-launched cloud workspaces include an authenticated Conductor API key. In
other workspaces, configure an API key before trying these prompts. See [Calling
the API from a cloud workspace](#calling-the-api-from-a-cloud-workspace) for
availability details.

#### Plan, Implement [#plan-implement]

```text
I will give you a task and a model in my next message. You handle the planning and review; the model I name does the implementation in a fresh Conductor session via the Conductor API:

1. Plan first: investigate whatever you need here, then write a self-contained implementation brief — files, approach, constraints, definition of done. The implementing session shares no context with you.
2. Find the right project with GET /v0/projects and create a workspace for the task (POST /v0/workspaces — name it after the task, with the model I name). Note the sessionId and deepLink it returns.
3. Send the brief with POST /v0/sessions/{sessionId}/messages.
4. Manage the session until the work actually gets done: poll GET /v0/sessions/{sessionId}/status every ~15 seconds and read new transcript messages with GET /v0/sessions/{sessionId}/messages?after=<last message id>. If the implementer stalls, drifts from the brief, or asks a question, answer or course-correct it with another message — messages sent while it works are steered into the running turn. (The session reports "idle" until the queued brief is delivered, so wait until you have seen "working" and it settles back to "idle", or until the reply is already in the transcript.)
5. When it settles, review the result against the brief and send follow-ups until it meets the bar. Then give me a short summary of what was done, the PR, and the deepLink.
```

#### Implement a multi-PR task [#implement-a-multi-pr-task]

```text
I will give you a task and a model in my next message. The task is too big for one pull request — it may even span repositories — so split it up and run the pieces in parallel through the Conductor API:

1. Break the task into independent pieces, each small enough for its own pull request, and decide which repository each piece belongs in (GET /v0/projects lists the repositories you can use). Write a self-contained brief for each piece — the implementing sessions share no context with you or with each other, so spell out any interfaces the pieces must agree on.
2. For each piece, create a workspace named after it (POST /v0/workspaces, with the model I name) and send its brief to the workspace's session with POST /v0/sessions/{sessionId}/messages, asking for a PR. Kick them all off before waiting on any of them so they run in parallel.
3. Supervise the sessions: poll GET /v0/sessions/{sessionId}/status, read new messages with GET /v0/sessions/{sessionId}/messages?after=<last message id>, and steer any session that stalls or drifts with a follow-up message. (A session reports "idle" until its queued brief is delivered — wait until you have seen "working" and it settles back to "idle", or until the reply is already in the transcript.)
4. When everything settles, review each result against its brief, then give me a table: piece, repository, the PR, and the workspace's deepLink.
```

#### Daily Report [#daily-report]

```text
Use the Conductor API to search my organization's session transcripts with POST /v0/sql (body {"query": "..."}). Queries can only read the view session_transcripts_view; useful columns include workspace_id, workspace_name, workspace_created_at, workspace_creator_id, session_title, transcript, and transcript_updated_at.

Find every workspace that was worked on in the last 24 hours — for example SELECT workspace_id, workspace_name, session_title, transcript FROM session_transcripts_view WHERE transcript_updated_at >= now() - interval '24 hours' ORDER BY transcript_updated_at DESC — then give me a report with one entry per workspace: what is being done in it (summarize the transcripts) and its deepLink (from GET /v0/workspaces/{workspaceId}) so I can open it in Conductor.
```

#### Manage Conductor from your OpenClaw [#manage-conductor-from-your-openclaw]

Agents running outside Conductor just need an API key. This prompt turns any personal agent into a manager for your Conductor fleet:




## Tips [#tips]

* **Always pass an explicit `model`.** The defaults are conservative. Models must match the chosen `agent` (`claude`, `codex`, or `cursor`); the accepted ids are enumerated in the OpenAPI document, and an invalid combination fails with a `400` naming them. An optional `effort` field sets the thinking level for `claude` and `codex` sessions.
* **Wait for `working` before trusting `idle`.** A queued prompt hasn't started a turn yet, and the session reports `idle` until it does. After sending, poll session status until you've seen `working` at least once, then treat the next `idle` as done and read the reply from the transcript. A very fast turn can start and finish between polls, so if `idle` persists, check the transcript for the reply directly.
* **Name what you create.** Unnamed workspaces get a random [city name](/docs/reference/cities); the workspace name also names the git branch.
* **The agent's commits are attributed to you.** The workspace's git identity defaults to the API caller's Conductor account. You can also pass an `env` map at workspace creation to set environment variables for the setup script, agent, and terminals.
* **Send a `User-Agent` header from custom clients.** The API sits behind a proxy that rejects some default client signatures — notably Python's `urllib` — with a `403`. `curl` and `python-requests` work as-is.
* **Check API access inside cloud workspaces.** Every cloud workspace receives `CONDUCTOR_API_URL`. New machine-launched cloud workspaces—the cloud-collab experience for team organizations with cloud access—receive a workspace-scoped `CONDUCTOR_API_TOKEN` by default (older workspaces set `CONDUCTOR_API_KEY` instead). Each user can configure automatic API access separately for each organization in **Settings → User → General**. Changes affect only new workspaces; existing workspaces and tokens are unchanged. Other cloud workspaces do not receive a token automatically. `CONDUCTOR_SESSION_ID` is available to agent processes on current workspaces and can be sent as the `X-Conductor-Session-Id` header to attribute a request to its session.

## Calling the API from a cloud workspace [#calling-the-api-from-a-cloud-workspace]

Use the environment variables below when calling the API from a cloud workspace:

| Variable               | Availability                                                                                                                                                                                                                                                                                                                                  |
| ---------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `CONDUCTOR_API_URL`    | Set in every cloud workspace.                                                                                                                                                                                                                                                                                                                 |
| `CONDUCTOR_API_TOKEN`  | A workspace-scoped token is set by default in new machine-launched cloud workspaces. Each user can configure automatic API access separately for each organization in **Settings → User → General**. Changes affect only new workspaces; existing workspaces and tokens are unchanged. Other cloud workspaces receive no token automatically. |
| `CONDUCTOR_API_KEY`    | Your own Conductor API key when you configure one; it always takes precedence over the workspace token. For compatibility, workspaces that receive an automatic token also set it here when you don't supply your own.                                                                                                                        |
| `CONDUCTOR_SESSION_ID` | Set in agent processes on current workspaces; older workspaces may not have it. Send it as the optional `X-Conductor-Session-Id` header so Conductor can attribute the request.                                                                                                                                                               |

For example:

```bash
curl -H "Authorization: Bearer ${CONDUCTOR_API_KEY:-$CONDUCTOR_API_TOKEN}" \
  ${CONDUCTOR_SESSION_ID:+-H "X-Conductor-Session-Id: $CONDUCTOR_SESSION_ID"} \
  "$CONDUCTOR_API_URL/me"
```
