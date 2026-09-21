# ScamGuard — check a link or a text message for scam shapes, offline, on Android

**ScamGuard** reads a message you have already received — the SMS, the WhatsApp
text, the email, the comment with a link in it — and prints every reason inside the
text itself that says *look closer*: who actually owns each address in it, whether a
brand's name is being carried along as decoration, and what the wording is doing to
you. All of it happens on the device, from a weight table shipped in the APK.

No blocklist lookup. No account. No telemetry. No API key needed. It never opens a
link it is shown, because opening it is what whoever sent it wanted.

---

## Why this exists

The skill this came from is a link checker backed by "over 2.5 million scam domains"
and "38 global threat-intelligence feeds". That is a real product and it works — and
it cannot be a phone app, for two reasons that are not about engineering effort.

A feed is only as good as the moment it was fetched. The domain in the message in
your hand was registered this morning; the honest answer about a brand-new domain
from any feed is "not seen yet", which is the same answer as "clean". And the lookup
itself is a request: to run the check you have to tell somebody — the feed operator,
and transitively whoever registered that domain — that a number is live and reading.

What a phone can do without asking anything is read the text. An address that borrows
a name it does not own, a suffix sold by the thousand, a login page hanging off an
IP literal, a "dear customer" wrapped around a deadline — all of that is already in
front of you, and every one of those is a thing you can be shown *why* it was noticed.

So ScamGuard answers a narrower question than a threat feed does: **what does this
text say about itself, and can I see the reason?** It has no opinion about whether
the message is a scam, because nothing that runs offline and shows its working can
have one.

## What it actually runs

| Pass | What it does | Network? |
| --- | --- | --- |
| Extract | Finds every link-shaped string: with a scheme, `www.`, or a bare `word.tld` | No |
| Ownership | Reads the host right to left and prints the owner-part, using the public suffix rules | No |
| Encoding | Decodes punycode, transliterates accents, and looks for two scripts inside one name | No |
| Hide | Invisible characters, text before an `@`, and a second address folded into the query | No |
| Brand | 39 brands and 83 published domains: is the name the owner, inside the host, or a keystroke off it | No |
| Shape | Bulk-sold suffixes, generated-looking labels, odd ports, deep subdomains, plain http | No |
| Framing | 12 families of wording, in English and Chinese, counted once per family | No |
| Absence | Prints what was tested and did **not** match, so coverage is visible | No |
| Plain words | Optional: sends the findings, never the message, to your own endpoint | Yes, only if you configure it |

Everything is recomputed as you type. The whole pass is string arithmetic over tables
in [`link/Tables.kt`](app/src/main/java/dev/capriguard/scamguard/link/Tables.kt).

## The weights, published

Points, not probabilities. They add; they do not multiply.

| Wt | What earned it |
| --- | --- |
| 8.0 | A brand's name sits inside a host that is not the brand's |
| 7.0 | A brand's whole address used as one label under somebody else's domain |
| 7.0 | Text before an `@`, so the address you read is not the one reached |
| 6.0 | Two writing systems inside one host |
| 6.0 | Characters that never print, beside or inside a link |
| 6.0 | One or two keystrokes off an address this app knows |
| 6.0 | A dotted number where a name should be |
| 5.0 | A second address folded into the first one's query |
| 4.0 | A brand named in the wording with no link of its own in the message |
| 4.0 | A registered name that reads as generated rather than chosen |
| 3.0 | A chat deep link or a dialled number instead of a page |
| 3.0 | The first framing family whose wording matched |
| 3.0 | An encoded internationalised name |
| 3.0 | A port a login page would not be published on |
| 2.0 | Each further framing family (once per family, not per word) |
| 2.0 | More than one owner in a single message |
| 2.0 | A forwarding service, whose destination this app will not follow |
| 2.0 | Four or more labels before the path starts |
| 2.0 | Composite: a look-alike host *and* a verify-or-pay request |
| 1.5 | A bulk-sold suffix |
| 1.0 | Plain http |
| 0 | A suffix outside the shipped list — a coverage statement, never a suspicion |

**Bands.** Under 5.0 prints *inconclusive*. 12.0 and above prints *several independent
shapes pointing the same way*. The total stops being printed at 40.0, because past that
the extra findings say nothing the first twelve did not.

The brand and suffix lists are curated, not complete, and that is the honest weakness:
an over-long list of "official" domains quietly turns detections into misses, so the
lists err short and say so on screen.

## What "nothing matched" is worth

Weakest possible claim, stated first: **a clean reading is not a clearance**, and the
app prints that sentence under its own answer rather than in a manual.

What is measurable is the other direction. Running the shipped rules over 30 addresses
belonging to real services (banks, couriers, marketplaces, `support.apple.com`,
`t.me`, `wa.me`) as ordinary-looking messages:

- **3 of 30 produced a finding.** Two are `off-platform-link` at 3.0 — a link that jumps
  into a chat app. That is what it is; a Telegram profile is genuinely that, and the band
  it lands in says *inconclusive*, not *scam*.
- The third is a domain one keystroke away from a marketplace's address, at 6.0. A
  near-miss can be an honest business with an unfortunate name, which is why the finding
  prints the edit distance rather than a verdict, and why "check the two side by side"
  is in the sentence.

The misses matter more than the false positives and cannot be measured this way: a
domain registered yesterday, correctly spelled and politely worded, trips nothing here
by design.

## What it will not tell you

- **No percentage, no confidence score, no "this is a scam".** A number out of 100
  implies a model with a known error rate. This is arithmetic over a table, and it is
  shown to you as that.
