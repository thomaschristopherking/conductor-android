#!/usr/bin/env python3
"""Capture scrubbed read-only responses from the live Conductor API as unit-test fixtures.

Usage: CONDUCTOR_API_KEY=... python3 scripts/capture_fixtures.py
The script only sends GET requests. It never writes the API key to disk.
"""
import json
import os
import re
import sys
import urllib.error
import urllib.request

BASE_URL = "https://api.conductor.build"
OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "test", "resources", "fixtures")
API_KEY = os.environ["CONDUCTOR_API_KEY"]
MAX_LIST_ITEMS = 3
STRUCTURAL_KEYS = {
    "id", "type", "subtype", "role", "name", "model", "state", "status", "stop_reason", "terminal_reason",
    "sessionId", "eventId", "turnId", "userMessageId", "senderId", "senderSessionId", "deliveryAttemptId",
    "uuid", "session_id", "tool_use_id", "parent_tool_use_id", "tool_name", "command_uuid", "receivedAt",
    "timestamp", "createdAt", "lastActivityAt", "updatedAt", "archivedAt", "deepLink", "resolvedModel",
    "effort", "agent", "collaborationMode", "thinkingLevel", "resume", "assetId", "task_id", "hook_id",
    "hook_event", "hook_name", "rateLimitType", "overageStatus", "service_tier", "workspaceId", "projectId",
    "creatorId", "userId", "organizationId", "authMethod", "code", "source", "traceId", "request_id",
    "repoUrl", "gitRemote", "creatorName", "email", "userMessage",
}
FREE_TEXT = "Example text."
MARKDOWN_SAMPLE = (
    "I updated the parser. Here is the change:\n\n"
    "```kotlin\nfun parse(input: String): Int = input.trim().toInt()\n```\n\n"
    "- Added **tests**\n- Ran `./gradlew test`"
)


def fetch(path, key=API_KEY):
    request = urllib.request.Request(
        BASE_URL + path,
        headers={"Authorization": f"Bearer {key}", "User-Agent": "curl/8.5.0"},
    )
    try:
        with urllib.request.urlopen(request) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        return json.load(error)


class Scrubber:
    def __init__(self):
        self.replacements = {}

    def add(self, real, fake):
        if real and real not in self.replacements:
            self.replacements[real] = fake

    def text(self, value):
        for real, fake in sorted(self.replacements.items(), key=lambda item: -len(item[0])):
            value = value.replace(real, fake)
        return re.sub(r"github\.com/(?!example-)[\w.-]+/[\w.-]+", "github.com/example-org/example-repo", value)

    def value(self, value, key=None):
        if isinstance(value, dict):
            return {k: self.value(v, k) for k, v in value.items()}
        if isinstance(value, list):
            return [self.value(v, key) for v in value[:MAX_LIST_ITEMS]] if key in LONG_LISTS else [self.value(v, key) for v in value]
        if isinstance(value, str):
            return self.text(self.free_text(key, value))
        return value

    @staticmethod
    def free_text(key, value):
        if key == "text":
            return MARKDOWN_SAMPLE
        if key in ("thinking", "signature"):
            return ""
        if key in STRUCTURAL_KEYS or re.fullmatch(r"[\w.\-:\[\]]{0,48}", value):
            return value
        return FREE_TEXT


LONG_LISTS = {"tools", "skills", "slash_commands", "terminal_slash_commands", "agents", "plugins", "mcp_servers", "memory_paths"}


def message_kind(message):
    payload = message["content"].get("rawPayload") if isinstance(message["content"], dict) else None
    if not isinstance(payload, dict):
        return (message["type"],)
    block_types = tuple(block.get("type") for block in payload.get("message", {}).get("content", []) if isinstance(block, dict))
    return (message["type"], payload.get("type"), payload.get("subtype"), block_types)


def one_message_per_kind(session_ids):
    chosen = {}
    for session_id in session_ids:
        after = None
        while True:
            page = fetch(f"/v0/sessions/{session_id}/messages?limit=200" + (f"&after={after}" if after else ""))
            for message in page["data"]:
                chosen.setdefault(message_kind(message), message)
            if not page["hasMore"] or not page["data"]:
                break
            after = page["data"][-1]["id"]
    return sorted(chosen.values(), key=lambda m: (m["sessionId"], m["sessionIndex"]))


