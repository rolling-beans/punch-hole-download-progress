# PHDP notification progress test app

Standalone module: `tools/download-sim`, Android package `eu.hxreborn.downloadsim`.
No real download is performed.

## Timed test

1. Install the debug APK and allow its notification permission.
2. Open **PHDP → Apps** and enable **PHDP Download Sim** as a monitored package.
3. Set **Initial progress (%)**, for example `65`. The **first**
   notification will already contain 65% — no initial 0% notification is posted.
   Set `0` to keep testing ordinary starts.
4. Set **remaining duration**, for example `30` seconds. This controls
   the time from the initial progress to 100%, not the total hypothetical
   download duration.
5. Set how often the simulator **attempts to post** updated progress notifications:
   `100` ms for 10/s or `1000` ms for 1/s. Android/SystemUI may throttle
   delivery independently.
6. Start **constant speed** or **uneven speed** and switch to the home screen.
   **Stop simulation** or the notification **Stop** action cancels the run.

Both profiles are deterministic and use `SystemClock.elapsedRealtime()` rather
than counting handler callbacks. A run reaches 100% at the chosen elapsed
duration, independently of scheduling jitter (subject to service/OS scheduling
latency). The notification holds 100% for two seconds, then clears.

**Constant:** `progress = start + (100 - start) * elapsed / duration`.

**Uneven:** `progress = start + (100 - start) * uneven(elapsed / duration)`.

The uneven profile has eight equal-duration phases with speed multipliers
`[0.60, 1.75, 0.15, 0, 2.20, 0.35, 1.50, 1.45]`.
The multipliers average exactly 1, including an intentional stall between
37.5% and 50% of the total elapsed time. Note that these percentages refer
to elapsed **time**, not downloaded data.

Notification max progress is 10,000 (0.01% units), and completion cannot be
reported before the specified duration. Tests cover mid-download starts,
remaining-time interpolation, nondecreasing progress and both boundary starts. Some apps and PHDP may
quantize displayed progress to whole percentages. This simulator cannot
force SystemUI to dispatch notification callbacks at the specified cadence.

Legacy repeating and multi-notification tests remain accessible below.

## Dedicated CI

Workflow `.github/workflows/download-sim.yml` runs on pushes to
`test/notification-progress-lab`. It runs JUnit unit tests and produces
`notification-progress-simulator-apk` without compiling the main PHDP app
or requiring signing secrets. The manual `workflow_dispatch` action may
require the workflow to be present on the repository's default branch first.
