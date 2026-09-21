package dev.capriguard.scamguard.link

import kotlin.math.roundToInt

/**
 * Every number, name and list this app can state about a message, in one file.
 *
 * Nothing here is a secret and nothing here is a statistic the app cannot show
 * you the source of. A finding that fires is traceable to a line in this file,
 * which is the only kind of scoring worth arguing with.
 */

/* ---------------------------------------------------------------- weights */

/**
 * Points, not probabilities. They add, they do not multiply, and no single item
 * can carry a reading on its own — a cheap domain plus an off-brand host is the
 * shape of a real scam, and either one alone is the shape of a normal Tuesday.
 */
object Weight {
    /** The host is not the domain of a brand the app knows, but contains it. */
    const val LOOKALIKE_HOST = 8.0

    /** The owner-part of the host is a one-character edit of a known domain. */
    const val TYPO_HOST = 6.0

    /** Something that reads as a domain is carried inside another domain's labels. */
    const val DOMAIN_IN_SUBDOMAIN = 7.0

    /** `http://evil.tld?@shopee.com/` — the authority and the visible text disagree. */
    const val CREDENTIAL_TRICK = 7.0

    /** Letters from two scripts inside one host, or Cyrillic where Latin was meant. */
    const val HOMOGLYPH_HOST = 6.0

    /** Zero-width or bidi characters inside or around a link. */
    const val INVISIBLE_CHARS = 6.0

    /** A dotted-decimal address instead of a name. */
    const val IP_LITERAL = 6.0

    /** The real destination is one hop away and the app will not take the hop. */
    const val SHORTENER = 2.0

    /** Another full URL is tucked into the query string — an open-redirect shape. */
    const val NESTED_URL = 5.0

    /** `xn--` labels: legal, and the mechanism behind most look-alike domains. */
    const val PUNYCODE = 3.0

    /** The host's own label is long, vowel-poor and looks generated. */
    const val RANDOM_LABEL = 4.0

    /** Bulk-registered TLD. Also used by a great many honest sites. */
    const val CHEAP_TLD = 1.5

    /** `http://` where a login page would be expected to sit behind TLS. */
    const val NO_TLS = 1.0

    /** A port the browser would not normally be told to use. */
    const val ODD_PORT = 3.0

    /** More sub-labels than a hand-written address usually carries. */
    const val DEEP_SUBDOMAINS = 2.0

    /** A brand is named in the text and none of the links belongs to it. */
    const val BRAND_OFF_DOMAIN = 4.0

    /** The message pushes the conversation onto a chat app or a phone number. */
    const val OFF_PLATFORM = 3.0

    /** Points the message's own framing earns, one per category, never per hit. */
    const val FRAMING = 3.0

    /** A second category of framing in the same message. */
    const val FRAMING_EXTRA = 2.0

    /** More than one link, going to more than one owner. */
    const val MANY_LINKS = 2.0
}

/* --------------------------------------------------------------- banding */

object Bands {
    /** Below this, the honest word is "inconclusive", not "clean". */
    const val SOME_SIGNALS_FROM = 5.0

    /** At or above this, several independent things point the same way. */
    const val SEVERAL_SIGNALS_FROM = 12.0

    const val MAX_REPORTED = 40.0
}

/**
 * The whole scoring surface, in the order a reader should meet it: strongest and
 * most specific first. `on` is the exact string every Finding carries, so the
 * screen can print this table next to a reading and the two line up by themselves.
 */
data class WeightRow(val on: String, val points: Double, val what: String)

