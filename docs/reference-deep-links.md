---
title: "Deep links"
url: "/docs/reference/deep-links"
description: "Open Conductor and trigger actions via conductor:// URLs"
---

# Deep links



Conductor registers the `conductor://` URL scheme. Clicking a deep link (or opening it via `open conductor://...`) will activate Conductor and trigger the corresponding action.

## Supported links [#supported-links]

### Workspace [#workspace]

```
conductor://prompt=<prompt>&path=<repo path>&branch=<branch name>
```

Opens the new-workspace composer in your active organization. Review the composer, then click **Create** to create the workspace. All parameters are optional and can be combined in any order.

#### `prompt` [#prompt]

Prefills the composer with the prompt, which is sent when the workspace is created.

#### `path` [#path]

Selects the repository to create the workspace in. If no repository matches, Conductor keeps the current or default selection without showing an error.

* **Local organizations:** Matches the absolute root path of a repository added to Conductor, ignoring trailing slashes.
* **Cloud organizations:** First matches the repository's checkout path on the organization's cloud machine, such as `my-app`. Otherwise, it tries the final folder name if that name identifies exactly one repository. For example, `/Users/jane/code/my-app` can select the cloud repository checked out at `my-app`.

#### `branch` [#branch]

Selects an existing branch to create the workspace from. Requires `path`. If the branch doesn't exist on the repository's remote, the composer says so and uses the default branch.

#### `linear_id` [#linear_id]

Creates a workspace for a Linear issue instead of opening the composer. Conductor fetches the issue, detects the matching repository, and either opens an existing workspace on the issue's branch or creates a new one with the issue attached. `prompt` is sent to the new workspace, or drafted in the existing one; `path` and `branch` are ignored. Requires a connected Linear account.

#### Example [#example]

```
conductor://prompt=Fix%20the%20login%20bug&path=%2FUsers%2Fjane%2Fcode%2Fmy-app&branch=fix%2Flogin
```

### Async plan [#async-plan]

```
conductor://async?repo=<repo name>&plan=<base64 plan>
```

Creates a new workspace with a base64-encoded plan attached as a markdown file. The `repo` parameter is optional and defaults to the first repository.

## Notes [#notes]

* All parameter values should be URL-encoded.
* The generic links (`prompt`, `path`, `branch`, `linear_id`) use a flat `key=value&key=value` format directly after `conductor://`, without a hostname or path.
* The `async` link uses standard URL structure with a hostname (`conductor://async?...`).