- **No blocklist answer.** There is no feed behind it and nothing you paste is looked up
  anywhere — which is also why it cannot see a domain it has never been told about.
- **No clearance.** See above.
- **No advice about your money, your account, or who to contact.** It reads text; it does
  not act for you, does not name a helpline, and does not tell you to call anybody.
- **No inbox access.** There is no SMS permission, and there never will be. Reading a
  person's messages to warn them would make this the thing it guards against, so the
  message comes to the app by share sheet or by hand, one at a time.
- **No history.** Nothing you check is written to disk. A forwarded scam text carries
  somebody's number, and the app keeps none of it.

## Bring your own key, and it is genuinely optional

Settings holds a base URL, a model name and a key. They go to this app's private
DataStore and nowhere else; the manifest sets `allowBackup=false`, so they cannot ride
out in a cloud backup, a device transfer or an `adb backup`.

The optional pass sends the **findings** — each label, its weight, and the fragment
that matched, which can include the address itself. It does not send the message body,
because the findings already carry everything the wording said. The prompt forbids
clearing a message, declaring one a scam, producing a number, or naming anyone to call.

Turn the switch off and the app makes zero requests. Nothing else changes.

## Requirements

- Android 8.0 (API 26) or newer
- **No runtime permission is requested.** The manifest declares only `INTERNET`, for the
  optional pass you configure.
- Share target: `text/plain` via `ACTION_SEND`. It deliberately does **not** register as
  a browser for `http(s)` — being asked to open a link is the moment to check it, not a
  reason to visit it.

## Build from source

```bash
git clone https://github.com/wonghanz/ScamGuard.git
cd ScamGuard
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew --no-daemon :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, compileSdk 35. The debug APK is
signed with the standard Android debug key.

## Privacy, stated

- Nothing is read from your device. The text arrives by typing, pasting on purpose, or
  one share — so there is no inbox, gallery, file, camera or account access to grant.
- No analytics, no crash reporter, no telemetry, no ad or attribution SDK.
- No link is resolved. A shortened address stays unresolvable, and the reading says so.
- The form is wiped when the app leaves the foreground, so a half-read message does not
  sit in a recent-apps thumbnail or survive into the next session.
- Screenshots are **not** blocked. A reading is the sort of thing you would want to show
  somebody, and there is nothing sensitive behind it to protect.
- The only outbound code path is the one you configure, and it carries findings.

## FAQ

**How do I check if a link is a scam before clicking it, on Android?**
Paste or share it into ScamGuard. It prints the part of the address that decides who
answers — read right to left, not left to right — and lists what looks borrowed,
generated or hidden. It never opens the link, so nothing you check is announced.

**Can it read my SMS and warn me automatically?**
No, and there is no setting for it. The app declares no SMS permission by design:
scanning an inbox to protect it is the trade a checker app should not get to make.

**Why does it say "inconclusive" instead of safe or dangerous?**
Because both words are claims this app cannot support. Under 5.0 points the answer is
inconclusive by design, and zero findings prints "that is not a clearance" rather than a
green tick.

**Does it use a scam-domain database?**
No. It ships a weight table and a curated list of 83 published brand domains, in one
file, that you can read and argue with. A database would be out of date in the exact
direction that matters — newly registered addresses.

**Why did a link I trust come back with points on it?**
Because the findings are shapes, not identities. A chat-app deep link scores 3.0 whether
it belongs to a courier or a friend, and that is printed as *inconclusive*. The weight
table is on screen under the reading; open it and you can see exactly which line fired.

**Does it work on a message with no link?**
Yes. The 12 framing families score the wording alone, which is where the deadline, the
prize, the "customs fee" and the "share me the OTP" live. The reading then says plainly
that there was no address to test.

**Which languages does the wording check cover?**
English and Chinese. Cue lists are in `link/Tables.kt`. Malay, Thai, Vietnamese,
Indonesian and Tagalog are not checked at all, and the app says so rather than returning
a quiet zero that looks like an all-clear.

**Do I need an account or an API key?**
Neither. Every check works offline with no configuration. A key only unlocks the optional
plain-words pass.

**Can it un-mask or resolve a bit.ly link?**
No. Following a short link is a request to a server chosen by whoever sent it, and that
request tells them the number is live. Type the full address instead, or ask the sender.

**Will it stop me being scammed?**
No. It cannot block anything, cannot see inside an image or a voice note, and cannot know
a domain from yesterday. It makes the shapes in a message easier to read, which is a
different and much smaller claim.

## Repo map

```
app/src/main/java/dev/capriguard/scamguard/
├── MainActivity.kt             two routes, the share intake, and the wipe on backgrounding
├── core/Core.kt                palette, continuous-corner shapes, glass, type ramp
├── core/Settings.kt            DataStore settings and the OpenAI-compatible client
├── link/Tables.kt              every weight, brand, suffix, hop and phrase family — the audit surface
├── link/Analyzer.kt            extraction, ownership, encoding, framing, banding, report text
├── link/CheckViewModel.kt      memory-only state and the optional findings-only pass
└── ui/Screens.kt               every screen
```

## Contributing

Issues and pull requests welcome. Two kinds of report are the most useful here:

1. **A line in `link/Tables.kt` that is wrong** — a brand domain that is not theirs, a
   suffix that is no longer cheap, a phrase family that fires on ordinary bank mail.
   That file is the whole audit surface; nothing should be in it that cannot be pointed at.
2. **A reading you disagree with**, from the Copy button. The report text carries each
   finding's weight and the table line that set it, so a disagreement traces to one rule.

False positives are more damaging here than misses, because a person who is told a
friend's message is suspicious stops believing the app.

## License

MIT — see [LICENSE](LICENSE).
