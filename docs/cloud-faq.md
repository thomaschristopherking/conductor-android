---
title: "Cloud FAQ"
url: "/docs/cloud/faq"
description: "Plans, specifications, pricing, and security for Conductor Cloud"
---

# Cloud FAQ



## Features and plans [#features-and-plans]

### How do I get access to Cloud workspaces? [#how-do-i-get-access-to-cloud-workspaces]

Cloud workspaces are available to users on Pro, Teams, or Enterprise plans.

### What are the differences between Pro, Teams, and Enterprise plans? [#what-are-the-differences-between-pro-teams-and-enterprise-plans]

Our *Pro plan* is for individual users who want to get the most out of
Conductor. Pro gets you access to cloud workspaces and all of our latest
features like the Conductor API, our mobile app (coming soon), shared workspaces
with other Pro users, and a whole lot more coming soon.

Our *Team plan* gets you all of Pro's features to use across your company. And
now with Conductor Multiplayer, users in the same Conductor organization can
collaborate live by sharing workspaces and prompting agents together in real
time. Our Team plan also provides centralized billing and an admin portal to
manage user roles within your organization.

The *Enterprise plan* includes all Team features, plus the security and privacy
controls that large organizations expect, such as automated user provisioning,
an SLA for dedicated support, and fine-grained security controls.

[Compare Conductor plans](https://www.conductor.build/pricing).

### How do I sign my company up for a Team plan? [#how-do-i-sign-my-company-up-for-a-team-plan]

Team plans are currently invite-only. If you would like to sign up, [contact
our team](https://www.conductor.build/team-plan).

### Do I have to use API keys to set up inference in cloud workspaces? [#do-i-have-to-use-api-keys-to-set-up-inference-in-cloud-workspaces]

No. As with local Conductor, you can use your own subscriptions, API keys, or
both in cloud workspaces.

### Do I have to pay an additional fee for cloud workspaces? [#do-i-have-to-pay-an-additional-fee-for-cloud-workspaces]

Not right now, but we plan to introduce usage-based pricing for cloud compute
in the future.

### Can an agent set up my Cloud Computer? [#can-an-agent-set-up-my-cloud-computer]

Yes. Finishing Cloud onboarding opens an Admin workspace with a setup prompt
ready for you to review and send, and you can open another one later with
**Configure with an agent** in `Settings → Organization → Cloud Computer`.
See [Edit settings with an agent](/docs/cloud/cloud-computer#edit-settings-with-an-agent).

## Cloud specifications [#cloud-specifications]

### How are cloud workspaces different from local workspaces? [#how-are-cloud-workspaces-different-from-local-workspaces]

When you create a cloud workspace, Conductor automatically creates a Vercel
sandbox for your agent. Your agent runs in the cloud, so it can continue
working if you close the Conductor app.

Local workspace sessions run on your Mac. If you close the app or shut down
your Mac, those sessions end.

### What are the hardware specifications of the sandboxes? [#what-are-the-hardware-specifications-of-the-sandboxes]

Each sandbox has 8 vCPUs, 16 GB of RAM, and 32 GB of ephemeral NVMe storage.
See [Cloud Computer specifications](/docs/cloud/cloud-computer#specifications).

### What comes preinstalled on the sandboxes? [#what-comes-preinstalled-on-the-sandboxes]

Each sandbox runs Amazon Linux 2023 with Node.js 24, npm, pnpm, Python 3, git,
GitHub CLI, jq, ripgrep, Git LFS, tmux, Vim, and Google Chrome preinstalled. You
can install
additional software by [configuring your Cloud
Computer](/docs/cloud/cloud-computer).

### Can I use GitLab, Bitbucket, or other Git hosts? [#can-i-use-gitlab-bitbucket-or-other-git-hosts]

Not yet. Cloud workspaces support GitHub repositories. Use local workspaces
for repositories hosted elsewhere.

### Where can I learn more about Vercel Sandbox? [#where-can-i-learn-more-about-vercel-sandbox]

Read the [Vercel Sandbox documentation](https://vercel.com/docs/sandbox).
Conductor Cloud was built in close collaboration with Vercel.

### Where are cloud workspaces hosted? [#where-are-cloud-workspaces-hosted]

Cloud workspaces run in Vercel sandboxes in the `us-east-1` region.

## Security [#security]

### Does Conductor have access to my data in cloud workspaces? [#does-conductor-have-access-to-my-data-in-cloud-workspaces]

Yes. Unlike local Conductor sessions, chat messages from cloud workspaces are
stored on Conductor's servers.

### Why is data privacy different between Cloud and local Conductor? [#why-is-data-privacy-different-between-cloud-and-local-conductor]

With local Conductor, messages go directly to your coding model provider, and
session data stays on your device. Conductor does not access your coding inputs
or outputs.

With Conductor Cloud, sessions run in managed cloud sandboxes instead of on
your device. Conductor must store session inputs and outputs on its servers to
support activity in cloud workspaces.

### Can I use my own cloud machines instead of Conductor's? [#can-i-use-my-own-cloud-machines-instead-of-conductors]

Not yet, but we are building a self-hosted cloud offering. If you are
interested in exploring it as part of an Enterprise plan, [contact our
team](/enterprise).
