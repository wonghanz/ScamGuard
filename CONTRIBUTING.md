# Contributing

Small repository, one rule that matters more than the rest: **every weight, brand
domain, suffix and phrase family this app can act on must live in
`link/Tables.kt`.** No number or name list should be duplicated in the analyzer,
because that file is the whole audit surface. If a rule cannot be defended there,
it does not belong in the app.

## What is here

```
app/src/main/java/dev/capriguard/scamguard/
├── link/Tables.kt      weights, bands, 39 brands, suffixes, hops, 12 framing families
├── link/Analyzer.kt    extraction, ownership, encoding, look-alikes, banding, report text
├── link/CheckViewModel.kt  memory-only state, the optional findings-only pass
└── ui/Screens.kt       every screen
```

`core/Core.kt` and `core/Settings.kt` are shared with the other Guard apps and carry
the visual system and the AI client. Changes there should be proposed in the other
repositories too, or not at all.

## The two reports worth filing

1. **A false positive.** An honest message that earned points. Paste the reading from
   *Copy this reading* and name the line in `link/Tables.kt` that fired. These matter
   more than misses: somebody who is told a friend's message looks fraudulent stops
   believing the app, and then stops using it for the case where it would have helped.
2. **A wrong table line.** A domain that is not the brand's, a suffix that is no longer
   cheap, a phrase that belongs to ordinary bank mail rather than to a scam family.
   Name what it should say and why.

## Before you open a pull request

Build it:

```bash
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew --no-daemon :app:assembleDebug
```

`BUILD SUCCESSFUL` and an APK are the bar for a compile. Correctness is not checked by
the build, and there is no instrumented test in this repository and none is planned:
the app's risk is not crashes, it is printing a reason that is not true.

The analysis layer imports nothing but `kotlin.math`, `java.text.Normalizer` and
`java.net.IDN`, so it runs on a plain JVM against the Kotlin stdlib jar your Gradle
cache already holds — no emulator, no device. That is how this code was checked before
it shipped: known scam shapes (a brand inside a stranger's host, a whole brand address
used as a sub-label, one- and two-keystroke look-alikes, a login page on an IP literal,
a `wa.me` handoff, punycode and Cyrillic hosts), ordinary messages from real services as
a false-positive sweep, and Chinese and English wording passes. **No harness is checked
in here.** If you want one in-tree, open an issue first: the argument for it is that a
wrong weight is silent and user-visible, and the argument against is that a test
asserting `8.0` for a look-alike host only ever copies the constant.

## The judgement calls, so they are not relitigated by accident

- **No verdict and no percentage.** "Real or fake", and a confidence number, are claims
  this design cannot support. If a patch turns the band line into a score out of 100,
  it should not be merged.
- **A clean reading prints that it is not a clearance.** Resisting the temptation to
  make zero findings feel like an answer is the core honesty of the app.
- **Never resolve a link.** No fetching, no previewing, no unfollowing of a shortener.
  The request is the harm: it confirms the number is live and reading.
- **No SMS permission, ever.** Reading an inbox to protect it makes the checker the
  thing it guards against, and it is the feature the family refuses.
- **The lists err short.** Adding ten more "official" domains to a brand makes the app
  quieter in a way that looks like safety. A brand list is not a favour to brands.
- **The weight table stays on screen** under every reading. Moving it behind a settings
  page would be the single most damaging "cleanup" this UI could receive.
