AyuBase6 GitHub Pages package
- index.html at repository/site root
- config/manifest.json and config/sw.js
- icon/ contains app icons
Deploy all files preserving this folder structure.

Note: Service worker scope restrictions in browsers may prevent a worker located in /config/ from controlling pages at the site root unless the host sends Service-Worker-Allowed: /. GitHub Pages does not normally let you configure that response header. If registration fails, root-level sw.js is required for full-site service-worker control.
