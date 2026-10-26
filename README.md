# Reminder Ducky 🦆

An Android reminder app that lives in a shiny 3D metal rubber duck floating over your other apps, like a chat head.

* Drag the duck anywhere. Let go and it glides to the nearest edge and turns to face the screen.
* Tap the duck to open your reminders and add a new one (once, every day or every week).
* When a reminder is due, the duck wobbles and shows a speech bubble with **Snooze 10 min** and **Done**. You also get a normal notification.
* Drag the duck onto the ✕ at the bottom of the screen to hide it. Reminders still arrive as notifications, and you can bring the duck back from the app.
* Choose a gold, chrome, rose gold or midnight finish, and a small, medium or large size.

The duck is drawn live by a shader (`app/src/main/assets/duck.frag`), so there are no 3D model files. Reminders are stored only on your phone.

## Installing on your phone

1. On your phone, open this repository's **Releases** page (on GitHub: the "Releases" link on the right of the main page, or add `/releases/latest` to the repository address).
2. Tap **ReminderDucky.apk** to download it.
3. Open the downloaded file. Android will ask whether to allow your browser (or Files app) to install apps: tap **Settings**, switch on **Allow from this source**, then go back and tap **Install**. If Play Protect warns that it doesn't recognise the app, tap **More details → Install anyway**. That warning appears for any app that isn't from the Play Store.
4. Open **Reminder Ducky** and tap each button in the yellow "Finish setting up" card. Each one opens an Android settings page: switch it on and come back.
5. Tap **Try it: remind me in 1 minute**, leave the app, and wait for the quack.

To update, download the newest APK from Releases the same way and install it over the old one. Your reminders are kept.

### If the duck disappears after a while

Some phones (especially Samsung, Xiaomi, Huawei, OnePlus) close background apps aggressively. Tap **Stop battery saver from putting the duck to sleep** in the app. On Samsung also check *Settings → Battery → Background usage limits* and make sure Reminder Ducky is not in "Sleeping apps".

## Building it yourself

Every push to `main` is built by GitHub Actions (`.github/workflows/build.yml`) and published as a release. To build locally you need Android Studio or the Android SDK with JDK 17, then run `./gradlew assembleRelease`.

## iPhone

An iPhone version with a Home Screen widget lives in [`ios/`](ios/README.md).
