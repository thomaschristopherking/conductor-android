# Conductor API notes for the Android client

These notes summarise the parts of the Conductor Cloud API that the app uses.
The source of truth is `docs/openapi.json` (OpenAPI 3.0.3, "Roundhouse public API", version 0.0.1).
I checked each statement below against the live API on 2026-10-05 with read-only `GET` requests.

## Authentication

- The base URL is `https://api.conductor.build`. The public endpoints are under `/v0`. The identity endpoint is `/me`, with no `/v0` prefix.
- Each request sends the header `Authorization: Bearer <API key>`.
- A user creates a key at `https://app.conductor.build/home/api-keys`.
- A bad key gives HTTP 401 with this body:

```json
{"userMessage":"Unauthorized client request","code":"UNAUTHORIZED","source":"network"}
```

- The app uses `GET /me` to test a key. It is the smallest read-only call.

## Hierarchy

A project contains workspaces. A workspace contains sessions. A session contains messages.

- A **project** is a Git repository that the user can create workspaces in. Fields: `id`, `name`, `gitRemote`.
- A **workspace** is a cloud machine with a checkout of the repository. Fields: `id`, `projectId`, `name`, `state`, `lifecycleStep`, `repoUrl`, `createdAt`, `deepLink`, `creatorId`, `creatorName`, `lastActivityAt`.
- A **session** is one agent chat in a workspace. Fields: `id`, `deepLink`, `name`, `model`, `resolvedModel`, `effort`, `fastMode`, `archivedAt`.
- A **message** is one transcript event. Fields: `id`, `sessionId`, `sessionIndex`, `type`, `content`, `receivedAt`.

## Pagination and errors

- List endpoints take `limit` and `offset`. They return `{ "data": [...], "offset": n, "hasMore": bool }`.
- An error body is a `StructuredError`. Only `userMessage` is required. The live API also sends `code`, `source`, `traceId` and sometimes `details`.
- The live API returns 404 with `code: "NOT_FOUND"` and 400 with `code: "INVALID_REQUEST"`.
- The spec does not document HTTP 429. The app treats 429 and 5xx as errors that the user can retry.

## Endpoints the app uses

| Method and path | Use in the app |
| --- | --- |
| `GET /me` | Test the API key on the settings screen. |
| `GET /v0/projects` | Projects screen. |
| `GET /v0/workspaces?repo={projectId}&includeArchived=…` | Workspaces of one project. See the discrepancy notes. |
| `GET /v0/workspaces/{id}` | Workspace detail header. |
| `POST /v0/workspaces` | Create a workspace and its first session. |
| `POST /v0/workspaces/{id}/rename` | Rename a workspace. Body: `{"name": "..."}`. |
| `POST /v0/workspaces/{id}/archive` | Archive a workspace. No body. |
| `GET /v0/workspaces/{id}/sessions` | Sessions of a workspace. `includeArchived=true` adds archived sessions. |
| `POST /v0/sessions` | Create a session. Body needs `workspaceId` and `agent`. |
| `GET /v0/sessions/{id}` | Session header. |
| `GET /v0/sessions/{id}/status` | The agent status of a session. |
| `GET /v0/sessions/{id}/messages` | Read the transcript. |
| `POST /v0/sessions/{id}/messages` | Send a prompt. Body: `{"message": "...", "messageId": "<uuid>"}`. |
| `POST /v0/sessions/{id}/cancel` | Stop the current agent turn. |
| `GET /v0/favorite-models` | Put the user's favourite models first in the model picker. |

## Status values

- Workspace `state`: `initializing`, `ready`, `sleeping`, `archived`, `deleted`, `updating`, `unstarted`.
- Workspace `lifecycleStep`: `building_snapshot`, `preparing`, `setting_up`, `updating`.
- Session status, from `GET /v0/sessions/{id}/status`: `idle`, `working`, `error`. The response also has `updatedAt`, and can have `errorMessage`, `lastError` and `lastErrorAt`.
- `idle` means that the agent waits for the user.
- The session object itself has no status field. The app calls the status endpoint once for each session.
- The docs say: "The session reports idle until the queued brief is delivered." After the app sends a message, it continues to poll for a short time, even when the status is `idle`.

## Incremental transcript polling

1. Load the first page with `GET /v0/sessions/{id}/messages?limit=200`. Continue with `after=<last id>` while `hasMore` is `true`.
2. Remember the `id` of the last message.
3. Poll with `GET /v0/sessions/{id}/messages?after=<last id>&limit=200`. The response holds only newer messages, in ascending `sessionIndex` order.
4. Do not combine `after` with `offset`. The live API answers 400: `Cannot combine \`after\` with \`offset\`; use one or the other.`
5. Poll the status endpoint at the same time. Stop when the status is `idle` or `error`.

`sessionIndex` values can have gaps. For example, a live session has indexes 1, 3, 4, with no 2.

## Message content

The spec types `content` as "any". The live API sends two `type` values:

- `userMessage`: `content.message` is the prompt text. `content.state` is `sent` or `queued`. `content.config` holds the model settings. `content.attachments` can list files.
- `agent`: `content.rawPayload` is one event from the agent. For Claude sessions, it is one Claude Code stream-JSON event.

