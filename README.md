# SubTracker

A small native Android app for tracking subscriptions, with a calendar view and a home-screen widget.

## Features
- **Upcoming** tab: monthly/yearly totals, what's due in the next 30 days, and every subscription sorted by its next charge.
- **Calendar** tab: month grid with coloured dots on charge days; tap a day to see what's charged.
- **Charge dates roll forward automatically.** Enter any date you've been charged; weekly, monthly, quarterly, half-yearly and yearly cycles are supported (month-end dates such as the 31st stay on the last day of short months).
- **Home-screen widget** (Jetpack Glance): monthly total plus the next charges (6 by default, 1–7 in Settings), highlighted in red when ≤ 3 days away. Tap it to open the app.
- **Multi-currency.** Price a subscription in USD, EUR, GBP, SEK, DKK, CHF, PLN, JPY, CAD or AUD; totals convert to NOK using Norges Bank's open rates API (fetched once a day, cached on disk).
- Statuses: Active, Trial, Paused, Cancelled (paused/cancelled are excluded from totals, calendar and widget).
- **Optional Google Drive sync.** Local-only by default. Connect a Google account and the dataset is backed up to that account's hidden `appDataFolder`, so a new phone restores in one tap. See *Cloud sync* below.
- Subscriptions are stored locally with Room. With sync off, the only network call is the daily exchange-rate fetch and no subscription data leaves the phone.

## Build & install
1. Unzip and open the `SubTracker` folder in Android Studio (File → Open).
2. Let Gradle sync. If Studio offers to upgrade AGP / Kotlin / dependencies, that's fine to accept.
3. Enable USB debugging on your phone (Settings → About phone → tap *Build number* 7 times, then Developer options → USB debugging), plug it in, and press **Run**.
4. Long-press the home screen → Widgets → **SubTracker** → drag *Upcoming subscriptions* onto the screen.

## Cloud sync

Off by default; the app is fully usable without ever touching this.

**How it works.** The whole dataset (subscriptions, custom tags, theme and widget settings)
is one JSON document in the `appDataFolder` of your own Google Drive: a per-app hidden
folder that doesn't show up in your Drive listing and that no other app can read. The only
scope requested is `drive.appdata`, which cannot see your real files.

Sync is **whole-blob last-write-wins**, and deliberately asymmetric:

- **Uploads happen on their own**, 3 seconds after a change (bursts coalesce into one push).
- **Restores are always manual**, because a restore replaces this phone's data.
- If Drive has changed since this phone last synced, a push **stops and asks** which copy to
  keep instead of silently overwriting.

**Setup (one-off, needed before Connect works).** The app has no API key in it — Google
matches on your package name plus signing certificate, so you need your own OAuth client:

1. [Google Cloud Console](https://console.cloud.google.com) → create or pick a project.
2. **APIs & Services → Library** → enable **Google Drive API**.
3. **OAuth consent screen** → External → add the scope
   `https://www.googleapis.com/auth/drive.appdata` → add your own Google account under
   **Test users**.
4. **Credentials → Create credentials → OAuth client ID → Android**:
   - Package name: `com.subtracker`
   - SHA-1: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`
5. Nothing to paste into the project. Build, run, **Settings → Account → Connect Google Drive**.

Leave the consent screen in **Testing** status. `drive.appdata` counts as a sensitive scope,
so a *published* app would need Google's verification review; in Testing, with yourself as a
test user, it doesn't. A release build is signed with a different certificate, so it needs a
second OAuth client with the release SHA-1.

**Not encrypted.** The document is plain JSON. `appDataFolder` keeps it away from other apps
and from your Drive UI, but Google can read it. Client-side encryption would fix that at the
cost of a passphrase that, if forgotten, destroys the backup — a poor trade for something
whose whole job is rescuing data, so it isn't done.

## Stack
Kotlin 2.2 · Jetpack Compose (Material 3, dynamic colour) · Room · Glance 1.1 · AGP 9.4 · minSdk 26

## Structure
```
data/    Subscription entity, DAO, database, Billing.kt (all date maths), Rates.kt (currency)
sync/    Backup.kt (the JSON document), DriveStore.kt (Drive REST), Sync.kt (push/pull rules)
ui/      MainActivity, Upcoming/Calendar/Edit/Categories screens, ViewModel, theme, formatting
widget/  Glance widget + receiver
```

## Notes
- The widget refreshes whenever you open the app or save a change, and otherwise every ~6 hours (Android's minimum-friendly interval), so "in N days" can lag by a few hours around midnight. Its ↻ button forces a refresh.
- Drive access tokens last about an hour and no refresh token is stored on the device, so each sync re-authorises. Once you've granted the scope that happens without any prompt.