object AuditTable {
    val rows: List<WeightRow> = listOf(
        WeightRow("Weight.LOOKALIKE_HOST", Weight.LOOKALIKE_HOST, "A brand's name sits inside a host that is not the brand's"),
        WeightRow("Weight.DOMAIN_IN_SUBDOMAIN", Weight.DOMAIN_IN_SUBDOMAIN, "A brand's whole address used as one label under somebody else's domain"),
        WeightRow("Weight.CREDENTIAL_TRICK", Weight.CREDENTIAL_TRICK, "Text before an @, so the address you read is not the one reached"),
        WeightRow("Weight.HOMOGLYPH_HOST", Weight.HOMOGLYPH_HOST, "Two writing systems inside one host"),
        WeightRow("Weight.INVISIBLE_CHARS", Weight.INVISIBLE_CHARS, "Characters that never print, beside or inside a link"),
        WeightRow("Weight.TYPO_HOST", Weight.TYPO_HOST, "One or two keystrokes from an address this app knows"),
        WeightRow("Weight.IP_LITERAL", Weight.IP_LITERAL, "A dotted number where a name should be"),
        WeightRow("Weight.NESTED_URL", Weight.NESTED_URL, "A second address folded into the first one's query"),
        WeightRow("Weight.BRAND_OFF_DOMAIN", Weight.BRAND_OFF_DOMAIN, "A brand named in the wording, no link belonging to it"),
        WeightRow("Weight.RANDOM_LABEL", Weight.RANDOM_LABEL, "A registered name that reads as generated rather than chosen"),
        WeightRow("Weight.PUNYCODE", Weight.PUNYCODE, "An internationalised name shown in its encoded form"),
        WeightRow("Weight.ODD_PORT", Weight.ODD_PORT, "A port a login page would not be published on"),
        WeightRow("Weight.OFF_PLATFORM", Weight.OFF_PLATFORM, "A dialled number or a chat deep link instead of a page"),
        WeightRow("Weight.FRAMING", Weight.FRAMING, "The first framing family whose wording matched"),
        WeightRow("Weight.FRAMING_EXTRA", Weight.FRAMING_EXTRA, "Each further family, once per family"),
        WeightRow("Weight.SHORTENER", Weight.SHORTENER, "A forwarding service, whose destination this app will not follow"),
        WeightRow("Weight.MANY_LINKS", Weight.MANY_LINKS, "More than one owner in a single message"),
        WeightRow("Weight.DEEP_SUBDOMAINS", Weight.DEEP_SUBDOMAINS, "Four or more labels before the path starts"),
        WeightRow("stated composite, 2.0", 2.0, "Two halves agreeing: an account or payment ask aimed at a look-alike host"),
        WeightRow("Weight.CHEAP_TLD", Weight.CHEAP_TLD, "A suffix sold in bulk, and also used by a great many honest sites"),
        WeightRow("Weight.NO_TLS", Weight.NO_TLS, "Plain http"),
        WeightRow("not weighted — a coverage statement", 0.0, "A suffix outside the shipped list, where ownership cannot be stated"),
    )
}

/* ------------------------------------------------------ brands we can name */

/**
 * A brand the app is willing to say a sentence about, the words that suggest it
 * in a message, and the domains it actually controls. The last field is the one
 * that does the work: everything else is a guess about intent.
 *
 * Deliberately weighted towards services this app's users are scammed with —
 * marketplaces, wallets, couriers, banks and telcos in Malaysia, Singapore,
 * Indonesia, the Philippines, Taiwan and Thailand, plus the global accounts
 * (Google, Apple, Netflix, PayPal) that appear in credential-phishing everywhere.
 */
data class Brand(val display: String, val cues: List<String>, val domains: List<String>)

