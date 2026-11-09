---
name: visual-review
description: Hand screenshots of your UI work to the user for visual review in Claude Code Manager. Use after UI changes the user wants to check visually, or when the user asks for a visual review or screenshots.
---

# Visual review in cc-manager

This session runs inside Claude Code Manager. Its `cc-manager` MCP server has the tool `submit_screenshots`; the user looks at the images in the Review tab and marks what is wrong.

## Produce the screenshots

The user chooses how. If the request does not say, use offscreen mode and say so in your reply.

- Offscreen: render the UI to PNG without a window. In the cc-manager repo: `./gradlew gitShots --args="--out=<dir> --only=<names> --dark --size=1280x820"` and the preview shot tooling. In other projects use that project's screenshot or preview tooling, or Playwright.
- Real app: launch the app and capture its window. In the cc-manager repo: `./gradlew runPreview --args="--shots --out=<dir>"`.

Save the PNGs into a fresh temp folder and use ABSOLUTE paths. Never paste image data into the chat.

## Submit

Call `submit_screenshots` with:

- `title`: what is being reviewed.
- `note`: one or two sentences on what changed.
- `images`: `{path, label}` per file, the label naming the screen and state.

The first `submit_screenshots` call asks the user's permission; ask the user to allow it for the session. Screenshots must contain only the app under review, never other windows, secrets or personal data.

## Then stop

After submitting, stop and wait. Do not poll. The user sends the feedback later as a prompt that points at a `review.md` with numbered marks. Numbers in the feedback are per image (`image <k> #<n>`). Fix every numbered mark, then submit new screenshots the same way.
