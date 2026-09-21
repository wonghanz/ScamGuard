# Security and safety

## The one thing to read first

**A clean reading is not a clearance, and acting as if it were is the harm this app
could cause.** ScamGuard has no blocklist, no threat feed and no network lookups, so a
domain registered this morning — correctly spelled, politely worded, behind a valid
certificate — produces zero findings here and would continue to produce zero findings
however long you stared at it. Those are the scams that work.

The app is a reading aid for the shapes already visible in a piece of text. It is not a
filter, not a decision, and not a substitute for reading the address yourself. If you
want an answer to "can I tap this", no offline tool has one, and the ones that claim to
are doing a database lookup whose honest result for a new domain is "not seen yet".

## Attack surface, stated plainly

- **There is one exported entry point besides the launcher:** `ACTION_SEND` with
  `text/plain`. Any app can hand this one a string to analyse. That is deliberate —
  the message you want checked arrived in another app — and the consequence is that the
  input path has to treat its input as hostile: no code execution, no component
  launched, no URI opened, no link followed, no formatting interpreted. Nothing the
  analyzer does with the text can cause a request.
- **It does not register as a browser.** No `http(s)` `VIEW` intent filter, no
  BROWSABLE category. Being asked to open a link is the moment to check it, not a reason
  to visit it, and claiming `VIEW` would put this app in the path of traffic it should
  only read.
- **No runtime permission is requested**, and specifically no SMS, contacts, storage,
  camera, phone or query-profile permission. There is no `QUERY_ALL_PACKAGES`, no
  `MediaStore` access, no `ExifInterface` path and no WebView, so there is no parser to
  fuzz and no inbox to leak.
- **No persistence of what you check.** No history, no queue, no log, no file. The text
  lives in a ViewModel and is cleared on `ON_STOP`. The only thing DataStore holds is
  the endpoint configuration you type yourself.
- **No telemetry.** No analytics, crash reporting, advertising, attribution or
  error-reporting SDK is linked, in any build type.
- **No bundled credential.** The repository and the APK contain no key, token or
  endpoint other than the placeholder base URL string `https://api.openai.com` in
  `core/Settings.kt`, which is never contacted unless you type a key and turn the switch
  on.

## What leaves the device, and only if you configure it

The optional plain-words pass sends the **findings**: each label, its weight, and the
fragment that matched — which can include the domain or hostname, and can include a
phone number if a number was what matched. It does not send the message body, a device
identifier, or anything from your accounts, because the reading never needed them.

Transport is HTTPS to a host you typed, with your key in an `Authorization` header.
Two consequences worth stating: **the endpoint you configure can see what you were
checking**, and a hostile or compromised endpoint can return prose designed to make a
fraud look ordinary. The UI labels generated text and keeps it visually separate from
the table's own output, so the findings above it remain the app's. If that exposure is
unacceptable, leave the switch off — the whole reading path works without it and gains
nothing from it. Point it at an `ollama` or `llama.cpp` endpoint on your own network and
even the findings stay local.

Clipboard access happens only when you tap *Paste from clipboard*, and Android 10+ shows
its own notice when an app reads it. The app never polls the clipboard.

## Honest residual risks

- **False clearance is the failure mode that matters.** It is silent: nothing prints
  when nothing matches. The mitigations are that a zero-finding reading prints that it
  is not a clearance, that the "checked and did not match" list is always on screen, and
  that the weight table is never hidden.
- **False positives cost trust.** A measured sweep of 30 real-service addresses fired on
  three: two chat-app jumps (which is what they are) at 3.0, and one domain one keystroke
  from a marketplace's address at 6.0. Both are printed as *inconclusive*, not as a
  verdict, which is the design's answer to the case where the app is wrong.
- **Language coverage is a lie by omission if it is not said out loud.** The wording
  families are English and Chinese only. A Thai, Malay, Vietnamese, Indonesian or Tagalog
  scam scores nothing on the wording pass, and the app states that on screen and in the
  README.
- **Screenshots and the recents thumbnail are not blocked.** `FLAG_SECURE` is deliberately
  not set, because a reading is the sort of thing you would want to show somebody. The
  cost is that a shared message — which can contain somebody's number — is visible in a
  task thumbnail until the next background clear.
- **The lists drift.** Brand domains change, suffix prices change, and the framing cues
  are a snapshot of how messages are worded this year; scammers rephrase faster than a
  bundled table updates. Everything is in one file so a reader can check the lines they
  depend on.
- **The heuristic is not a classifier.** It never saw a corpus, has no measured precision
  or recall, and cannot have either while it ships a fixed table. Anyone describing its
  output as an accuracy figure is describing something this app is not.

## Reporting

Open a [private security advisory](https://github.com/wonghanz/ScamGuard/security)
rather than an issue for anything that could send a request without configuration, read
device state beyond an explicit paste, persist a checked message, defeat the
findings-only boundary in the optional pass, or execute on hostile `EXTRA_TEXT`.

A wrong weight, a missing brand domain or a phrase family that fires on ordinary bank
mail is not a vulnerability — it is a bug with one specific file to fix, and a plain
issue is faster and more useful.