object Brands {
    val all: List<Brand> = listOf(
        Brand("Shopee", listOf("shopee", "蝦皮", "虾皮"), listOf("shopee.com.my", "shopee.my", "shopee.sg", "shopee.tw", "shopee.ph", "shopee.co.id", "shopee.vn", "shopee.com", "shp.ee")),
        Brand("Lazada", listOf("lazada"), listOf("lazada.com.my", "lazada.com.sg", "lazada.com.ph", "lazada.co.id", "lazada.vn", "lazada.co.th", "lazada.com")),
        Brand("Grab", listOf("grab"), listOf("grab.com", "grabpay.com")),
        Brand("Touch 'n Go", listOf("touch n go", "touch 'n go", "tng", "tng ewallet", "eWallet"), listOf("touchngo.com.my", "tngdigital.com.my")),
        Brand("GCash", listOf("gcash", "mynt"), listOf("gcash.com")),
        Brand("Maya", listOf("maya", "paymaya"), listOf("maya.ph")),
        Brand("Boost", listOf("boost"), listOf("myboost.com")),
        Brand("Maybank", listOf("maybank", "m2u"), listOf("maybank.com.my", "maybank2u.com.my")),
        Brand("CIMB", listOf("cimb"), listOf("cimb.com.my", "cimbbank.com.my", "cimbclicks.com.my")),
        Brand("Public Bank", listOf("public bank", "pbe online"), listOf("publicbank.com.my")),
        Brand("DBS / POSB", listOf("dbs", "posb"), listOf("dbs.com", "dbs.com.sg")),
        Brand("OCBC", listOf("ocbc"), listOf("ocbc.com", "ocbc.sg")),
        Brand("UOB", listOf("uob", "uobdigital"), listOf("uob.com.sg", "uobgroup.com", "uob.com.my")),
        Brand("HSBC", listOf("hsbc"), listOf("hsbc.com.my", "hsbc.com.sg", "hsbc.com")),
        Brand("PayPal", listOf("paypal"), listOf("paypal.com", "paypal.me")),
        Brand("Netflix", listOf("netflix"), listOf("netflix.com")),
        Brand("Google account", listOf("google", "gmail"), listOf("google.com", "gmail.com")),
        Brand("Apple / iCloud", listOf("apple", "icloud", "itunes"), listOf("apple.com", "icloud.com")),
        Brand("Microsoft account", listOf("microsoft", "outlook", "onedrive", "hotmail"), listOf("microsoft.com", "live.com", "outlook.com", "office.com")),
        Brand("Telegram", listOf("telegram", "t.me"), listOf("telegram.org", "t.me", "web.telegram.org")),
        Brand("WhatsApp", listOf("whatsapp", "wa.me"), listOf("whatsapp.com", "wa.me")),
        Brand("WeChat", listOf("wechat", "weixin"), listOf("wechat.com", "weixin.qq.com", "qq.com")),
        Brand("Binance", listOf("binance"), listOf("binance.com")),
        Brand("DHL", listOf("dhl"), listOf("dhl.com", "dhl.com.my", "dhl.co.th")),
        Brand("FedEx", listOf("fedex"), listOf("fedex.com")),
        Brand("Ninja Van", listOf("ninjavan", "ninja van"), listOf("ninjavan.co")),
        Brand("J&T Express", listOf("j&t", "j&t express", "jt express"), listOf("jtexpress.com.my", "jtexpress.sg", "jtexpress.com")),
        Brand("Pos Laju", listOf("pos laju", "poslaju"), listOf("poslaju.com.my")),
        Brand("Singtel", listOf("singtel"), listOf("singtel.com")),
        Brand("StarHub", listOf("starhub"), listOf("starhub.com")),
        Brand("Unifi / TM", listOf("unifi", "tm net"), listOf("unifi.com.my", "tm.com.my")),
        Brand("Astro", listOf("astro"), listOf("astro.com.my")),
        Brand("IRAS (Singapore tax)", listOf("iras", "tax refund", "gst"), listOf("iras.gov.sg")),
        Brand("LHDN (Malaysian tax)", listOf("lhdn", "hasil", "e-filing"), listOf("lhdn.gov.my")),
        Brand("Inland Revenue (Taiwan)", listOf("稅務", "財政區賦"), listOf("eptax.nat.gov.tw", "nat.gov.tw")),
        Brand("Customs (MY)", listOf("customs", "kastam", "sjk"), listOf("customs.gov.my")),
        Brand("Facebook / Meta", listOf("facebook", "meta"), listOf("facebook.com", "fb.com", "meta.com")),
        Brand("TikTok", listOf("tiktok"), listOf("tiktok.com")),
        Brand("99 Speedmart", listOf("99 speedmart", "speedmart"), listOf("99speedmart.com.my")),
    )

    /** Longest cue first, so "touch n go" wins over "go" style collisions. */
    val byCue: List<Pair<String, Brand>> = all
        .flatMap { b -> b.cues.map { it.lowercase() to b } }
        .distinctBy { it.first }
        .sortedByDescending { it.first.length }
}

/* -------------------------------------------------- domains we can resolve */

/**
 * A curated subset of the Public Suffix List, not the whole thing. It covers the
 * ccTLD second levels this app's users actually receive links to. Where a host's
 * suffix is not in here, the app says the owner-part is *its best guess* rather
 * than pretending to know.
 */