def main():
    scrubber = Scrubber()
    me = fetch("/me")
    scrubber.add(me.get("email"), "user@example.com")
    scrubber.add(me.get("name"), "Test User")
    scrubber.add(me.get("apiKey", {}).get("id"), "api_key_EXAMPLE")
    projects = fetch("/v0/projects")
    for index, project in enumerate(projects["data"]):
        owner_repo = project["gitRemote"].removeprefix("https://github.com/")
        scrubber.add(owner_repo, f"example-org/example-project-{index + 1}")
        scrubber.add(project["name"], f"example-project-{index + 1}")
    workspaces = fetch("/v0/workspaces?limit=5")
    project_id = next(w for w in fetch("/v0/projects/" + projects["data"][0]["id"] + "/workspaces?limit=100&offset=100")["data"] if w["state"] != "archived")["projectId"]
    listed_workspaces = fetch("/v0/workspaces?limit=100")["data"] + fetch("/v0/workspaces?limit=100&state=archived")["data"]
    for index, listed_workspace in enumerate(listed_workspaces):
        scrubber.add(listed_workspace["name"], f"Example workspace {index + 1}")
    sections = fetch("/v0/sections")
    for index, section in enumerate(sections["data"]):
        scrubber.add(section["name"], f"Example section {index + 1}")
    scrubber.add(os.environ.get("FIXTURE_GITHUB_OWNER", "thomaschristopherking"), "example-user")
    workspace = workspaces["data"][-1]
    sessions = fetch(f"/v0/workspaces/{workspace['id']}/sessions")
    session = sessions["data"][0]
    first_page = fetch(f"/v0/sessions/{session['id']}/messages?limit=12")
    all_sessions = [s for w in workspaces["data"] for s in fetch(f"/v0/workspaces/{w['id']}/sessions")["data"]]
    for index, listed_session in enumerate(all_sessions):
        scrubber.add(listed_session.get("name"), f"Example session {index + 1}")
    responses = {
        "me.json": me,
        "projects.json": projects,
        "project.json": fetch(f"/v0/projects/{project_id}"),
        "project_workspaces.json": fetch(f"/v0/projects/{project_id}/workspaces?limit=3"),
        "workspaces.json": workspaces,
        "workspaces_by_repo.json": fetch(f"/v0/workspaces?repo={project_id}"),
        "workspace.json": fetch(f"/v0/workspaces/{workspace['id']}"),
        "workspace_status.json": fetch(f"/v0/workspaces/{workspace['id']}/status"),
        "sessions.json": sessions,
        "session.json": fetch(f"/v0/sessions/{session['id']}"),
        "session_status.json": fetch(f"/v0/sessions/{session['id']}/status"),
        "messages_page.json": first_page,
        "messages_after.json": fetch(f"/v0/sessions/{session['id']}/messages?after={first_page['data'][-1]['id']}&limit=5"),
        "messages_all_kinds.json": {"data": one_message_per_kind([s["id"] for s in all_sessions]), "offset": 0, "hasMore": False},
        "favorite_models.json": fetch("/v0/favorite-models"),
        "sections.json": sections,
        "error_401.json": fetch("/v0/projects", key="invalid-key"),
        "error_404.json": fetch("/v0/sessions/00000000-0000-0000-0000-000000000000/status"),
        "error_400.json": fetch(f"/v0/sessions/{session['id']}/messages?after=x&offset=1"),
    }
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    for file_name, body in responses.items():
        text = json.dumps(scrubber.value(body), indent=2, ensure_ascii=False) + "\n"
        if API_KEY in text:
            sys.exit(f"API key found in {file_name}; aborting")
        with open(os.path.join(OUTPUT_DIR, file_name), "w") as output:
            output.write(text)
    print(f"Wrote {len(responses)} fixtures to {os.path.normpath(OUTPUT_DIR)}")


if __name__ == "__main__":
    main()
