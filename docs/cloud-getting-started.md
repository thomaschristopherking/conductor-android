---
title: "Set up cloud"
url: "/docs/cloud/getting-started"
description: "Connect GitHub, add an agent credential, and set up your Cloud Computer"
---

# Set up cloud



This tutorial takes you through Cloud onboarding, ending with an agent ready
to set up your organization's development environment.

You need access to Conductor Cloud, a GitHub repository, and a subscription or
API key for at least one supported agent.

## Create or select the organization [#create-or-select-the-organization]

1. Sign in to the Conductor Mac app.
2. Open the organization switcher and choose **Create organization**.
3. Name the organization and optionally invite teammates in the browser, then
   choose **Continue in Conductor**.

If you're joining an organization that has already finished onboarding, you
can create a workspace right away — just add your agent credential when
prompted.

## Choose the computer color [#choose-the-computer-color]

This is just for fun :)

## Connect GitHub and choose repositories [#connect-github-and-choose-repositories]

Connect the Conductor GitHub App if prompted, then select at least one
repository to clone onto the Cloud Computer. Conductor starts building the
computer when you continue.

You can add repositories later, and connect the GitHub App to additional
GitHub organizations.

## Add an agent [#add-an-agent]

Add a personal subscription token or API key for at least one agent. If you
already configured credentials in local Conductor, use **Import** when it is
available.

Organization admins can also add shared API keys in
`Settings → Organization → Agents` for everyone in the organization to use. A
shared key takes precedence over a member's personal API key for the same
agent.

**Finish** becomes available once your credential is ready and the computer
build has succeeded.

## Set up the computer with the agent [#set-up-the-computer-with-the-agent]

Finishing onboarding opens an Admin workspace with a suggested first prompt:

> Set up this computer for development. Check that you can run tests and
> applications in all the repos.

Edit it if you like, then send it. The agent can test your repositories,
update install and setup scripts, and run builds — without asking you to
confirm each step. See
[Edit settings with an agent](/docs/cloud/cloud-computer#edit-settings-with-an-agent).

## Create your first workspace [#create-your-first-workspace]

When the agent is done, press `⌘N`. Pick a repository and an optional starting
branch, pull request, or issue, then send the agent its first task.

## Next steps [#next-steps]

* [Work with cloud workspaces](/docs/cloud/working-with-cloud-workspaces)
* [Multiplayer](/docs/cloud/collaboration)
* [Configure the Cloud Computer](/docs/cloud/cloud-computer)
