# Notification overlay

Azkar draws its own reminders instead of asking the operating system to draw them. This document
covers why, how the pieces fit, what the user-visible policies are, and — most of it — what the
overlay can and cannot do on each platform, because most of that half is not answerable by a test.

## Architecture

```
NotificationRuntime          the scheduler: queue slots, prayer times, 30s tick
        │  NotificationNotice(title, message, action)
        ▼
NotificationPublisher        the only thing the scheduler is allowed to ask for
        ▼
NotificationDispatcher       remembers where the last notice wanted to go, then forwards it
        ▼
NotificationPresenter       the overlay's state machine: stack cap, de-dup, expiry, hover pause
        │
        ▼  StateFlow<List<PresentedNotice>>
NotificationOverlayWindow     one undecorated, transparent, always-on-top, unfocusable window
    ▼
NotificationCard             AzkarCard — the app's own card, themed
```

The point of the seams is testability. `NotificationRuntime` decides *when* a passage is due and
never learns whether the result is a Compose card or nothing at all. `NotificationPresenter`
decides *what is on screen, for how long, and what happens on arrival* and has no window in it at
all, so every rule below is unit-tested in milliseconds with an injected clock. Only
`NotificationOverlayWindow` and `NotificationCard` touch AWT.

There is one channel. An OS notification service used to sit beside the overlay behind an opt-in
setting — the same announcement drawn by the desktop in the desktop's own styling, expiring on a
schedule the app could not read and refusing to be paused by a hover. It was removed rather than
defaulted off, because two drawings of one reminder is one reminder too many and the second is
never the one that matches the app. Nothing about the overlay or the scheduler was changed to
accommodate that: the seam was already there.

Everything above the window is a Koin singleton, resolved before any window exists, because the
state the tray leaves the app in is *"no window at all"* and a reminder still has to arrive there.

## The overlay window

One window, created once, hidden when there is nothing to say. Not one window per card: a second
top-level frame per reminder is how an app ends up with three tray entries, three focusable
surfaces and three things to dispose at Quit.

| Flag | Value | Why |
| --- | --- | --- |
| `undecorated` | `true` | No title bar, no border. The card is the whole window. |
| `transparent` | `true` | The window is only the card's shape plus a screen-edge inset. |
| `alwaysOnTop` | `true` | The reminder has to appear over whatever the user is doing. |
| `focusable` | `false` | The keyboard must stay with whatever the user was typing in. |
| `resizable` | `false` | Sized from `maxVisible` and the placement. |

Two consequences of the Compose Desktop implementation are load-bearing and easy to undo by accident:

- **Transparency may only be set while the window is not showing.** AWT throws otherwise. The window
  is created with `visible = false`, and only `visible` is ever toggled afterwards.
- **Toggling `visible = false` does not dispose the window.** Compose keeps the AWT frame and its
  state, so the second card costs nothing and the window does not flicker back into existence.

Position is `WindowPosition.Aligned(...)`, which resolves against the graphics configuration the
window was seeded with — the screen the user was last on — so a second monitor needs no
configuration at all.

## Queueing and replacement policy

Documented here rather than left to the code, because "why did I only see two cards" is the question
it has to answer.

- **Cap.** At most `maxVisible` cards (default 3, 1–5).
- **Arrival.** A new card goes to the front, which is the corner the overlay is anchored to, so the
  stack grows inwards and never pushes the card being read.
- **Replacement.** Exceeding the cap *drops* the oldest card. It is dropped rather than covered: a
  reminder that has been on screen for a while and is then hidden reads as a bug, not as a reminder.
- **De-duplication.** A notice matching one already on screen (title, message and action) is refused,
  so a short interval cannot produce three identical cards for the same passage.
- **Expiry.** Each card runs its own countdown from acceptance and expires on its own.
- **Pause.** Hovering freezes the countdown. Hover is the only signal available because the window is
  unfocusable by design.
- **Dismissal.** Closing a card has no effect on the scheduler: the queue's progress is committed
  before the card exists, so a dismissed card was already delivered.
- **Quitting.** Tray → Quit cancels the scheduler's scope, disposes the overlay, closes the database
  and the location client, stops Koin, and exits.