object Suffixes {
    val twoPart: Set<String> = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk",
        "com.sg", "net.sg", "org.sg", "gov.sg", "edu.sg",
        "com.my", "net.my", "org.my", "gov.my", "edu.my",
        "co.id", "web.id", "or.id", "go.id",
        "com.ph", "net.ph", "org.ph", "gov.ph",
        "com.tw", "org.tw", "gov.tw", "idv.tw",
        "co.th", "or.th", "go.th",
        "co.jp", "or.jp", "ne.jp", "go.jp", "ac.jp",
        "com.hk", "org.hk", "gov.hk", "idv.hk",
        "com.vn", "net.vn", "org.vn", "gov.vn",
        "co.kr", "or.kr", "go.kr", "ne.kr",
        "com.br", "net.br", "org.br", "gov.br",
        "com.au", "net.au", "org.au", "gov.au",
        "com.cn", "net.cn", "org.cn", "gov.cn",
        "co.in", "net.in", "org.in", "gov.in",
        "com.pk", "com.bd", "com.mo", "com.bn",
    )

    val knownSingle: Set<String> = setOf(
        "com", "net", "org", "gov", "edu", "mil", "int", "info", "biz", "name",
        "io", "ai", "co", "me", "app", "dev", "xyz", "top", "online", "site",
        "store", "shop", "icu", "cyou", "monster", "click", "link", "vip", "club",
        "space", "website", "fun", "buzz", "quest", "sbs", "asia", "live", "tech",
        "world", "city", "life", "plus", "network", "host", "cloud", "tw", "hk",
        "sg", "my", "id", "ph", "th", "vn", "jp", "kr", "cn", "in", "au", "nz",
        "uk", "us", "ca", "de", "fr", "nl", "se", "no", "fi", "dk", "ch", "at",
        "be", "ie", "es", "pt", "pl", "cz", "ru", "ua", "tr", "sa", "ae", "il",
        "za", "ng", "ke", "br", "mx", "ar", "cl", "sg", "eu",
    )

    /** Bulk-registered suffixes that show up in phishing far more than average. */
    val cheap: Set<String> = setOf(
        "top", "xyz", "icu", "cyou", "monster", "click", "buzz", "quest", "sbs",
        "loan", "work", "fit", "beauty", "icu", "win", "biz", "info", "online",
        "site", "space", "website", "live", "link", "shop", "store", "vip", "asia",
    )

    val suspiciousSingleLabelTlds: Set<String> = setOf("zip", "mov", "api", "cs", "epizy", "rz", "tk", "ml", "ga", "cf", "gq")
}

/** Hop services whose destination the app deliberately will not follow. */
object Hops {
    val shorteners: Set<String> = setOf(
        "bit.ly", "tinyurl.com", "t.cn", "goo.gl", "ow.ly", "is.gd", "buff.ly",
        "t.co", "cutt.ly", "rb.gy", "rebrand.ly", "shorturl.at", "lnkd.in",
        "spl.ink", "s.id", "m2r.gg", "4sq.com", "j.mp", "v.gd", "clp.im",
        "cuturl.in", "uo.to", "kie.nz", "tiny.cc", "tr.im", "fave.co", "trib.al",
    )

    /** Deep links into a chat app: the conversation leaves the platform here. */
    val chatDeeplinks: Set<String> = setOf(
        "wa.me", "api.whatsapp.com", "web.whatsapp.com", "t.me", "telegram.me",
        "line.me", "m.me", "messenger.facebook.com", "viber://", "skype:",
    )
}

/* ------------------------------------------------------ the message framing */

/**
 * Phrase families, in English and Chinese, each one a shape a real scam message
 * has been observed to take. Matching is per family and counted once per family,
 * so a message that says "urgently" and "immediately" and "right now" scores the
 * same as one that says "immediately" — a repeated word is not two facts.
 *
 * These are word-shape patterns, not a model. They miss anything phrased
 * differently, and they will occasionally catch a bank's honest reminder.
 */
