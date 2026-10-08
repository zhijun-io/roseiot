# Issue tracker: GitHub

Issues and specs for this repository live in GitHub Issues for
`zhijunio/roseiot`. Use the `gh` CLI for all operations.

## Conventions

Run commands inside this repository so `gh` can infer the repository from the
Git remote.

- Create an issue:

  ```sh
  gh issue create --title "..." --body "..."
  ```

- Read an issue:

  ```sh
  gh issue view <number> --json number,title,body,labels,comments
  ```

- List issues:

  ```sh
  gh issue list --state open \
    --json number,title,body,labels,comments \
    --jq '[.[] | {
      number,
      title,
      body,
      labels: [.labels[].name],
      comments: [.comments[].body]
    }]'
  ```

  Add appropriate `--label` and `--state` filters.

- Comment on an issue:

  ```sh
  gh issue comment <number> --body "..."
  ```

- Apply or remove labels:

  ```sh
  gh issue edit <number> --add-label "..."
  gh issue edit <number> --remove-label "..."
  ```

- Close an issue:

  ```sh
  gh issue close <number> --comment "..."
  ```

Use a heredoc instead of escaping a complex multi-line issue body.

## Sub-issues

With a compatible `gh` version, create or attach a sub-issue using:

```sh
gh issue create --parent <parent> ...
gh issue edit <parent> --add-sub-issue <child>
```

If native sub-issues are unavailable, put `Part of #<parent>` at the top of
the child issue body.

## Pull requests as a triage surface

**PRs as a request surface: no.**

Do not treat external pull requests as triage requests unless this policy is
explicitly changed.

GitHub uses one number space for issues and pull requests. If `#<number>` is
ambiguous, try `gh pr view <number>` and then `gh issue view <number>`.

## Skill operations

When a skill says “publish to the issue tracker”, create a GitHub issue in
`zhijunio/roseiot`.

When a skill says “fetch the relevant ticket”, read the corresponding GitHub
issue, including its body, labels, and comments.

## Wayfinding operations

- **Map**: one issue labelled `wayfinder:map`, containing notes, decisions and
  unresolved areas.
- **Child ticket**: a GitHub sub-issue of the map, labelled
  `wayfinder:research`, `wayfinder:prototype`, `wayfinder:grilling` or
  `wayfinder:task`.
- **Blocking**: use GitHub issue dependencies when available. Otherwise, add
  `Blocked by: #<number>` at the top of the child issue body.
- **Claim**: assign the issue to the current user before starting work:

  ```sh
  gh issue edit <number> --add-assignee @me
  ```

- **Resolve**: comment with the result, close the child issue, then add a
  context pointer to the map.