Settings live in **Reminders → Notification appearance**: duration, placement, stack size, card
sizing and the language the reminder is written in. They are separate from the queue's own settings
on purpose — turning a reminder off is not the same decision as changing how a reminder looks.

## What the scheduler announces

The scheduler polls every thirty seconds and announces two things: a passage from the shared queue
and a prayer time. The rule that decides *whether* either of them is announced is one sentence long:

> **A reminder is announced only if it became due after the scheduler started.**

What came due while the app was closed is **missed, not replayed** — the same answer the schedule
already gives a slot it slept through. A fresh install opens with nothing on screen at all.

This is the answer to a bug that was reported as "a notification pops up exactly when the app is
opened for the first time after installing it". Both of the scheduler's paths were catch-up paths:
the queue treats the most recent slot in its window as due, and a prayer stays due for two minutes
after its time. The scheduler's first poll runs the moment the app opens, so anything that had come
due while the app was closed was announced at the door — and on a fresh install, where nothing had
ever been delivered, that card was the very first thing on screen. A notification that arrives
before the reader has decided they want notifications is the one kind that reads as a fault rather
than as a reminder.

The rule lives in `DeliveryPolicy`, which decides and does not deliver:

- `dueQueueSlot(now, config, since)` answers the queue. It refuses a slot that came due before
  `since`, and it refuses the whole queue when it is switched off, when the day is not one the queue
  speaks on, or when the window is closed.
- `isPrayerDue(now, prayerAt, since, graceMinutes)` answers the prayer bells. It requires both the
  grace window (from the prayer's own time until two minutes after it, which is what lets a
  thirty-second poll still catch it) and the baseline.

A slot that comes due at the very moment the scheduler started *is* announced: `since` is the
start, and the scheduler was there for it.

The scheduler's clock is injected, so "what does the scheduler do at this moment?" is a question a
test can ask rather than a question a test has to wait for. `DeliveryPolicySuite` pins the rule
itself and `NotificationRuntimeSuite` pins the scheduler's use of it, including the case this bug
was reported from: the app opens a minute after a prayer time, and nothing is announced.

## What the card offers

The card carries three gestures: the body opens the passage, the cross closes the card, and a star
keeps the passage.

The star is the only one of the three that is not about the app. Copy and next — controls this card
carried once — acted on the clipboard and on the queue, and both had a home the click already
opens; they cost the passage the room it is read in, so they are gone. Keeping a passage has no such
home: a reader who keeps a passage in the library and then meets that same passage in the queue has
to be able to keep it from the queue too, or the passage behaves differently depending on which
surface it happened to turn up on. It is one glyph in the corner, it costs the passage no room, and
it is the only gesture on the card that does not also open the app.

Three things follow from that, and they are the whole of the star's behaviour:

- **It writes through the same door the library writes through.** `NotificationRuntime` already holds
  a `CollectionsGateway`, and the star uses it, so starring from the overlay and starring from the
  library are the same write rather than two that can disagree. The favourites mirror collection is
  updated by that write, exactly as it is from the library.
- **It is not a navigation.** The overlay's actions travel through `NotificationActionRouter`, which
  has two halves: showing the window and moving the reader to a screen. Starring is neither, so it
  has a channel of its own — a listener the library tree installs — and pressing the star never
  brings the app forward.
- **It is optimistic, and it admits it.** The glyph moves on the press, before the database has
  answered, because a round trip between the press and the picture is visible. If the write fails,
  the star goes back. A card that left a claim on screen that nothing behind it agreed with would be
  worse than one that never lit up.

The star is offered only where it can be honest: a prayer time and a bare notice have no passage to
keep, and a card with no way to write the flag draws no star rather than a dead one.

## The rule along the top edge

The card carries a three-pixel gradient rule along its top edge, and it is a clock rather than a
decoration: it empties as the card's on-screen time runs out, so a reader can see how long the
reminder intends to stay instead of having to remember how long it usually stays.

Two rules keep it honest:

- **It only runs one way.** The bar keeps the smallest fraction it has been told. A card that is
  fading off the screen is still asking how much time it has, and the answer can arrive after the
  fact — the presenter has already let the card go, and a reader that has forgotten it answers "all
  of it". Believing that would fill the bar back up on the way out.
- **It slides.** The reading is sampled a few times a second and the bar *chases* it, so the strip
  moves at one speed rather than stepping once per sample. Thirty small jumps a second is a lot of
  jumps for a bar this thin, and they read as a stutter rather than as a clock.

