package dev.capriguard.scamguard.link

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max

/* ------------------------------------------------------------------ model */

/**
 * One line of the reading. `on` names the table line that set the weight, so any
 * finding can be argued with by pointing at `Weight` rather than at a mood.
 */
data class Finding(
    val key: String,
    val label: String,
    val detail: String,
    val evidence: String,
    val weight: Double,
    val on: String,
    val linkIndex: Int? = null,
) {
    /** A coverage statement, not a suspicion. It never moves the total. */
    val informational: Boolean get() = weight <= 0.0
}

data class LinkScan(
    val raw: String,
    val scheme: String,
    val hostAsTyped: String,
    val hostUnicode: String,
    val owner: String,
    val suffixKnown: Boolean,
    val ownerLabel: String,
    val port: String?,
    val path: String,
    val findings: List<Finding>,
)

data class Reading(
    val links: List<LinkScan>,
    val message: List<Finding>,
    val absent: List<String>,
    val score: Double,
    val band: String,
    val note: String,
) {
    val all: List<Finding> get() = links.flatMap { it.findings } + message
    val scored: List<Finding> get() = all.filter { !it.informational }
}

/* --------------------------------------------------------------- matching */

private const val NOT_WEIGHTED = "not weighted — a coverage statement"

private val SCHEME_LINK = Regex(
    """(?i)\b(?:https?|ftps?|ws|wss|mailto|tel|sms|smsto|intent|whatsapp|viber|tg|weixin|alipays|paytm|upi|line|skype|market|geo):[^\s<>"'\[\]{}|\\^`]{2,300}""",
)

private val WWW_LINK = Regex(
    """(?i)\b(?:www\.)[a-z0-9\u00C0-\u024F\u0400-\u04FF\-]+(?:\.[a-z0-9\u00C0-\u024F\u0400-\u04FF\-]+)+(?:[:/][^\s<>"'\[\]{}|\\^`()]*)?""",
)

/**
 * A bare `word.tld` with no scheme is the shape most messages actually use.
 * Matching every `x.y` would flag "e.g." and "photo.jpg", so a bare candidate has
 * to end in a suffix this list recognises. That is a coverage trade, stated here
 * rather than hidden: an unusual gTLD written without a scheme can slip past.
 */
private val BARE_OK = setOf(
    "com", "net", "org", "gov", "edu", "info", "biz", "co", "me", "io", "app", "dev",
    "xyz", "top", "icu", "cyou", "monster", "click", "buzz", "quest", "sbs", "shop",
    "store", "online", "site", "space", "website", "vip", "asia", "live", "link",
    "tk", "ml", "ga", "cf", "gq", "zip", "mov", "work", "fit", "beauty", "loan",
    "my", "sg", "id", "ph", "tw", "th", "vn", "jp", "kr", "cn", "in", "uk", "au",
    "nz", "us", "ca", "de", "fr", "nl", "ru", "br", "mx", "za", "ae", "sa", "tr",
)

private val BARE_DOMAIN = Regex(
    """(?i)\b[a-z0-9\u00C0-\u024F\u0400-\u04FF\-]+(?:\.[a-z0-9\u00C0-\u024F\u0400-\u04FF\-]+)*\.[a-z]{2,24}(?:/[^\s<>"'\[\]{}|\\^`()]*)?""",
)

/** Never prints, so what you read and what the parser reaches are different. */
private val INVISIBLE: Set<Char> = setOf(
    '\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF', '\u00AD',
    '\u202A', '\u202B', '\u202C', '\u202D', '\u202E', '\u2066', '\u2067', '\u2068', '\u2069',
)

/** Cyrillic, Greek and Georgian letters that stand in for Latin ones. */
private val LOOKALIKE_MAP: Map<Char, Char> = mapOf(
    'а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'у' to 'y',
    'х' to 'x', 'ѕ' to 's', 'і' to 'i', 'ј' to 'j', 'ԁ' to 'd', 'ѡ' to 'w',
    'օ' to 'o', 'ց' to 'c', 'թ' to 't', 'ѵ' to 'y', 'ⲟ' to 'o',
)

private val VOWELS = setOf('a', 'e', 'i', 'o', 'u', 'y')

