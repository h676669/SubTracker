# SubTracker

A small native Android app for tracking subscriptions, with a calendar view and a home-screen widget.

## Features
- **Upcoming** tab: monthly/yearly totals, what's due in the next 30 days, and every subscription sorted by its next charge.
- **Calendar** tab: month grid with coloured dots on charge days; tap a day to see what's charged.
- **Charge dates roll forward automatically.** Enter any date you've been charged; weekly, monthly, quarterly, half-yearly and yearly cycles are supported (month-end dates such as the 31st stay on the last day of short months).
- **Home-screen widget** (Jetpack Glance): monthly total plus the next 4 charges, highlighted in red when ≤ 3 days away. Tap it to open the app.
- Statuses: Active, Trial, Paused, Cancelled (paused/cancelled are excluded from totals, calendar and widget).
- Data is stored locally with Room. Nothing leaves the phone.

## Build & install
1. Unzip and open the `SubTracker` folder in Android Studio (File → Open).
2. Let Gradle sync. If Studio offers to upgrade AGP / Kotlin / dependencies, that's fine to accept.
3. Enable USB debugging on your phone (Settings → About phone → tap *Build number* 7 times, then Developer options → USB debugging), plug it in, and press **Run**.
4. Long-press the home screen → Widgets → **SubTracker** → drag *Upcoming subscriptions* onto the screen.

## Stack
Kotlin 2.0 · Jetpack Compose (Material 3, dynamic colour) · Room · Glance 1.1 · minSdk 26

## Structure
```
data/    Subscription entity, DAO, database, Billing.kt (all date maths)
ui/      MainActivity, Upcoming/Calendar/Edit screens, ViewModel, theme, formatting
widget/  Glance widget + receiver
```

## Notes
- The widget refreshes whenever you open the app or save a change, and otherwise every ~6 hours (Android's minimum-friendly interval), so "in N days" can lag by a few hours around midnight.