enum class Framing(val label: String, val note: String, val cues: List<String>) {
    URGENCY(
        "Time pressure",
        "Deadlines and 'act now' exist to stop you reading the address.",
        listOf(
            "immediately", "right now", "within 24", "within24", "expires", "expire", "last day",
            "hurry", "urgent", "urgently", "asap", "today only", "final notice", "less than",
            "before it is closed", "before it's closed", "countdown", "do this now",
            "立即", "马上", "尽快", "24小时内", "二十四小时内", "即将过期", "最后", "限时", "今日", "截止", "赶紧", "速速", "立刻",
        ),
    ),
    THREAT(
        "Threat of loss",
        "Suspension, freeze, penalty, warrant — fear does the work a link cannot.",
        listOf(
            "suspended", "suspend", "will be closed", "disabled", "blocked", "frozen", "freeze",
            "restricted", "permanently", "arrest", "warrant", "penalty", "fine imposed", "legal action",
            "account closed", "terminate", "deactivated", "lost access",
            "已被冻结", "账号被冻结", "将被停用", "永久封", "涉嫌", "违法", "拘捕", "罚款", "已封锁", "已停用", "限制使用", "涉案",
        ),
    ),
    PRIZE(
        "A win you did not enter",
        "Nobody wins a lottery they never bought a ticket for.",
        listOf(
            "you have won", "you've won", "congratulations you", "winner", "selected", "lucky draw",
            "gift package", "free iphone", "reward claimed", "unclaimed", "voucher for you",
            "cashback prize", "your prize", "draw result", "eligible to receive",
            "恭喜", "中奖", "获派", "抽奖", "幸运", "奖品", "免费赠送", "已选中", "获得奖金", "未领取", "派奖", "抽奖活动",
        ),
    ),
    VERIFY(
        "Verify your identity",
        "The legitimate version of this asks nothing of you by link.",
        listOf(
            "verify", "verification", "re-verify", "confirm your identity", "kyc", "re-kyc",
            "update your info", "update your details", "validate your account", "identity check",
            "one time password", "share the code", "enter the otp", "provide the code", "security code",
            "验证", "实名认证", "身份验证", "更新资料", "填写信息", "验证码", "安全码", "校验", "解冻", "申诉",
        ),
    ),
    MONEY(
        "Move money, or hand over a code",
        "Payment diversion, gift cards, crypto and 'advance fee' all live here.",
        listOf(
            "pay a fee", "small fee", "customs fee", "delivery fee", "clearance", "release payment",
            "refund process", "gift card", "itunes card", "google play card", "steam wallet",
            "usdt", "crypto transfer", "wallet address", "deposit to", "transfer to our", "advance payment",
            "reimbursement", "amount will be returned", "processing charge", "unlock the refund",
            "手续费", "清关", "代缴", "解冻费", "充值", "转账", "退款需", "先支付", "礼品卡", "提货费", "保证金", "缴纳",
        ),
    ),
    JOB(
        "Work that pays for tapping",
        "The 'like and earn' task scam: small first payout, large fake balance.",
        listOf(
            "part time", "part-time", "earn daily", "daily commission", "task commission", "like task",
            "subscribe task", "product rating", "add to cart task", "no experience", "work from home",
            "salary will be", "get paid to", "review jobs", "order boosting",
            "兼职", "日结", "佣金", "点赞任务", "刷单", "关注任务", "在家可做", "无门槛", "日入", "返佣",
        ),
    ),
    INVEST(
        "Guaranteed returns",
        "Nothing is guaranteed, which is exactly why it is promised.",
        listOf(
            "guaranteed profit", "guaranteed return", "daily profit", "double your", "risk free",
            "insider signal", "trading signal", "mining pool", "yield plan", "investment plan",
            "principal protected", "no loss", "profit daily", "vip group", "mentor",
            "稳赚", "保本", "日收益", "内部消息", "带单", "拉群", "老师", "投资平台", "翻倍", "无风险",
        ),
    ),
    DELIVERY(
        "A parcel you did not order",
        "Courier redelivery scams get their credibility from you expecting a parcel.",
        listOf(
            "parcel", "shipment", "delivery failed", "delivery attempt", "courier", "held at depot",
            "pending delivery", "your package", "redeliver", "waybill", "tracking number is invalid",
            "包裹", "快递", "派送失败", "取件", "物流", "运单", "签收", "海关扣留", "补发",
        ),
    ),
    SUPPORT(
        "Support that is not the company's",
        "A number or chat handle substituted for the company's own channels.",
        listOf(
            "customer support", "help desk", "support team", "call us", "contact number", "toll free",
            "1800", "helpline", "agent will", "chat with an agent", "whatsapp only", "not via call",
            "do not share", "official only", "customer care",
            "客服", "客服热线", "官方客服", "专员", "请勿外泄", "加客服", "唯一联系方式",
        ),
    ),
    AUTHORITY(
        "Government or bank standing in",
        "Agencies write to you on letterhead, and do not send a link to re-register.",
        listOf(
            "inland revenue", "tax refund", "tax authority", "customs department", "immigration",
            "election", "voter", "police", "court", "registry", "central bank", "banking ombudsman",
            "official notice", "government scheme", "subsidy", "allowance payout", "bantuan",
            "税务局", "海关", "公安", "法院", "政府公告", "补贴", "退税", "官方通知",
        ),
    ),
    ROMANCE(
        "Familiarity, quickly",
        "Trust built in days is a channel, not a relationship.",
        listOf(
            "we met", "mutual friend", "your profile", "single again", "encrypted chat",
            "signal app", "my love", "husband", "wife", "prison", "diplomat", "oil rig",
            "亲爱的", "加我微信", "转私聊",
        ),
    ),
    CREDENTIALS(
        "A login handed over",
        "Sign-in pages reached from a message are where accounts go.",
        listOf(
            "sign in to continue", "log in to", "login expired", "re-enter your password",
            "secure your account", "unusual activity", "new device", "session expired", "unlock account",
            "confirm password", "reset password", "unauthorized", "someone tried",
            "重新登录", "密码失效", "异地登录", "账号异常", "解锁账号", "修改密码",
        ),
    ),
}

