# Google Play listing — English

Current as of 1.4.0 (draft 04.10.2026: everything from 1.2.3–1.3.4).

## Title (30 chars)
Ogneliss Dialer

## Short description (80 chars)
Calls and SMS, no internet: T9 search, notes for numbers, spam protection

## Full description (up to 4000)

Ogneliss Dialer is a phone app that works entirely on your device. It has no internet permission — you can check this yourself in the installed app's details.

DIALING AND SEARCH
• T9 search in Cyrillic and Latin at the same time
• Search by first name, last name and digits of the number
• Speed dial on keys 2–9
• Pause and wait for bank voice menus

CALL LOG
• Grouped by date: today, yesterday, this week
• Pinned contacts always on top — with calls from any of their numbers
• "Awaiting reply" filter: missed calls you haven't returned
• Filters by type, SIM card, period, notes and reminders
• Missed call counter on the app icon
• Clearing and deleting ask for confirmation
• Export the call log as a CSV table

MESSAGES
• SMS right in the dialer — or make it your default SMS app: conversations, incoming SMS and MMS, replies from the notification
• SMS in the call log, one timeline with calls, with a Calls / Messages filter
• Codes from SMS with one “Copy” tap, recognised on the phone

SWIPES
• Up to three actions per swipe — the row slides and reveals buttons, like in email apps
• 14 actions to choose from: call, SMS, messenger, reminder, note, block…
• Protection against accidental swipes: normal scrolling doesn't move rows sideways

CONTACT CARD
• "Message": SMS or a messenger — Telegram, WhatsApp, Viber, Max, if the contact uses it
• Email: write or copy the address
• Call summary, contact QR code, call history export
• Custom ringtone, send calls to voicemail, home screen shortcut
• Delete a contact with confirmation

CARRIER AND REGION
• Unknown numbers show the carrier and region they were issued in
• Works from the official numbering plan, offline (Russian numbers for now)

NOTES AND REMINDERS
• A note for any number, even if it's not in your contacts
• The note shows while dialing and in the after-call notification
• Call-back reminders: today, tomorrow or any day

SPAM PROTECTION
• Whitelist takes priority over the blacklist
• Mask rules: 8 800 * covers all toll-free lines
• Number labels: spam, scam
• A quiet notification with "Unblock" for every blocked call
• Warning about premium-rate and international numbers before dialing
• Emergency numbers — no needless warnings; after an emergency call the filter lets all incoming calls through for two hours, so a call-back is never blocked

FAKE CALL
Your phone "rings" — an excuse to step away from an awkward conversation. No real call happens: nothing is sent anywhere and nothing appears in the call log.

DUAL SIM
• A separate call button for each SIM
• Remembers which SIM you use for each person
• Your own names for slots: "Work", "Personal"

CALL SCREEN
• Quick SMS reply to an incoming call
• Audio to a Bluetooth headset
• Screen-off near the ear can be disabled

COMFORT
• Light, dark and AMOLED black themes
• Large buttons for low vision; button labels can be hidden
• Landscape, tablets and foldables: call log and contact card side by side
• 48 interface languages

WHAT THE APP DOESN'T HAVE
• Ads
• Subscriptions or paid features
• Internet access
• Call recording — since Android 10 the system blocks it for third-party apps

ABOUT YOUR DATA
The call log and contacts are read from Android's system providers and stay on your device. The app keeps no copy of its own, so if you switch back to the stock Phone app you lose nothing.

OPEN SOURCE
The source code is open under the Apache License 2.0, so everything said about your data can be checked against the code: github.com/oedante/OgnelissDialer. You may use the app for any purpose, including work and business.

## Category
Tools → Communication

## Tags
dialer, phone, calls, T9, contacts, call blocker, notes, reminders,
offline

## Developer contact
**oedante@gmail.com**

Google Play displays this address publicly on the app page — it is
a store requirement and cannot be hidden.


## Links for Google Play Console

| Console field | Value |
|---|---|
| Privacy policy | `https://ogneliss.ru/dialer/privacy` |
| Source code (in the description) | `https://github.com/oedante/OgnelissDialer` |
| Website | `https://ogneliss.ru/dialer/` |
| Email | `oedante@gmail.com` |

⚠️ The privacy policy URL cannot change after release: it is printed
in the documents inside installed builds. For the same reason the
Apache License 2.0 (since 1.5.7) lives at the old address `/dialer/eula`.

## Important on some phones

On Xiaomi, Samsung, Huawei, Honor, Oppo and nubia the manufacturer
shell restricts third-party apps by default. For the dialer to work
fully:

1. **Allow modifying system settings** — otherwise the phone will not
   let you set the app as the default dialer.
   Settings → Security → Special app access.

2. **Allow auto-start** — otherwise missed calls will not appear
   in the log until you open the app yourself.

3. **Turn off background restrictions** for the app.

The app offers these steps on first launch.