/**
 * Letter pairs a reader's eye resolves into one letter. Kept short on purpose:
 * a collapse only ever matters when the result is exactly a brand's registered
 * label, so an entry here cannot fire on its own.
 */
private val VISUAL_COLLAPSE = listOf("rn" to "m", "vv" to "w", "nn" to "u", "mm" to "m", "cl" to "d")

private fun scrubInvisible(s: String): String = buildString(s.length) {
    for (c in s) if (!INVISIBLE.contains(c)) append(c)
}

/** Accent-stripped, look-alike-mapped, lower-case: the form names are matched in. */
private fun translit(s: String): String {
    val decomposed = Normalizer.normalize(s, Normalizer.Form.NFD)
        .filter { it.category != CharCategory.NON_SPACING_MARK }
    return buildString(decomposed.length) {
        for (c in decomposed.lowercase()) append(LOOKALIKE_MAP[c] ?: c)
    }
}

private fun flatten(s: String): String = translit(s).filter { it.isLetterOrDigit() }

private fun punycodeToUnicode(host: String): String = runCatching {
    java.net.IDN.toUnicode(host)
}.getOrDefault(host)

private fun labelsOf(host: String): List<String> = host.split(".").filter { it.isNotEmpty() }

private fun levenshtein(a: String, b: String, cap: Int = 3): Int {
    if (a == b) return 0
    if (abs(a.length - b.length) > cap) return cap + 1
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1)
        cur[0] = i
        var rowMin = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            rowMin = minOf(rowMin, cur[j])
        }
        if (rowMin > cap) return cap + 1
        prev = cur
    }
    return prev[b.length]
}

/**
 * The part of a host that decides who controls it, and whether the suffix was
 * recognised at all. Where it was not, `suffixKnown` is false and the app stops
 * sounding certain about ownership.
 */
fun ownerOf(host: String): Pair<String, Boolean> {
    val labels = labelsOf(host)
    if (labels.size < 2) return host to false
    val last = labels.last()
    val second = labels[labels.size - 2]
    val two = "$second.$last"
    return when {
        Suffixes.twoPart.contains(two) && labels.size >= 3 -> labels.takeLast(3).joinToString(".") to true
        Suffixes.twoPart.contains(two) -> two to true
        Suffixes.knownSingle.contains(last) || BARE_OK.contains(last) -> two to true
        else -> host to false
    }
}

/** The registered name inside an owner-part: what a look-alike has to resemble. */
fun registeredLabelOf(owner: String): String {
    val labels = labelsOf(owner)
    val two = if (labels.size >= 2) "${labels[labels.size - 2]}.${labels.last()}" else ""
    return if (Suffixes.twoPart.contains(two) && labels.size >= 3) labels[labels.size - 3]
    else if (labels.size >= 2) labels[labels.size - 2]
    else labels.firstOrNull() ?: owner
}

/* ------------------------------------------------------------- the scanner */

object Analyzer {

    /** Every link-shaped thing in the text, left to right, de-duplicated. */
    private fun candidates(text: String): List<String> {
        val clean = scrubInvisible(text)
        val found = LinkedHashSet<String>()
        SCHEME_LINK.findAll(clean).forEach { found.add(it.value) }
        WWW_LINK.findAll(clean).forEach { found.add(it.value) }
        BARE_DOMAIN.findAll(clean).forEach { m ->
            val v = m.value
            val host = v.substringBefore("/").substringBefore(":").substringBefore("?")
            val last = host.substringAfterLast(".").lowercase()
            if (BARE_OK.contains(last) || Suffixes.twoPart.any { host.endsWith(".$it") }) found.add(v)
        }
        // The same address can be caught twice — once with its scheme, once as the
        // bare host inside somebody else's query. Keep the longer form.
        val list = found.filter { it.length >= 4 }.sortedByDescending { it.length }
        val kept = ArrayList<String>()
        for (cand in list) if (kept.none { it.contains(cand) }) kept.add(cand)
        return kept.sortedBy { clean.indexOf(it) }
    }

