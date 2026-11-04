AyuBase6 GitHub Pages package

FOLDER STRUCTURE
  index.html          The app (one file), without web push: reminders only while it is open. Must sit at the site root.
  index-with-Cloudflare.html  The same app with web push (closed-app reminders through the push server).
                      Not served as the site; the Android app (AyuBase6-Android) is built from it, with push switched off.
  sw.js               Service worker (offline use, notification taps, saved videos). Must sit at the site root.
  CNAME, ads.txt      Domain and ads settings.
  lib/                PDF reader (pdf.js), PDF maker (jsPDF) and the OCR tool (tesseract.js + English data), served from
                      this site so they work offline and do not depend on public CDNs. See lib/README.txt.
  privacy.html        Privacy policy (website and Android app).
  config/             manifest.json (install details), OurOtherSites.json (footer list), feedback_apps_script.gs,
                     push.json (address of the push server; empty = reminders only work while the app is open).
  icons/, favicons/   App icons and favicons.
  content/            Videos, preview images and the JSON lists that name them (see below).

Deploy all files preserving this folder structure. index.html and sw.js must be uploaded together.

CONTENT FOLDER
  content/
    body/
      yoga/
        yoga.json         List of yoga videos.
        media/            Video (.mp4) and preview image (.jpg) for each pose.
      exercise/
        exercise.json     List of exercise videos (empty until videos are added).
        media/            Video and preview image for each exercise.
    reminders/
      reminders.json      Eye exercise video for the Reminders tab.
      media/

Each section has one JSON file next to its media/ folder. A JSON entry looks like this:

  { "name": "Tree Pose", "type": "video", "file": "tree-pose.mp4", "poster": "tree-pose.jpg",
    "bytes": 160141, "posterBytes": 5000 }

  name         Must match the name shown in the app (yoga pose name or exercise name). Names are case-sensitive.
  file         Video file name inside that section's media/ folder.
  poster       Preview image shown before the video loads (optional, but recommended).
  bytes        Size of the video file in bytes. Used for the download size shown on the Save for offline button.
  posterBytes  Size of the preview image in bytes (same use).

ADDING OR REPLACING A VIDEO
  1. Put the video and preview image in the section's media/ folder.
  2. Add or update its entry in the section's JSON file.
  3. Exercise videos: the "Save for offline" button appears under Exercise as soon as exercise.json lists a video.
  Replacing a video: keep the same name and the app tells people to save again (size or last-modified changed),
  or give the new file a new name in the JSON.
  GitHub's web upload skips empty folders, so create content/body/exercise/media/ when adding the first exercise video.

OFFLINE VIDEOS
  Videos load only when they are played. The Save for offline button (under the Yoga and Exercise headings in the
  Body tab) stores them in the browser's own storage for this site, so they play without internet. The app shows a
  message to save again when a saved file is missing, renamed, removed, a different size, or newer on the site.
  Saved files are not visible in a folder; clearing the site's data removes them.

NOTES
  - The service worker must be at the site root (sw.js) to control the whole site.
  - Files opened directly from a computer (file://) will not load the content lists; use GitHub Pages or a local server.

REMINDERS WHEN THE APP IS CLOSED (WEB PUSH)
-------------------------------------------
Currently OFF: the site serves index.html (no push) and the push server's timer is paused. The Android app sends
reminders from the phone itself. To turn web push back on: rename index-with-Cloudflare.html to index.html (keep the
other as index-without-Cloudflare.html, and point AyuBase6-Android's scripts/prepare-www.mjs at the push build),
and restore the cron in the push server's wrangler.toml.
Setup: Deploy the separate AyuBase6-push-worker package on Cloudflare (steps in its SETUP.txt), then put its
address in config/push.json, e.g. { "api": "https://ayubase6-push.yourname.workers.dev" }.
The app then sends each phone's reminder schedule to that server, which pushes the reminders on time.
While the app is on screen it shows reminders itself, so you never get doubles. With "api" empty nothing is sent anywhere.
iPhone needs the app added to the Home Screen first.
