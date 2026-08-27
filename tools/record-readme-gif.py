#!/usr/bin/env python3
"""Re-record the README's GIF: one pass through calling a poll, in light mode.

The README opens with a GIF of the create flow, and a GIF is stale the moment any screen
in that flow changes. This is how it is made again, so that it never has to be made by
hand: it drives a headless Chromium over the DevTools protocol, screenshots each step,
and assembles the frames with ImageMagick.

    ./run.sh readmegif https://whichday.example.org/ [docs/create-a-poll.gif]

Point it at a real deployment rather than at localhost. The share screen shows the voting
link, and `localhost:8080/vote/...` in the README would be a link nobody reading it can
follow. **It calls a real poll on whatever it points at**, which the retention sweep will
clear in ninety days.

What it needs, none of which this repository installs:

  * A Chromium. Playwright's cached one is used if it is there
    (~/.cache/ms-playwright/chromium-*/chrome-linux/chrome); $CHROMIUM overrides.
  * ImageMagick, for the assembly.
  * Python's `websockets` package, for the protocol.

The steps below read the UI's own English labels — "Continue", "Choose the days" — so a
change to `translations.properties` can break the recording rather than the application.
That is the trade for a script with no test behind it: it fails loudly, on the label it
could not find, and the fix is one line.
"""

import asyncio
import base64
import glob
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request

# The app is phone-shaped, so the GIF is too. Captured at twice this and scaled back
# down, because a 400-pixel-wide screenshot of 13-pixel text is a smudge.
WIDTH, HEIGHT, SCALE = 400, 860, 2
GIF_WIDTH = 400

# Long enough to read, short enough that nobody waits for the loop to come round again.
BEAT_SHORT, BEAT, BEAT_LONG = 90, 150, 220

DEFAULT_OUTPUT = os.path.join("docs", "create-a-poll.gif")
DEBUG_PORT = 9333


def chromium():
    """The browser to drive, or a failure that says how to get one."""
    named = os.environ.get("CHROMIUM") or os.environ.get("CHROME")
    if named:
        return named
    cached = sorted(glob.glob(os.path.expanduser(
        "~/.cache/ms-playwright/chromium-*/chrome-linux/chrome")))
    if cached:
        return cached[-1]
    raise SystemExit("No Chromium found. Set CHROMIUM=/path/to/chrome, or install "
                     "Playwright's browsers (npx playwright install chromium).")


def assembler():
    """ImageMagick, under whichever of its two names this machine has."""
    for name in ("magick", "convert"):
        found = shutil.which(name)
        if found:
            return found
    raise SystemExit("ImageMagick is not installed, so the frames cannot be assembled.")


class Recording:
    """A page being driven, and the frames taken off it."""

    def __init__(self, socket, into):
        self.socket = socket
        self.into = into
        self.next_call = 0
        self.frames = []

    async def call(self, method, **params):
        self.next_call += 1
        wanted = self.next_call
        await self.socket.send(json.dumps({"id": wanted, "method": method, "params": params}))
        while True:
            message = json.loads(await self.socket.recv())
            if message.get("id") == wanted:
                if "error" in message:
                    raise SystemExit(f"{method} failed: {message['error']}")
                return message.get("result", {})

    async def evaluate(self, script):
        """Page JavaScript, wrapped so that a step can await its own animations."""
        answer = await self.call("Runtime.evaluate",
                                 expression=f"(async () => {{ {script} }})()",
                                 awaitPromise=True, returnByValue=True)
        return answer.get("result", {}).get("value")

    async def click(self, label):
        """The button whose text says what it does, which is how a reader finds it too."""
        found = await self.evaluate(f"""
            const button = [...document.querySelectorAll('vaadin-button')]
              .find(candidate => candidate.textContent.includes({json.dumps(label)}));
            if (!button) {{ return false; }}
            button.click();
            return true;
        """)
        if not found:
            raise SystemExit(f'No button reading "{label}" — has the copy changed?')

    async def type_into(self, selector, text):
        """A letter at a time, because a field that fills instantly reads as a paste."""
        await self.evaluate(f"""
            const field = document.querySelector({json.dumps(selector)});
            field.focus();
            for (const letter of {json.dumps(text)}) {{
              field.value += letter;
              field.dispatchEvent(new Event('input', {{bubbles: true}}));
              await new Promise(resolve => setTimeout(resolve, 85));
            }}
            field.dispatchEvent(new Event('change', {{bubbles: true}}));
        """)

    async def shot(self, label, hold=BEAT):
        image = await self.call("Page.captureScreenshot", format="png")
        name = os.path.join(self.into, f"frame-{len(self.frames):02d}.png")
        with open(name, "wb") as file:
            file.write(base64.b64decode(image["data"]))
        self.frames.append((name, hold))
        print(f"  frame {len(self.frames):02d}  {label}")