    fun analyze(text: String): Reading {
        val scans = candidates(text).mapIndexed { i, raw -> scanLink(raw, i) }
        val perLink = scans.flatMap { it.findings }
        val (msgFindings, absentFamilies) = messageFindings(text, scans, perLink)
        val scored = (perLink + msgFindings).filter { !it.informational }
        val score = scored.sumOf { it.weight }.coerceAtMost(Bands.MAX_REPORTED)

        return Reading(
            links = scans,
            message = msgFindings,
            absent = absentFamilies + absentChecks(perLink, scans),
            score = score,
            band = bandWords(score, scored.size),
            note = when {
                text.isBlank() -> "Nothing was typed."
                scans.isEmpty() -> "No link-shaped text was found, so this is a reading of the wording alone."
                else -> "${scans.size} link${if (scans.size == 1) "" else "s"} read. None of them was opened."
            },
        )
    }

    /* --------------------------------------------------------- one link */

    private fun schemeOf(raw: String): String {
        if (raw.contains("://")) return raw.substringBefore("://").lowercase()
        val head = raw.substringBefore(":", "")
        return if (head.length in 2..12 && head.all { it.isLetter() }) head.lowercase() else ""
    }

    private fun scanLink(rawIn: String, index: Int): LinkScan {
        val raw = rawIn.trim().trimEnd('.', ',', ';', ':', ')', ']', '}')
        val scheme = schemeOf(raw)
        val rest = when {
            raw.contains("://") -> raw.substringAfter("://")
            scheme.isNotEmpty() -> raw.substringAfter(":")
            else -> raw
        }

        val authority = rest.substringBefore("/").substringBefore("?").substringBefore("#")
        val atTrick = authority.contains("@") && scheme != "mailto"
        val hostRaw = (if (atTrick) authority.substringAfterLast("@") else authority).substringBefore(":")
        val port = authority.substringAfter(":", "").takeIf { it.isNotEmpty() && it.all { c -> c.isDigit() } && it != "80" && it != "443" }
        val hostUnicode = punycodeToUnicode(hostRaw).lowercase()
        val (owner, suffixKnown) = ownerOf(hostUnicode)
        val ownerLabel = translit(registeredLabelOf(owner))
        val flat = flatten(hostUnicode)
        val labels = labelsOf(hostUnicode).map { translit(it) }
        val pathAndQuery = rest.substringAfter("/", "")

        val fs = ArrayList<Finding>()
        fun add(key: String, label: String, detail: String, evidence: String, weight: Double, on: String) =
            fs.add(Finding(key, label, detail, evidence, weight, on, index))

        val chatScheme = scheme in setOf("whatsapp", "viber", "tg", "skype", "line")
        val phoneScheme = scheme in setOf("tel", "sms", "smsto")

        if (phoneScheme) {
            add(
                "phone-link", "This is not a website, it is a dialled number",
                "The address is a phone number that whoever wrote the message chose. Companies you deal with publish one; a message that arrives with a private number attached to it is the other thing.",
                hostRaw.take(40), Weight.OFF_PLATFORM, "Weight.OFF_PLATFORM",
            )
        } else if (chatScheme) {
            add(
                "off-platform-link", "This opens a chat app rather than a page",
                "Nothing said after the jump can be seen, moderated or recovered from where you were. A request that starts by moving you to a private chat is the shape of a conversation with no record.",
                scheme, Weight.OFF_PLATFORM, "Weight.OFF_PLATFORM",
            )
        }

        if (hostUnicode.isNotEmpty() && hostUnicode.all { c -> c.isDigit() || c == '.' } && hostUnicode.count { it == '.' } == 3) {
            add(
                "ip-host", "A dotted number where a name should be",
                "Services that expect to be trusted buy names. A raw address is what a drop server, a camera panel or a page built to be blocked looks like.",
                hostUnicode, Weight.IP_LITERAL, "Weight.IP_LITERAL",
            )
        }

        if (labels.any { it.startsWith("xn--") }) {
            add(
                "punycode", "An internationalised name, shown in its encoded form",
                "Punycode lets a domain be spelled in another script, including letters that imitate Latin ones. Legal, common, and also the mechanism behind most look-alike addresses. Decoded here as: $hostUnicode",
                hostRaw.take(60), Weight.PUNYCODE, "Weight.PUNYCODE",
            )
        }

        val latin = hostUnicode.count { it in 'a'..'z' || it in '\u00C0'..'\u024F' }
        val foreign = hostUnicode.count {
            it in '\u0400'..'\u04FF' || it in '\u0370'..'\u03FF' || it in '\u0600'..'\u06FF' ||
                it in '\u05D0'..'\u05FF' || (it in '\u4E00'..'\u9FFF' && latin > 0)
        }
        if (latin > 0 && foreign > 0) {
            add(
                "mixed-script", "Two writing systems inside one address",
                "A Cyrillic о next to a Latin o renders as one word and resolves as another. Mixing scripts inside a single host is how that is done; a fully non-Latin name is normal and is not what this finding is about.",
                hostUnicode.take(60), Weight.HOMOGLYPH_HOST, "Weight.HOMOGLYPH_HOST",
            )
        }

        if (atTrick) {
            add(
                "at-authority", "The address you can see is not the address you reach",
                "Everything before the @ belongs to the user-info field, which nothing displays as a warning. The host actually contacted is what follows it: $hostUnicode",
                authority.take(70), Weight.CREDENTIAL_TRICK, "Weight.CREDENTIAL_TRICK",
            )
        }

        val nested = Regex("""(?i)[?&](?:url|uri|to|next|target|dest|destination|redirect|redirect_uri|redirect_url|return|returnto|continue|go)=[^&]{4,140}""")
            .find(pathAndQuery)
        if (nested != null) {
            add(
                "nested-url", "A second address is folded into the first",
                "This page is built to carry you on somewhere else. That is a legitimate sign-in pattern, and it is also the standard way to hide where a link in a message really ends up.",
                nested.value.take(80), Weight.NESTED_URL, "Weight.NESTED_URL",
            )
        }

        /* Every brand finding below is a comparison against Tables.kt. The list of
           official domains is curated, not exhaustive, so the strongest finding is
           reserved for the case where the brand name is *inside* a host that is
           plainly somebody else's, and an exact brand label on an unfamiliar suffix
           is treated as the weaker thing it is. */
        var sawLookalike = false
        var sawTypo = false
        var sawUnfamiliarSuffix = false
        for (b in Brands.all) {
            val primary = translit(b.cues.first().replace(" ", "").replace("'", "").replace("&", ""))
            if (primary.length < 3) continue
            if (!flat.contains(primary) && !labels.contains(primary)) continue
            val owned = b.domains.any { hostUnicode == it || hostUnicode.endsWith(".$it") }
            if (owned) break

            if (ownerLabel == primary) {
                sawUnfamiliarSuffix = true
                add(
                    "brand-suffix", "${b.display} is the registered name, on a suffix this app does not list for them",
                    "The owner-part here is «$owner». ${b.display} is listed in this build at ${b.domains.take(3).joinToString(", ")}${if (b.domains.size > 3) " and others" else ""}. Companies do register new suffixes, so this is a question to ask rather than a finding to act on.",
                    owner, Weight.BRAND_OFF_DOMAIN, "Weight.BRAND_OFF_DOMAIN",
                )
            } else if (primary.length >= 5 && levenshtein(ownerLabel, primary, 2) in 1..2) {
                sawTypo = true
                add(
                    "brand-typo", "Within one or two keystrokes of ${b.display}'s address",
                    "The owner-part is «$owner», which ${b.display} does not use, and it differs from «$primary» by ${levenshtein(ownerLabel, primary, 2)} edit(s). Put the two side by side before believing either reading — this is the single easiest check to do yourself.",
                    owner, Weight.TYPO_HOST, "Weight.TYPO_HOST",
                )
            } else if (flat.contains(primary)) {
                sawLookalike = true
                add(
                    "brand-in-host", "${b.display} appears inside this host, but does not own it",
                    "The name is decoration: it sits inside a longer label, or in a sub-label, and neither says anything about who answers for the address. The owner-part is «$owner».",
                    owner, Weight.LOOKALIKE_HOST, "Weight.LOOKALIKE_HOST",
                )
            }
            break
        }

        var borrowed: Pair<Brand, String>? = null
        outer@ for (b in Brands.all) {
            for (d in b.domains) {
                if (hostUnicode.startsWith("$d.") && owner != d) {
                    borrowed = b to d
                    break@outer
                }
            }
        }
        borrowed?.let { (b, d) ->
            sawLookalike = true
            add(
                "domain-in-subdomain", "${b.display}'s whole address is used as one label of another domain",
                "Read it right to left: the owner is «$owner», and «$d» is a sub-label beneath it. That position costs nothing to write, which is why it appears in messages.",
                hostUnicode.take(70), Weight.DOMAIN_IN_SUBDOMAIN, "Weight.DOMAIN_IN_SUBDOMAIN",
            )
        }

        /* A look-alike spelled one keystroke wrong is by definition not a substring
           match, so the loop above can never find it. Two edits are allowed only at
           the same length, which is what a substituted character costs; an inserted
           or dropped character has to stand alone. */
        if (!sawLookalike && !sawTypo && !sawUnfamiliarSuffix && ownerLabel.length >= 5) {
            for (b in Brands.all) {
                val real = b.domains.map { translit(registeredLabelOf(it)) }.distinct()
                val near = real.firstOrNull { c ->
                    if (c.length < 5 || c == ownerLabel) false
                    else if (c.length == ownerLabel.length) levenshtein(ownerLabel, c, 2) in 1..2
                    else levenshtein(ownerLabel, c, 1) == 1
                } ?: continue
                val edits = levenshtein(ownerLabel, near, 2)
                sawTypo = true
                add(
                    "brand-typo", "$edits keystroke${if (edits == 1) "" else "s"} off ${b.display}'s address",
                    "The owner-part is «$owner», which ${b.display} does not use, and it differs from «$near» by $edits edit${if (edits == 1) "" else "s"}. Put the two side by side before believing this one — it is the single easiest check to do yourself.",
                    owner, Weight.TYPO_HOST, "Weight.TYPO_HOST",
                )
                break
            }
        }

        /* Letter pairs that fold into one letter are two edits away, so the test
           above cannot see them: "rn" for "m" is written precisely to be read as the
           brand at a glance. Collapsing them and asking for an exact match keeps this
           narrow — a genuine brand label collapses to itself and never fires. */
        if (!sawLookalike && !sawTypo && !sawUnfamiliarSuffix && ownerLabel.length >= 5) {
            val collapsed = VISUAL_COLLAPSE.fold(ownerLabel) { acc, (from, to) -> acc.replace(from, to) }
            if (collapsed != ownerLabel) {
                val spoofed = Brands.all.firstOrNull { b ->
                    b.domains.map { translit(registeredLabelOf(it)) }.any { it == collapsed }
                }
                if (spoofed != null) {
                    sawTypo = true
                    add(
                        "brand-typo", "Folded letter pairs spelled to read as ${spoofed.display}",
                        "«$ownerLabel» is not «$collapsed», but collapse the pairs that double up for one letter — rn for m, vv for w, nn for u — and it is exactly. This is written to be recognised rather than read.",
                        owner, Weight.TYPO_HOST, "Weight.TYPO_HOST",
                    )
                }
            }
        }

        if (Hops.shorteners.any { hostUnicode == it || hostUnicode.endsWith(".$it") }) {
            add(
                "shortener", "A forwarding service, so the destination is not in this text",
                "Resolving it means making a request to a server chosen by whoever sent the link, and that request itself tells them the number is live. This build does not do that. Type the real address, or ask the sender, and read it again.",
                hostUnicode, Weight.SHORTENER, "Weight.SHORTENER",
            )
        }
        if (Hops.chatDeeplinks.any { hostUnicode == it || hostUnicode.endsWith(".$it") }) {
            add(
                "off-platform-link", "This link moves the conversation into a chat app",
                "Whatever was said before the jump cannot be seen or traced from there. Requests that begin by taking you to a private chat are conversations with no record.",
                hostUnicode, Weight.OFF_PLATFORM, "Weight.OFF_PLATFORM",
            )
        }

        val suffix = labelsOf(owner).lastOrNull() ?: ""
        if (Suffixes.cheap.contains(suffix) || Suffixes.suspiciousSingleLabelTlds.contains(suffix)) {
            add(
                "cheap-tld", "Registered under .$suffix, a suffix sold in bulk",
                "Cheap by the thousand and replaced when they are taken down, which is what a campaign needs. They are also used by an enormous number of honest sites, so this one finding on its own is worth very little.",
                suffix, Weight.CHEAP_TLD, "Weight.CHEAP_TLD",
            )
        }

        if (scheme == "http") {
            add(
                "no-tls", "Plain http",
                "Nothing between the phone and that server is encrypted. For a page that wants a password this is close to disqualifying; for a ten-year-old club site it is simply old.",
                "http://", Weight.NO_TLS, "Weight.NO_TLS",
            )
        }

        if (port != null) {
            add(
                "odd-port", "Reached on port $port",
                "Pages people are asked to sign in to sit on 443. A port in the tens of thousands is usually an exposed app or device panel, which is not what a company sends customers.",
                ":$port", Weight.ODD_PORT, "Weight.ODD_PORT",
            )
        }

        if (labels.size >= 5 && phoneScheme.not() && scheme != "mailto") {
            add(
                "deep-subdomains", "Four or more labels before the path starts",
                if (suffixKnown) "The part that decides who answers is «$owner». Everything to the left of it is a free choice of whoever registered that address, including a name that is not theirs."
                else "The part that decides who answers is a guess here, because the suffix is not in this app's list — which is exactly why a long chain of labels should not be read from left to right.",
                hostUnicode.take(70), Weight.DEEP_SUBDOMAINS, "Weight.DEEP_SUBDOMAINS",
            )
        }

        if (!suffixKnown && scheme.isNotEmpty() && !phoneScheme && scheme != "mailto" &&
            hostUnicode.contains(".") && hostUnicode.any { it.isLetter() }
        ) {
            add(
                "unknown-suffix", "No public suffix this app knows, so ownership is a guess",
                "This build ships a curated subset of the Public Suffix List rather than all of it. Where a host ends in something outside that subset, which two labels decide ownership cannot be stated, and the reading says so instead of choosing one.",
                hostUnicode.take(60), 0.0, NOT_WEIGHTED,
            )
        }

        val sld = ownerLabel
        if (sld.length >= 8 && sld.all { it.isLetterOrDigit() }) {
            val digits = sld.count { it.isDigit() }
            var run = 0
            var maxRun = 0
            for (c in sld) {
                if (c.isLetter() && !VOWELS.contains(c)) {
                    run++; maxRun = max(run, maxRun)
                } else run = 0
            }
            if (maxRun >= 5 || digits >= 4) {
                add(
                    "generated-label", "The registered name reads as generated rather than chosen",
                    "«$sld» is ${sld.length} characters, with a run of $maxRun consonants and $digits digits. Handwritten brand names rarely look like that; batch-registered ones almost always do.",
                    sld, Weight.RANDOM_LABEL, "Weight.RANDOM_LABEL",
                )
            }
        }

        if (pathAndQuery.isNotBlank() && scheme != "mailto" && !phoneScheme) {
            val credPath = Regex("""(?i)(login|signin|sign-in|verify|secure|account|wallet|refund|kyc|otp|unlock|unblock|update|confirm|gift|reward|bonus|prize|claim|invoice|payment)""")
                .find(pathAndQuery)
            if (credPath != null && sawLookalike && suffixKnown) {
                add(
                    "path-says-what-it-is-for", "The path is a login, a refund or a claim — on an address that is not the company's",
                    "«${credPath.value}» in the path is what the page is for; «$owner» is whose it is. Those two disagreeing is the whole mechanism of credential phishing, and it is readable without opening anything.",
                    credPath.value, 2.0, "stated composite, 2.0",
                )
            }
        }

        return LinkScan(
            raw = raw,
            scheme = scheme,
            hostAsTyped = hostRaw,
            hostUnicode = hostUnicode,
            owner = owner,
            suffixKnown = suffixKnown,
            ownerLabel = ownerLabel,
            port = port,
            path = pathAndQuery.take(120),
            findings = fs.toList(),
        )
    }