The presenter keeps the fraction a retired card was left at, so the answer it gives while a card is
fading is the truth rather than a fresh full bar.

## Platform behaviour

The overlay is an ordinary top-level window that asks the window manager to keep it above other
windows. It is the same class of window Discord, Steam or a media player uses.

| Platform | Appears over other apps | Above a maximized window | Above a borderless/"fullscreen" window | Above an **exclusive** fullscreen app |
| --- | --- | --- | --- | --- |
| Ubuntu X11 | Yes | Yes | Yes | Yes |
| Ubuntu GNOME (Wayland) | Yes, via XWayland | Yes | Yes | **No** |
| Windows | Yes | Yes | Yes | **No** |
| macOS | Yes | Yes | Yes, while on the same Space | **No** |

The "No" cells are structural, not tunable:

- **Windows exclusive fullscreen** takes the display out of the desktop compositor's hands. A window
  manager has nothing left to place a topmost window in; some games instead drop out of exclusive
  mode and let it appear, which is worse than a notification. Anything that reliably appeared there
  would need an overlay driver, a display-affinity bypass or injection into another process. This
  app does none of that and will not: it is the difference between a reminder and a cheat-flagged
  process.
- **Wayland** gives a client no way to sit above another client's surface. AWT runs under XWayland,
  so the overlay is a normal window as far as GNOME is concerned — above ordinary Wayland windows,
  and absent entirely over a native Wayland fullscreen surface.

This is why the tray has a **Pause reminders** affordance rather than a promise: the honest
mitigation for someone about to play is to pause, not to hope.

## Verifying it

```
./gradlew :app:run                     # then close the window: reminders must keep arriving
azkar --overlay-poc                    # six cards, three seconds apart, no scheduler
./gradlew :app:runScreenshot -Pazkar.screenshot.screen=notifications
./gradlew :app:runScreenshot -Pazkar.screenshot.screen=notifications -Dazkar.screenshot.dark=true
```

`--overlay-poc` starts the app **hidden**, does not start `NotificationRuntime`, and publishes
through the dispatcher rather than into the presenter — so what is on screen is only ever what the
harness put there.

Work down this table once per platform:

1. No window, app started from the tray or with `--overlay-poc` — a card appears.
2. Another app maximized — the card is above it.
3. A browser or a video playing — the card is above it.
4. A borderless fullscreen window — the card is above it.
5. An exclusive fullscreen game — the card does **not** appear; quitting the game or pausing
   reminders is the expected behaviour.
6. While a text field has focus — typing continues, the caret does not jump, the field keeps focus.
7. Hover a card for ten seconds — it stays; move away and it leaves on schedule.
8. Click the body — the window opens at that passage. Click × — the card goes and the next one
   arrives on its own schedule. Click the star — the app stays where it is, the star fills in, and
   the passage is in **Library → Favorites** when the app is next opened.
9. Two monitors, app hidden on the second — the card appears on the second.
10. Arabic (RTL) and dark mode — the card reads correctly; the corners of the window are transparent.
11. Tray → Quit — the overlay window disappears with the app, and nothing fires afterwards.
12. Fresh install, or a start within two minutes of a prayer time — the app opens with **nothing** on
    screen. A slot or a prayer that came due while the app was closed is missed, not replayed.

## What is tested, and what cannot be

`./gradlew :app:test` covers the parts with a right answer: the stack cap and replacement order,
de-duplication, per-card expiry on an injected clock, hover pause and resume, dismissal leaving the
scheduler alone, disposal, and the dispatcher's forwarding (`OverlayPresenterSuite`,
`NotificationDispatcherSuite`, `NotificationCardSuite`). The card's own gestures are asserted
through the semantics tree, which is the only place that can say "the body, the star and the cross,
and nothing else" — that the star keeps the passage it belongs to, that it does not open the app,
that it is offered only where it can be written, and that a failed write takes it back.

The scheduler's own rule is covered by `DeliveryPolicySuite` and `NotificationRuntimeSuite`: what is
announced, what is missed, and the case this documentation's previous section is about — an app
that opens with nothing on screen.

None of it can answer "is it on top". That is rows 2–5 and 9 of the table above, and they stay a
manual check by design.