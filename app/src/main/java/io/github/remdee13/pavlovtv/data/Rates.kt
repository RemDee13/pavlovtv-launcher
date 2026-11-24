package io.github.remdee13.pavlovtv.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.util.Currency
import java.util.Locale

enum class AssetKind { FIAT, CRYPTO, METAL, STOCK }

/** Something with a price: a currency, a coin or a metal. [yahoo] overrides the Yahoo Finance symbol. */
class RateAsset(val code: String, val kind: AssetKind, private val brand: String? = null, val yahoo: String? = null) {
    /** "US dollar", "Bitcoin", "Gold" in the screen language (coins keep their brand name). */
    fun name(locale: Locale): String = brand
        ?: runCatching { Currency.getInstance(code).getDisplayName(locale) }.getOrNull()?.replaceFirstChar { it.titlecase(locale) }
        ?: code
}

object RateCatalog {
    private val fiatCodes = listOf(
        "USD", "EUR", "THB", "RUB", "GBP", "CNY", "JPY", "KRW", "INR", "TRY", "AED", "KZT", "UAH", "BYN", "GEL", "AMD",
        "UZS", "VND", "IDR", "MYR", "SGD", "HKD", "PHP", "LKR", "CAD", "AUD", "NZD", "CHF", "SEK", "NOK", "DKK", "PLN",
        "CZK", "HUF", "ILS", "SAR", "EGP", "ZAR", "BRL", "MXN", "ARS",
    )
    val fiat: List<RateAsset> = fiatCodes.map { RateAsset(it, AssetKind.FIAT) }
    val crypto: List<RateAsset> = listOf(
        RateAsset("BTC", AssetKind.CRYPTO, "Bitcoin"),
        RateAsset("ETH", AssetKind.CRYPTO, "Ethereum"),
        RateAsset("SOL", AssetKind.CRYPTO, "Solana"),
        RateAsset("BNB", AssetKind.CRYPTO, "BNB"),
        RateAsset("XRP", AssetKind.CRYPTO, "XRP"),
        // Trading in TON is halted on Binance; Yahoo still has a live price.
        RateAsset("TON", AssetKind.CRYPTO, "Toncoin", yahoo = "TON11419-USD"),
        RateAsset("DOGE", AssetKind.CRYPTO, "Dogecoin"),
        RateAsset("ADA", AssetKind.CRYPTO, "Cardano"),
        RateAsset("TRX", AssetKind.CRYPTO, "TRON"),
        RateAsset("LTC", AssetKind.CRYPTO, "Litecoin"),
        RateAsset("DOT", AssetKind.CRYPTO, "Polkadot"),
        RateAsset("AVAX", AssetKind.CRYPTO, "Avalanche"),
        RateAsset("LINK", AssetKind.CRYPTO, "Chainlink"),
        RateAsset("XLM", AssetKind.CRYPTO, "Stellar"),
    )
    /** Futures prices in US dollars per troy ounce. */
    val metals: List<RateAsset> = listOf(
        RateAsset("XAU", AssetKind.METAL, yahoo = "GC=F"),
        RateAsset("XAG", AssetKind.METAL, yahoo = "SI=F"),
    )
    /** Shares and indices by their Yahoo Finance symbol, priced in the exchange's own currency. */
    val stocks: List<RateAsset> = listOf(
        "^GSPC" to "S&P 500", "^IXIC" to "Nasdaq", "^DJI" to "Dow Jones", "^N225" to "Nikkei 225", "^HSI" to "Hang Seng",
        "^GDAXI" to "DAX", "^FTSE" to "FTSE 100", "^SET.BK" to "SET (Thailand)",
        "AAPL" to "Apple", "MSFT" to "Microsoft", "NVDA" to "Nvidia", "GOOGL" to "Alphabet (Google)", "AMZN" to "Amazon",
        "META" to "Meta", "TSLA" to "Tesla", "NFLX" to "Netflix", "AMD" to "AMD", "INTC" to "Intel", "TSM" to "TSMC",
        "PLTR" to "Palantir", "COIN" to "Coinbase", "BABA" to "Alibaba", "DIS" to "Disney", "KO" to "Coca-Cola",
        "NKE" to "Nike", "V" to "Visa", "JPM" to "JPMorgan", "BRK-B" to "Berkshire Hathaway", "UBER" to "Uber", "SPOT" to "Spotify",
    ).map { (symbol, name) -> RateAsset(symbol, AssetKind.STOCK, name) }
    val all: List<RateAsset> = fiat + crypto + metals + stocks

    /** Shares are stored as "S:AAPL": always in their own currency, never converted. */
    const val STOCK_PREFIX = "S:"

