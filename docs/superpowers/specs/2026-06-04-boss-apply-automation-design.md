# BOSS Apply Automation Design

## Goal

Provide a local `bash + osascript` automation script that runs on macOS and operates the user's existing Chrome tab on BOSS Zhipin job list pages.

The script should:

- assume the active Chrome tab is already open on a BOSS job search result page
- scan the visible left-side job list from top to bottom
- click each visible job card and inspect the right-side action button
- click `立即沟通` when available
- click `留在此页` when the send-confirmation dialog appears
- skip entries that already show `继续沟通`
- if a click jumps into the chat page directly, go back to the job list and continue
- scroll and repeat until configured stop conditions are reached

## Constraints

- Platform: macOS
- Browser: `Google Chrome`
- Automation stack: `bash` entrypoint plus embedded `osascript`
- No browser driver, remote debugging, or Playwright dependency
- The script should avoid modifying project code outside its own file

## Page Assumptions

The script is intentionally narrow and assumes the current BOSS page matches the UI shape already observed during manual operation:

- left pane contains vertically stacked job cards
- right pane contains either `立即沟通` or `继续沟通`
- after `立即沟通`, BOSS may show a dialog containing `留在此页`
- some jobs may jump straight into `/web/geek/chat`

The script should rely on macOS Accessibility tree traversal and coarse click coordinates for selecting left-side cards, then use Accessibility labels to detect and press right-side controls.

## Runtime Flow

1. Activate Chrome and verify the active tab URL contains `/web/geek/jobs`.
2. For each visible screen:
3. Collect visible left-side job-title nodes from the Accessibility tree.
4. Sort them by Y coordinate and deduplicate repeated labels at the same position.
5. For each visible job node:
6. Click the approximate card position derived from the node coordinates.
7. Wait briefly for the right pane to refresh.
8. Inspect visible text nodes:
9. If `继续沟通` is present, log `skip`.
10. If `立即沟通` is present, press its parent Accessibility element.
11. If `留在此页` appears, press it and log `sent`.
12. If the current URL becomes `/web/geek/chat`, navigate back and log `chat-skip`.
13. If neither actionable state appears, log `unknown`.
14. After the visible items are processed, scroll downward and repeat.
15. Stop when one of these conditions is met:
16. maximum scroll rounds reached
17. repeated rounds produce no newly processed visible items
18. active tab leaves the BOSS jobs page and cannot be recovered

## Script Interface

The script should expose a few environment-tunable parameters near the top:

- `MAX_ROUNDS`: maximum number of scroll rounds
- `MAX_IDLE_ROUNDS`: stop after this many rounds without new processed items
- `SCROLL_PIXELS`: per-round scroll amount
- `CLICK_DELAY`: wait after clicking a job card
- `ACTION_DELAY`: wait after clicking `立即沟通`

The default invocation should be:

```bash
bash scripts/boss_apply_automation.sh
```

## Logging

The script should print compact structured text to stdout, for example:

- round start
- job title
- action result: `sent`, `skip-continue`, `chat-skip`, `unknown`
- final summary counts

This is enough for the user to watch progress and rerun safely.

## Error Handling

- If Chrome is not running, exit with a clear message.
- If the active tab is not a BOSS jobs page, exit with a clear message.
- If Accessibility lookup fails for a single item, log it and continue.
- If a round fails entirely, count it toward idle rounds and continue unless the page context is lost.

## Verification

Manual verification is sufficient for this task:

- open a BOSS jobs result page in Chrome
- run the script
- confirm that `立即沟通` items are sent
- confirm that `继续沟通` items are skipped
- confirm that direct chat jumps return to the list
- confirm the script stops without getting stuck in an infinite loop