    /* -------------------------------------------------- the message itself */

    /**
     * Cue matching is per family, not per occurrence: a message that says
     * "urgently", "immediately" and "right now" earns exactly one urgency finding.
     * Short Latin cues are matched on word boundaries so "dear" cannot be found
     * inside "dead".
     */
    private fun cueMatches(hay: String, cue: String): Boolean {
        val c = translit(cue)
        if (c.isEmpty()) return false
        val asciiWord = c.all { it in 'a'..'z' || it.isDigit() || it == '.' || it == '-' || it == '\'' }
        return if (asciiWord && c.length <= 6) {
            Regex("(?i)(?:^|[^a-z0-9])" + Regex.escape(c) + "(?:[^a-z0-9]|$)").containsMatchIn(hay)
        } else {
            hay.contains(c)
        }
    }

    private fun messageFindings(
        text: String,
        scans: List<LinkScan>,
        perLink: List<Finding>,
    ): Pair<List<Finding>, List<String>> {
        val out = ArrayList<Finding>()
        val absent = ArrayList<String>()
        val hay = translit(scrubInvisible(text).lowercase())

        val hits = LinkedHashMap<Framing, List<String>>()
        for (fam in Framing.values()) {
            val matched = fam.cues.filter { cueMatches(hay, it) }
            if (matched.isEmpty()) absent.add("Framing · ${fam.label}") else hits[fam] = matched
        }
        var familyIndex = 0
        for ((fam, matched) in hits) {
            out.add(
                Finding(
                    key = "framing-${fam.name.lowercase()}",
                    label = fam.label,
                    detail = fam.note + " Matched: " + matched.take(4).joinToString { "«$it»" } +
                        if (matched.size > 4) " and ${matched.size - 4} more in the same family." else ".",
                    evidence = matched.take(4).joinToString(" · ").take(90),
                    weight = if (familyIndex == 0) Weight.FRAMING else Weight.FRAMING_EXTRA,
                    on = if (familyIndex == 0) "Weight.FRAMING" else "Weight.FRAMING_EXTRA",
                ),
            )
            familyIndex++
        }

        val named = Brands.byCue.firstOrNull { cueMatches(hay, it.first) }
        if (named != null) {
            val ownedHere = scans.any { s -> named.second.domains.any { s.hostUnicode == it || s.hostUnicode.endsWith(".$it") } }
            if (!ownedHere && scans.isNotEmpty()) {
                out.add(
                    Finding(
                        "brand-off-domain",
                        "${named.second.display} is named in the message, and no link in it belongs to ${named.second.display}",
                        "The wording says one organisation and the addresses say another. Alone, this is ordinary — a link *about* Shopee is not from Shopee. It matters when the message also asks something of you.",
                        named.first, Weight.BRAND_OFF_DOMAIN, "Weight.BRAND_OFF_DOMAIN",
                    ),
                )
            }
        } else {
            absent.add("Table · a known brand named off its own domain")
        }

        val owners = scans.map { it.owner }.filter { it.isNotBlank() }.distinct()
        if (owners.size > 1) {
            out.add(
                Finding(
                    "many-links", "${owners.size} different owners in one message",
                    "This sends you to ${owners.take(3).joinToString(", ")}. One organisation talking to one customer does not usually need two domains, and a chain of them is a different problem again.",
                    owners.take(3).joinToString(" · "), Weight.MANY_LINKS, "Weight.MANY_LINKS",
                ),
            )
        }

        val asksSomething = hits.keys.any { it in setOf(Framing.VERIFY, Framing.CREDENTIALS, Framing.MONEY) }
        val brandHost = perLink.any { it.key in setOf("brand-in-host", "brand-typo", "domain-in-subdomain") }
        if (asksSomething && brandHost) {
            out.add(
                Finding(
                    "both-halves-agree", "The address and the wording point the same way",
                    "A look-alike host is one thing; a look-alike host attached to a request to verify, sign in or pay is the pairing this table was written for. The two halves are not independent evidence, which is why this adds a little and not a lot.",
                    "look-alike host + account or payment request", 2.0, "stated composite, 2.0",
                ),
            )
        }

        val invisible = text.count { INVISIBLE.contains(it) }
        if (invisible > 0) {
            out.add(
                Finding(
                    "invisible-chars", "$invisible character${if (invisible == 1) "" else "s"} that do not print",
                    "Zero-width and bidi characters are invisible in every app, so the text you read and the text a parser reaches are not the same. Beside or inside an address, that is a disguise rather than an accident.",
                    "U+200B…U+FEFF, U+202A…U+202E", Weight.INVISIBLE_CHARS, "Weight.INVISIBLE_CHARS",
                ),
            )
        } else {
            absent.add("Table · invisible characters")
        }

        return out.toList() to absent
    }