/** Word-shapes that make an off-brand host much more likely to matter. */
object Context {
    /** When one of these is near a brand cue, a brand-shaped host is not a coincidence. */
    val accountWords: List<String> = Framing.VERIFY.cues + Framing.CREDENTIALS.cues + Framing.MONEY.cues
}

/** Things the reading always prints alongside any score. */
object Limits {
    val cannotSee: List<String> = listOf(
        "A domain registered yesterday. Nothing offline knows it; no list this app ships is ahead of it either.",
        "Whether the site behind the address is real. The app never opens it, so a well-built storefront looks exactly like a badly-built one.",
        "The padlock. HTTPS is free, and every link in a message today can have one.",
        "Who actually sent it. A display name, an avatar and a number are all things a stranger chooses.",
        "Anything inside a picture, a voice note, a PDF or a QR code. Only text typed or shared into this app is read.",
        "A shortened link's destination. Following it is a network request, and that is the one thing this build will not do unasked.",
    )

    val willNotSay: List<String> = listOf(
        "No percentage, no confidence score, no 'this is a scam'. A number out of 100 implies a model with a known error rate. This is arithmetic over a published weight table, and it is shown to you as that.",
        "No blocklist answer. There is no threat feed behind this, and nothing you paste is looked up anywhere.",
        "No clearance. A clean reading means no pattern in this file matched. Most reported phishing is made of domains and wording no rule has ever seen.",
        "No advice about your money, your account, or who to contact. It reads text; it does not act for you.",
    )
}

/* ---------------------------------------------------------------- helpers */

/** Display strings for a band. Chosen so the absence of a result reads as such. */
fun bandWords(score: Double, fired: Int): String = when {
    fired == 0 -> "Nothing in the published table matched. That is not a clearance."
    score < Bands.SOME_SIGNALS_FROM -> "A few shapes worth reading yourself — inconclusive."
    score < Bands.SEVERAL_SIGNALS_FROM -> "Several shapes, not all independent — read the list."
    else -> "Several independent shapes point the same way — read it before tapping."
}

fun fmt(v: Double): String {
    val r = (v * 10.0).roundToInt() / 10.0
    return if (r == 0.0) "0" else if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
}
