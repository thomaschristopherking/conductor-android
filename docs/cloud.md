---
title: "What is Conductor Cloud?"
url: "/docs/cloud"
description: "Run coding agents in shared cloud workspaces with your team"
---

# What is Conductor Cloud?



Conductor Cloud runs every agent in its own isolated cloud sandbox, with your
repos and dependencies pre-installed. Sandboxes spin up in seconds, and agents
keep working after you close your laptop. Workspaces and chats are shared with
your organization, so a teammate can open the same workspace, follow the same
conversation, and pick up where you left off.

New here? [Set up cloud](/docs/cloud/getting-started).




## How Cloud is organized [#how-cloud-is-organized]

| Concept         | What it means                                                                                                |
| --------------- | ------------------------------------------------------------------------------------------------------------ |
| Organization    | Your team in Conductor: its people, repositories, workspaces, and shared settings.                           |
| Cloud Computer  | The organization's shared environment: repositories, environment variables, secrets, and installed software. |
| Cloud workspace | An isolated sandbox for one repository and branch, created from the Cloud Computer's active build.           |

## Your Cloud Computer [#your-cloud-computer]

Each organization shares one Cloud Computer: the repositories, environment
variables, secrets, and software every new workspace starts with. Change its
configuration, then choose **Build computer**; new workspaces start from the
latest successful build, while existing workspaces keep the environment they
were created with.




You can also have an agent configure the computer for you: it can test your
repositories, edit setup scripts, and run builds. See
[Cloud Computer](/docs/cloud/cloud-computer).

## Multiplayer [#multiplayer]

Workspaces, chats, and presence are shared across the organization: browse
everyone's work in **Home**, contribute to a teammate's chat, follow
workspaces you care about, and hand work off with **Reassign to**. See
[Multiplayer](/docs/cloud/collaboration).

## Local and Cloud workspaces [#local-and-cloud-workspaces]

You can use both kinds of workspace from the Conductor Mac app.

|                         | Local workspace                     | Cloud workspace                                                                                                      |
| ----------------------- | ----------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| Files and agents run on | Your Mac                            | An isolated Linux sandbox                                                                                            |
| Team visibility         | Stored in your local Conductor app  | Shared with the organization                                                                                         |
| Environment             | Your Mac and local project settings | The active Cloud Computer build                                                                                      |
| Access from local tools | Directly through the local worktree | Through SSH or file sync                                                                                             |
| After your Mac closes   | Local processes stop                | Agents keep running until the sandbox [sleeps](/docs/cloud/working-with-cloud-workspaces#sleep-and-maximum-lifetime) |

The chat, diff, git, terminal, and pull request workflow is the same in both.

## Get help [#get-help]

Choose `Help → Send Feedback` in Conductor or email
[humans@conductor.build](mailto:humans@conductor.build). Include the
organization, workspace, and repository names when reporting a Cloud issue.