    private fun absentChecks(fired: List<Finding>, scans: List<LinkScan>): List<String> {
        val keys = fired.map { it.key }.toSet()
        val checks = listOf(
            "brand-in-host" to "Host · a known brand inside a host that is not its",
            "brand-typo" to "Host · one or two keystrokes from a known address",
            "domain-in-subdomain" to "Host · a whole address used as a sub-label",
            "at-authority" to "Host · text carried before an @",
            "ip-host" to "Host · a numeric address",
            "mixed-script" to "Host · two scripts in one name",
            "punycode" to "Host · encoded internationalised name",
            "shortener" to "Host · a forwarding service",
            "nested-url" to "Query · a folded second address",
            "cheap-tld" to "Suffix · bulk-registered",
            "generated-label" to "Label · reads as generated",
            "odd-port" to "Port · non-standard",
            "no-tls" to "Scheme · plain http",
            "phone-link" to "Scheme · dials rather than browses",
            "off-platform-link" to "Host or scheme · moves you to a chat app",
            "deep-subdomains" to "Host · four or more labels deep",
        )
        val out = checks.filter { !keys.contains(it.first) }.map { it.second }.toMutableList()
        if (scans.isEmpty()) out.add("Every host check — there was no link in the text to run them on")
        return out
    }

    /* ------------------------------------------------------------ outputs */