async def walk(page, site):
    """Calling a poll, the way the README says it goes."""
    await page.call("Page.enable")
    await page.call("Runtime.enable")
    await page.call("Emulation.setDeviceMetricsOverride",
                    width=WIDTH, height=HEIGHT, deviceScaleFactor=SCALE, mobile=False)
    # One look for every reader, whatever their own machine prefers.
    await page.call("Emulation.setEmulatedMedia",
                    features=[{"name": "prefers-color-scheme", "value": "light"}])
    await page.call("Page.navigate", url=site)
    # Generous: a cold deployment builds its session and its frontend on this request.
    await asyncio.sleep(5)
    await page.evaluate("document.documentElement.style.colorScheme = 'light';")
    await asyncio.sleep(0.6)

    where = await page.evaluate("return {title: document.title, path: location.pathname};")
    if where.get("path") != "/who":
        raise SystemExit(f"Expected the who-are-you screen, got {where}. Is this "
                         "deployment in anonymous mode?")

    await page.shot("the front door", BEAT)
    await page.type_into("vaadin-text-field:not(.code-box) input", "Ada")
    await asyncio.sleep(0.5)
    await page.shot("a name typed", BEAT)

    await page.click("Continue")
    await asyncio.sleep(2)
    await page.shot("what is it called", BEAT_SHORT)
    await page.type_into("vaadin-text-field input", "Team Event")
    await asyncio.sleep(0.8)
    await page.shot("the event named", BEAT)

    await page.click("Choose the days")
    await asyncio.sleep(2)
    await page.shot("the calendar", BEAT)

    # Forward a month, where a whole month is offered rather than the tail of this one.
    await page.evaluate("""
        const arrows = [...document.querySelectorAll('vaadin-button')]
          .filter(button => (button.getAttribute('aria-label') || '').toLowerCase().includes('next'));
        arrows[0].click();
    """)
    await asyncio.sleep(1.4)
    await page.shot("next month", BEAT_SHORT)

    for index, wanted in enumerate(("9", "10", "16")):
        await page.evaluate(f"""
            const days = [...document.querySelectorAll('[class*="day"]')]
              .filter(node => /^\\d+$/.test(node.textContent.trim())
                              && !node.hasAttribute('disabled')
                              && node.getBoundingClientRect().width > 0);
            (days.find(node => node.textContent.trim() === {json.dumps(wanted)}) || days[0]).click();
        """)
        await asyncio.sleep(0.9)
        await page.shot(f"day {index + 1} picked", BEAT_SHORT)

    await page.click("Next")
    await asyncio.sleep(2.5)
    await page.shot("the link and the code", BEAT_LONG)

    await page.click("Open for answers")
    await asyncio.sleep(2.5)
    await page.shot("open for answers", BEAT_LONG)


def page_socket(port):
    """Chromium's own page target, once it is listening."""
    for _ in range(60):
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/json/list", timeout=1) as answer:
                for target in json.load(answer):
                    if target.get("type") == "page" and target.get("webSocketDebuggerUrl"):
                        return target["webSocketDebuggerUrl"]
        except OSError:
            pass
        time.sleep(0.5)
    raise SystemExit("Chromium never offered a page to drive.")


def assemble(frames, output, magick):
    """Frames into one looping GIF, each held for as long as it was asked to be."""
    arguments = [magick, "-loop", "0"]
    for name, hold in frames:
        arguments += ["-delay", str(hold), name]
    arguments += ["-resize", f"{GIF_WIDTH}x", "-colors", "256", "-layers", "Optimize", output]
    subprocess.run(arguments, check=True)


async def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    site = sys.argv[1]
    output = sys.argv[2] if len(sys.argv) > 2 else DEFAULT_OUTPUT
    try:
        import websockets
    except ImportError:
        raise SystemExit("Python's websockets package is missing: pip install websockets")

    browser, magick = chromium(), assembler()
    with tempfile.TemporaryDirectory(prefix="whichday-gif-") as workspace:
        print(f"Recording {site} with {browser}")
        chrome = subprocess.Popen(
            [browser, "--headless=new", f"--remote-debugging-port={DEBUG_PORT}",
             f"--user-data-dir={os.path.join(workspace, 'profile')}", "--no-first-run",
             "--no-default-browser-check", "--hide-scrollbars", "--disable-gpu",
             "--force-color-profile=srgb", "--mute-audio", "about:blank"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            async with websockets.connect(page_socket(DEBUG_PORT),
                                          max_size=64 * 1024 * 1024) as socket:
                page = Recording(socket, workspace)
                await walk(page, site)
                assemble(page.frames, output, magick)
        finally:
            chrome.terminate()
    held = sum(hold for _, hold in page.frames) / 100
    print(f"Wrote {output}: {len(page.frames)} frames, {held:.1f}s, "
          f"{os.path.getsize(output) // 1024}K")


asyncio.run(main())
