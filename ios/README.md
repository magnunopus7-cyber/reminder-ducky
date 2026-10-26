# Ducky for iPhone

The iPhone version of Reminder Ducky: the same 3D duck (it runs the Android
app's `duck.frag` shader through WebGL), reminders that quack, a to-do list,
the 꾸미기 look picker and English / 한국어.

## What is different from Android

iPhones do not let any app float over other apps, so Ducky lives in a
**Home Screen widget** (small, medium and large) and on the **Lock Screen**
instead. The widget shows Ducky in your chosen look, your next reminders and
your to-dos. When a reminder is due Ducky gets excited and a ✅ button appears
on the widget; at night (11 pm to 7 am) Ducky sleeps. Reminders arrive as
notifications with the quack sound and Done / Snooze 10 min buttons.

## How it is built

There is no Mac here, so GitHub Actions builds it on a Mac
(`.github/workflows/ios.yml`). Each run:

1. makes the Xcode project from `project.yml` with XcodeGen,
2. builds an unsigned `Ducky-unsigned.ipa` (download it from the run's page),
3. runs the app in the iPhone simulator and saves pictures of the app and
   widgets (also pushed to the `ios-previews` branch for non-main builds),
4. on `main`, if the App Store Connect secrets below are set, signs the app and
   sends it to TestFlight.

## Installing on an iPhone

Apple only lets signed apps onto an iPhone. The simplest way without a Mac:

1. Join the Apple Developer Program (99 USD a year) at developer.apple.com.
2. In App Store Connect, create an app named Ducky with bundle ID
   `app.reminderducky` (register the IDs `app.reminderducky`,
   `app.reminderducky.widget` and the App Group `group.app.reminderducky` first
   under Certificates, Identifiers & Profiles).
3. In App Store Connect → Users and Access → Integrations, create an API key
   with the Admin role and download the `.p8` file.
4. In this GitHub repository → Settings → Secrets and variables → Actions, add:
   `APPLE_TEAM_ID`, `ASC_KEY_ID`, `ASC_ISSUER_ID` and `ASC_KEY_P8` (the whole
   text of the `.p8` file).
5. The next build on `main` appears in TestFlight. Install the TestFlight app
   on the iPhone and accept the invite.

### Free: AltStore (what Astra uses)

Every build publishes `Ducky-unsigned.ipa` on the
[Releases page](https://github.com/magnunopus7-cyber/reminder-ducky/releases)
as "Ducky for iPhone 1.0.N".

1. On the PC, install AltServer from altstore.io (on Windows it also asks for
   iTunes and iCloud from Apple's website, not the Microsoft Store versions).
2. Plug the iPhone into the PC with a cable, tap Trust on the phone.
3. Click the AltServer icon (Windows: near the clock) → Install AltStore →
   pick the iPhone → sign in with your Apple ID.
4. On the iPhone: Settings → General → VPN & Device Management → trust your
   Apple ID. If asked, turn on Settings → Privacy & Security → Developer Mode
   and restart.
5. On the iPhone, open the Releases page in Safari and download
   `Ducky-unsigned.ipa` (it goes to Files → Downloads).
6. Open AltStore → My Apps → + → pick the file. Ducky appears on the Home
   Screen. Then add the widget (instructions are inside the app).

Free installs last 7 days. AltStore renews them by itself when the iPhone and
the PC (with AltServer running) are on the same Wi-Fi; or tap Refresh All in
AltStore. AltStore also registers the App Group the widget needs.
