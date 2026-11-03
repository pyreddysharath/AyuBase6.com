# Google Play Console — answers for the forms

Prepared for when the app goes to Google Play. Check each answer against the app at the time of upload.

## Upload
- Upload the **app bundle** (`AyuBase6-<N>.aab`) from a publish run (Actions → the run → Artifacts → "AyuBase6-play-bundle-<N>").
- **Play App Signing**: accept Google's default. The key you created (`ayubase6-release.jks`) becomes the **upload key**; keep it safe.
- The version code must go up with every upload; each workflow run already gives a higher number.

## App content
### Privacy policy
https://ayubase6.com/privacy.html

### Ads
**Yes, my app contains ads.** (Google AdSense, shown in the app through Google's WebView API for Ads, only after the person agrees to Advertising.)

### App access
**All functionality is available without special access.** (No login.)

### Content rating (IARC questionnaire)
- Category: **Reference, News, or Educational** is not right; choose **"All other app types"**.
- Violence, sexuality, language, controlled substances, gambling: **No** to all.
- Users can interact or share content: **No** (feedback goes only to the developer).
- Shares the user's location: **No**.
- Digital purchases: **No**.
- Expected rating: Everyone / 3+.

### Target audience and content
- Target age group: **18 and over** (health guidance for adults; the Body plans include age groups, but the app is not made for children).
- Appeals to children: **No**.

### Health apps declaration
- Health features: **Physical activity and fitness**, **Nutrition and weight management**, **Mental health and wellness (general)**, **Habit tracking / reminders**, **Health measurements entered by the user** (vitals).
- Not a medical device; no diagnosis or treatment. The app says its content is general guidance, not medical advice.
- Health Connect: **not used**.

### Data safety
- Does the app collect or share user data? **Yes** — because of ads (the Google Mobile Ads SDK).
  - **Device or other IDs** (advertising ID): collected and shared, for **Advertising or marketing**; not processed ephemerally; collection is required for ads (people can refuse Advertising in the app's cookie settings).
  - **App activity → App interactions** and **App info and performance → Diagnostics**: collected by the ads SDK for ads and analytics of ads.
  - **Approximate location**: may be derived from the IP address by the ads SDK, for advertising.
- Everything the person enters (health readings, routines, reminders, choices): **stays on the device; not collected**.
- Feedback form: **name and message** are sent to the developer when the person submits feedback — **Personal info → Name**, **Messages → Other in-app messages**: collected, not shared, optional, for **App functionality / Developer communications**.
- Data encrypted in transit: **Yes** (HTTPS).
- People can ask for data deletion: **Yes** — on-device data is deleted by clearing the app's data or uninstalling; for feedback, through the Feedback form.
- Check Google's current "Mobile Ads SDK data disclosure" page before submitting; it lists exactly what the ads SDK collects.

### Exact alarms (`SCHEDULE_EXACT_ALARM`)
Reason, if asked: *"AyuBase6 is a reminder app. People set reminders for exact times (their own reminders, routine activities, water, eye breaks). Exact alarms are needed so each reminder arrives at the minute the person chose. The permission is requested in the app with an explanation, and the app still works without it (reminders may then be a few minutes late)."*

### Notifications (`POST_NOTIFICATIONS`)
Used only for the reminders the person turns on.

### Foreground services, background location, SMS, contacts, files
**Not used.**

## Testing (new personal developer accounts)
- A **closed test** must run before production: currently at least **12 testers**, opted in for **14 days in a row** (check the current rule in Play Console).
- Create a closed testing track, add testers by email list or a Google Group, share the opt-in link, upload the bundle.
- After 14 days, apply for production access in Play Console (Dashboard → "Apply for production").

## After going live
- The website's Get the app can point to the Play Store, and the in-app update card can open the Play page (small change; ask Claude).
