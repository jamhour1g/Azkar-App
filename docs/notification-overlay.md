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
NotificationDispatcher       channel policy: what the overlay does, and whether the OS does too
   ┌────┴─────┐
   ▼          ▼
Overlay…     SystemNotificationPresenter → ReminderNotifier (nucleus, opt-in)
presenter
   │
   ▼  StateFlow<List<PresentedNotice>>
NotificationOverlayWindow     one undecorated, transparent, always-on-top, unfocusable window
   ▼
NotificationCard             AzkarCard — the app's own card, themed
```

The point of the seams is testability. `NotificationRuntime` decides *when* a passage is due and
never learns whether the result is a Compose card, a native toast, or neither. `NotificationPresenter`
decides *what is on screen, for how long, and what happens on arrival* and has no window in it at
all, so every rule below is unit-tested in milliseconds with an injected clock. Only
`NotificationOverlayWindow` and `NotificationCard` touch AWT.

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
  and a refusal also suppresses the native channel — so a short interval cannot produce three
  identical cards, nor one card and one toast for the same passage.
- **Expiry.** Each card runs its own countdown from acceptance and expires on its own.
- **Pause.** Hovering freezes the countdown. Hover is the only signal available because the window is
  unfocusable by design.
- **Dismissal.** Closing a card has no effect on the scheduler: the queue's progress is committed
  before the card exists, so a dismissed card was already delivered.
- **Quitting.** Tray → Quit cancels the scheduler's scope, disposes both channels and the overlay
  window, closes the database and the location client, stops Koin, and exits.

Settings live in **Reminders → Notification appearance**: native notifications on/off, duration,
placement and stack size. They are separate from the queue's own settings on purpose — turning a
reminder off is not the same decision as changing how a reminder looks.

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
harness put there, and the native channel behaves exactly as it normally would.

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
   arrives on its own schedule.
9. Two monitors, app hidden on the second — the card appears on the second.
10. Arabic (RTL) and dark mode — the card reads correctly; the corners of the window are transparent.
11. Tray → Quit — the overlay window disappears with the app, and nothing fires afterwards.

## What is tested, and what cannot be

`./gradlew :app:test` covers the parts with a right answer: the stack cap and replacement order,
de-duplication, per-card expiry on an injected clock, hover pause and resume, dismissal leaving the
scheduler alone, disposal, and the dispatcher's channel policy (`OverlayPresenterSuite`,
`NotificationDispatcherSuite`, `NotificationCardSuite`).

None of it can answer "is it on top". That is rows 2–5 and 9 of the table above, and they stay a
manual check by design.