    fun stockId(symbol: String): String = STOCK_PREFIX + symbol.trim().uppercase()

    fun isStock(id: String): Boolean = id.startsWith(STOCK_PREFIX)

    fun asset(code: String): RateAsset? = all.firstOrNull { it.code == code }

    fun defaults(home: String): List<String> =
        if (home == "USD") listOf("EUR/USD", "GBP/USD", "BTC/USD") else listOf("USD/$home", "EUR/$home", "BTC/USD")

    /** "USD/THB" -> ("USD", "THB"). */
    fun split(id: String): Pair<String, String>? = id.split('/').takeIf { it.size == 2 && it[0] != it[1] }?.let { it[0] to it[1] }
}

/** How far back a chart looks and how finely. */
enum class ChartRange(
    val yahooInterval: String,
    val yahooRange: String,
    val windowMs: Long?,
    val binanceInterval: String,
    val binanceLimit: Int,
    val maxAgeMs: Long,
) {
    H1("1m", "1d", HOUR, "1m", 60, MINUTE),
    D1("5m", "5d", DAY, "15m", 96, 5 * MINUTE),
    W1("30m", "5d", null, "1h", 168, 20 * MINUTE),
    M1("1h", "1mo", null, "4h", 180, HOUR),
    Y1("1d", "1y", null, "1d", 365, 6 * HOUR),
    Y5("1wk", "5y", null, "1w", 260, 12 * HOUR),
}

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

/** Prices over time, oldest first. The change is against the first point of the period; [unit] is the price currency when known. */
class RateSeries(
    val times: List<Long>,
    val values: List<Double>,
    val source: String,
    val unit: String? = null,
    val fetchedAt: Long = System.currentTimeMillis(),
) {
    val last: Double get() = values.last()
    val first: Double get() = values.first()
    val change: Double get() = if (first != 0.0) last / first - 1 else 0.0
    val high: Double get() = values.max()
    val low: Double get() = values.min()

    /** The value at [time]: the last point at or before it. */
    fun at(time: Long): Double {
        var lo = 0
        var hi = times.lastIndex
        if (time <= times[0]) return values[0]
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (times[mid] <= time) lo = mid else hi = mid - 1
        }
        return values[lo]
    }
}

/**
 * Exchange rates and their history. Currencies and metals come from Yahoo Finance's public
 * chart data, coins from Binance's public market data; pairs that neither lists directly are
 * worked out through the US dollar. Nothing about the user is sent, only the pair.
 */
class RatesRepository(private val http: Http) {
    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, RateSeries>()
    private val _quotes = MutableStateFlow<Map<String, RateSeries>>(emptyMap())

    /** The last 24 hours of the favourite pairs shown on the home screen and the screensaver. */
    val quotes: StateFlow<Map<String, RateSeries>> = _quotes

    suspend fun refresh(ids: List<String>) {
        val fresh = coroutineScope { ids.map { id -> async { id to series(id, ChartRange.D1) } }.awaitAll() }
        _quotes.value = _quotes.value + fresh.mapNotNull { (id, s) -> s?.let { id to it } }
    }

    suspend fun series(id: String, range: ChartRange): RateSeries? {
        val key = "$id|$range"
        mutex.withLock {
            cache[key]?.let { if (System.currentTimeMillis() - it.fetchedAt < range.maxAgeMs) return it }
        }
        val loaded = runCatching { load(id, range) }.getOrNull()
        return mutex.withLock {
            if (loaded != null) {
                cache[key] = loaded
                // A handful of pairs and periods is all anyone looks at; keep memory small on a 1 GB TV.
                while (cache.size > 40) cache.remove(cache.keys.first())
            }
            loaded ?: cache[key]
        }
    }

    private suspend fun load(id: String, range: ChartRange): RateSeries? {
        if (RateCatalog.isStock(id)) return trim(yahoo(id.removePrefix(RateCatalog.STOCK_PREFIX), range), range)
        val (base, quote) = RateCatalog.split(id) ?: return null
        val asset = RateCatalog.asset(base)
        val series = when (asset?.kind ?: AssetKind.FIAT) {
            AssetKind.FIAT -> fiat(base, quote, range)
            AssetKind.CRYPTO -> inQuote(crypto(asset!!, range), quote, range)
            AssetKind.METAL -> inQuote(yahoo(asset!!.yahoo!!, range), quote, range)
            AssetKind.STOCK -> null
        } ?: return null
        return trim(RateSeries(series.times, series.values, series.source, quote), range)
    }

