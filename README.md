# sol

<img src="images/sol.svg">

Sol is a command line tool that allows you to chat with your git history.

# 📊 Capabilities Overview

## **Commit Metrics**

- **Tracking:** Total commits, commit frequency (daily, weekly, monthly).
- **Details:** Average time between commits, commits per author/file, commit size distribution.
- **Insights:** Identify significant refactoring or feature additions.

## **Author Metrics**

- **Contributors:** Total number of contributors, top contributors by commits or lines changed.
- **Collaboration:** Networks of authors collaborating on the same files.
- **Activity:** Developer activity patterns.

## **Code Change Metrics**

- **Line Changes:** Total lines added, modified, deleted, and overall code churn.
- **Hotspots:** Files/modules with the most changes and high code churn.
- **Complexity:** Modification complexity and change coupling between files.

## **Productivity Metrics**

- **Commit Activity:** Average commits per author and active development periods.
- **Analysis:** Compare productivity across authors/teams, impact of team size, feature vs. maintenance efforts.

## **Branching and Merging Metrics**

- **Branch Activity:** Number, frequency, and duration of branches.

## **Collaboration Metrics**

- **Team Dynamics:** Degree of collaboration, social network analysis, and knowledge distribution.

## **Technical Debt & Maintenance**

- **Debt Indicators:** Frequent bug areas.
- **Maintenance Patterns:** Time spent on maintenance vs. new features and trends over time.

## **Release Metrics**

- **Insights:** Commits per release, code churn, time between releases, and deployment frequency.
- **Lead Time:** Time from commit to release.

## **Additional Features**

- **Geographical & Temporal Analysis:** Commit activity by location and time (weekdays vs. weekends).
- **Specialized Metrics:** Expertise identification, bug-related patterns, refactoring activity, skill gaps, and file stability.

# Installation

```bash
brew install ludovicianul/tap/sol
```

There are binaries available for Linux and macOS as well as cross-platform `uberjar`. You can download them from the [releases page](https://github.com/ludovicianul/sol/releases)

# How to use sol?

## Pre-requisites - OpenAI or Anthropic API key

Sol uses by default the OpenAI API to generate responses to your questions. You need to have an API key to use it.

```bash
export OPENAI_API_KEY=your-api-key
```

You can also use the Anthropic API instead. To do so, you need to set the `ANTHROPIC_API_KEY` environment variable.

```bash
export ANTHROPIC_API_KEY=your-api-key
```

## Prerequisites - Indexing your git history

Before being able to query data from your git history, you need to index it. To do so, run the following command:

```bash
sol --index
```

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://github.com/ludovicianul/sol/blob/9e2479709…8cf2a91d14fdc45ba7e05b0/images/index.gif?raw=true">
  <source media="(prefers-color-scheme: light)" srcset="https://github.com/ludovicianul/sol/blob/9e2479709…8cf2a91d14fdc45ba7e05b0/images/index.gif?raw=true">
  <img width="600" alt="sol --index" src="https://github.com/ludovicianul/sol/blob/9e2479709…8cf2a91d14fdc45ba7e05b0/images/index.gif?raw=true">
</picture>

This will create a `.sol` directory at the root of your repository and store the necessary data to query your git history.

## Querying your git history

Once the indexing is done, you can start querying your git history. Here are a few examples:

```bash
sol -q "What is the average time between commits?"
```

```bash
sol -q "Calculate the bus factor for all developers"
```