    /** Plain text for the clipboard. Derived findings; it quotes what matched. */
    fun reportText(r: Reading): String = buildString {
        appendLine("ScamGuard reading")
        appendLine(r.band)
        appendLine("${fmt(r.score)} points across ${r.scored.size} finding${if (r.scored.size == 1) "" else "s"}, from a published weight table. Points, not a probability, and not a verdict.")
        appendLine()
        if (r.links.isEmpty()) {
            appendLine(r.note)
            appendLine()
        } else {
            appendLine("Links, in the order they appear:")
            r.links.forEachIndexed { i, s ->
                appendLine("  ${i + 1}. ${s.raw.take(160)}")
                appendLine("     who owns it: ${s.owner}" + if (s.suffixKnown) "" else " (a guess; this suffix is not in the app's list)")
                if (s.findings.isEmpty()) appendLine("     no host-shaped finding")
                s.findings.forEach { appendLine("     - ${it.label}  (${fmt(it.weight)} pts, ${it.on})") }
            }
            appendLine()
        }
        if (r.message.isNotEmpty()) {
            appendLine("About the wording:")
            r.message.forEach {
                appendLine("  - ${it.label}  (${fmt(it.weight)} pts, ${it.on})")
                appendLine("    ${it.detail}")
            }
            appendLine()
        }
        if (r.absent.isNotEmpty()) {
            appendLine("Checked, and did not match (${r.absent.size}):")
            r.absent.take(20).forEach { appendLine("  . $it") }
            appendLine()
        }
        appendLine("What this cannot see:")
        Limits.cannotSee.forEach { appendLine("  . $it") }
        appendLine()
        appendLine("Made on the device by ScamGuard. No request was sent, no list was queried, nothing was stored.")
    }

    /**
     * What goes to the user's own endpoint if they choose to configure one: the
     * findings, never the message. The finding text can quote an address, because
     * the address is what was found.
     */
    fun promptPayload(r: Reading): String = buildString {
        appendLine("Findings, weight and matched text, in the order fired. No message body is included.")
        if (r.links.isEmpty()) appendLine("No link was present; the wording was read on its own.")
        r.links.forEach { s ->
            appendLine("Address owned by: ${s.owner} (suffix recognised: ${s.suffixKnown})")
            s.findings.forEach { appendLine("- ${it.label} | ${fmt(it.weight)} | matched: ${it.evidence}") }
        }
        r.message.forEach { appendLine("- ${it.label} | ${fmt(it.weight)} | matched: ${it.evidence}") }
        appendLine("Total: ${fmt(r.score)} points. Band as printed on screen: ${r.band}")
    }
}