Values of `rawPayload.type` seen in the live API:

| `rawPayload.type` | Meaning | Shown in the app |
| --- | --- | --- |
| `assistant` | `message.content[]` holds `text`, `thinking` and `tool_use` blocks. | Text as markdown. Tool calls as one-line chips. Thinking is hidden. |
| `user` | `message.content[]` holds `tool_result` blocks, or `text` for injected prompts. | Tool results as collapsed chips. |
| `result` | End of a turn. Fields: `subtype`, `is_error`, `result`, `total_cost_usd`, `duration_ms`. | A small "turn complete" divider. |
| `system` | Subtypes: `init`, `hook_started`, `hook_response`, `session_state_changed`, `task_started`, `task_notification`, `task_updated`, `background_tasks_changed`, `vcs_state_changed`. | Hidden. |
| `rate_limit_event`, `tool_progress`, `command_lifecycle` | Agent housekeeping. | Hidden. |

The account that I used has no Codex or Cursor sessions. The app shows an unknown event as a short grey line when it has text, and hides it when it has no text.

## Create a workspace

`POST /v0/workspaces` takes one of two bodies:

- `projectId` (required in this form), or
- `repositoryUrl` (required in the other form).

Optional fields: `branch`, `name`, `sessionName`, `agent`, `model`, `effort`, `fastMode`, `env`, `access`, `message`, `source`.
The app sends `projectId`, `name`, `branch`, `agent`, `model`, `effort` and `message`.
The response (201) is `{ workspaceId, sessionId, deepLink, initialMessage? }`.
Creation is not idempotent. The app does not retry a create request automatically.

## Create a session

`POST /v0/sessions` requires `workspaceId` and `agent`. Optional fields: `sessionId`, `name`, `model`, `effort`, `fastMode`, `messageId`, `message`.
The response (201) is a session plus an optional `initialMessage`.

## Agents, models and effort levels

From the spec:

- Agents: `claude`, `codex`, `cursor`, `acp`. The app does not offer `acp`, because the spec lists no models for it.
- `claude` models: `fable-5-1`, `fable-5`, `opus-5-5-1m`, `opus-5-1m`, `opus-4-8-1m`, `opus-4-8`, `opus-4-7-1m`, `opus-4-7`, `opus-4-6-1m`, `sonnet-5-5-1m`, `sonnet-5-1m`, `sonnet-4-6-1m`, `sonnet-4-6`, `haiku-4-5`.
- `codex` models: `gpt-5.5`, `gpt-5.4`, `gpt-5.6-sol`, `gpt-5.6-terra`, `gpt-5.6-luna`, `gpt-5.3-codex-spark`, `gpt-5.3-codex`, `gpt-5.2-codex`, `gpt-6-astra`, `gpt-6.1-sol`, `gpt-6-sol`, `gpt-6-luna`, `gpt-daybreak-blue-latest`.
- `cursor` models: `auto`, `composer-2.5`, `grok-4.7`, `grok-4.6`, `grok-4.5`.
- Effort levels: `claude`: low, medium, high, xhigh, max. `codex`: none, low, medium, high, xhigh, max, ultra. `cursor`: low, medium, high, xhigh.
- Default model when the caller omits it: `claude`: `opus-5-1m`. `codex`: `gpt-6.1-sol`. `cursor`: `composer-2.5`.

## Deep links

Workspaces and sessions have a `deepLink`, for example `conductor://workspace?id=<workspaceId>&session=<sessionId>`.
The link opens the Conductor desktop app. Android cannot open it, so the app offers "Copy link" and "Share".

## Discrepancies between the docs and the live API

1. **`GET /v0/projects/{id}/workspaces` ignores archive state.** It returns archived workspaces mixed with active ones, and it has no `includeArchived` parameter. For one project, the first 100 results were all archived. The app uses `GET /v0/workspaces?repo={projectId}` instead. That endpoint hides archived workspaces by default and accepts `includeArchived=true`.
2. **`GET /v0/workspaces` leaves out `projectId`.** The spec marks `projectId` as optional, and this endpoint never sends it. `GET /v0/projects/{id}/workspaces` and `GET /v0/workspaces/{id}` do send it.
3. **No branch field.** The task asked for the branch on the workspace list. No workspace endpoint returns a branch. The app shows the repository name instead.
4. **No session status in the session object.** The app calls `GET /v0/sessions/{id}/status` for each session.
5. **`content` is untyped in the spec.** The section "Message content" above describes the shape that the live API sends.
6. **The `/me` endpoint is outside `/v0`.**
7. **Python's default user agent gets HTTP 403.** The edge network blocks the `Python-urllib` user agent. An `okhttp/5.5.0` user agent gets HTTP 200. The capture script sends a curl user agent.

## Fixtures

`scripts/capture_fixtures.py` saves scrubbed live responses to `app/src/test/resources/fixtures/`. It replaces names, e-mail addresses, repository URLs and free text with example values. It aborts if the API key appears in an output file.