    private suspend fun fiat(base: String, quote: String, range: ChartRange): RateSeries? {
        runCatching { yahoo(if (base == "USD") "$quote=X" else "$base$quote=X", range) }.getOrNull()?.let { return it }
        val toQuote = usdTo(quote, range) ?: return null
        val toBase = usdTo(base, range) ?: return null
        return combine(toQuote, toBase) { q, b -> q / b }
    }

    /** A dollar price in [quote], using the dollar rate at each moment. */
    private suspend fun inQuote(usd: RateSeries?, quote: String, range: ChartRange): RateSeries? {
        usd ?: return null
        if (quote == "USD") return usd
        val rate = usdTo(quote, range) ?: return null
        return combine(usd, rate) { price, r -> price * r }
    }

    private suspend fun usdTo(code: String, range: ChartRange): RateSeries? =
        if (code == "USD") RateSeries(listOf(0L), listOf(1.0), "") else runCatching { yahoo("$code=X", range) }.getOrNull()

    private suspend fun crypto(asset: RateAsset, range: ChartRange): RateSeries? {
        asset.yahoo?.let { symbol -> runCatching { yahoo(symbol, range) }.getOrNull()?.let { return it } }
        return runCatching { binance("${asset.code}USDT", range) }.getOrNull()
            ?: runCatching { yahoo("${asset.code}-USD", range) }.getOrNull()
    }

    private suspend fun binance(symbol: String, range: ChartRange): RateSeries {
        val rows = http.getJson(
            "https://data-api.binance.vision/api/v3/klines?symbol=$symbol&interval=${range.binanceInterval}&limit=${range.binanceLimit}",
        ).jsonArray
        val times = rows.map { it.jsonArray[0].primitiveOrNull()!!.toLong() }
        val closes = rows.map { it.jsonArray[4].primitiveOrNull()!!.toDouble() }
        require(closes.size >= 2)
        // A halted market still answers, with old candles: treat that as no data.
        require(System.currentTimeMillis() - times.last() < 10 * DAY)
        val open = rows.first().jsonArray[1].primitiveOrNull()?.toDoubleOrNull()
        return RateSeries(listOf(times.first()) + times, listOf(open ?: closes.first()) + closes, "Binance")
    }

    private suspend fun yahoo(symbol: String, range: ChartRange): RateSeries {
        val root = http.getJson("https://query1.finance.yahoo.com/v8/finance/chart/$symbol?interval=${range.yahooInterval}&range=${range.yahooRange}").jsonObject
        val result = root["chart"]!!.jsonObject["result"]!!.jsonArray[0].jsonObject
        val stamps = result["timestamp"]!!.jsonArray.map { (it.primitiveOrNull()?.toLongOrNull() ?: 0L) * 1000 }
        val closes = result["indicators"]!!.jsonObject["quote"]!!.jsonArray[0].jsonObject["close"]!!.jsonArray
            .map { it.primitiveOrNull()?.toDoubleOrNull() }
        val points = stamps.zip(closes).filter { (_, v) -> v != null && v > 0 }
        require(points.size >= 2)
        val currency = result["meta"]?.jsonObject?.str("currency")
        return RateSeries(points.map { it.first }, points.map { it.second!! }, "Yahoo Finance", currency)
    }

    private fun combine(a: RateSeries, b: RateSeries, op: (Double, Double) -> Double): RateSeries =
        RateSeries(a.times, a.times.indices.map { op(a.values[it], b.at(a.times[it])) }, listOf(a.source, b.source).filter { it.isNotEmpty() }.distinct().joinToString(", "))

    /** Only the period asked for (counted back from the latest price, so a closed weekend market still shows), at most 300 points. */
    private fun trim(s: RateSeries, range: ChartRange): RateSeries? {
        var times = s.times
        var values = s.values
        range.windowMs?.let { window ->
            val from = times.last() - window
            val start = times.indexOfFirst { it >= from }.coerceAtLeast(0)
            times = times.drop(start)
            values = values.drop(start)
        }
        if (values.size < 2) return null
        if (values.size > MAX_POINTS) {
            val step = values.size.toDouble() / MAX_POINTS
            val keep = (0 until MAX_POINTS).map { (it * step).toInt() } + values.lastIndex
            times = keep.distinct().map { times[it] }
            values = keep.distinct().map { values[it] }
        }
        return RateSeries(times, values, s.source, s.unit)
    }

    private companion object {
        const val MAX_POINTS = 300
    }
}

/** The currency of the country the TV is set to, for default pairs. */
fun homeCurrency(): String = LocalInfoRepository.currencyOf(LocalInfoRepository.homeZone())?.currencyCode ?: "USD"

/** The user's pairs, or ready ones for their home currency. */
val LauncherSettings.rateIds: List<String> get() = favoriteRates ?: RateCatalog.defaults(homeCurrency())
