package com.sdai.news.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sdai.news.SDAINewsApp
import com.sdai.news.data.Article
import com.sdai.news.data.GeneralArticleRepository
import com.sdai.news.data.LanguageResolver
import com.sdai.news.data.PrefsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningReduce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Simplified navigation model:  **Mode → Region → Topic**.
 *  - Mode:   Video (default) or Text
 *  - Region: Global / National / Regional / Local
 *  - Topic:  a universal filter ("all", "politics", "sports", "weather", …)
 */
enum class FeedMode { VIDEO, TEXT }

enum class FeedRegion(val key: String, val label: String) {
    GLOBAL("world", "Global"),
    NATIONAL("national", "National"),
    REGIONAL("regional", "Regional"),
    LOCAL("local", "Local"),
}

data class FeedView(
    val mode: FeedMode = FeedMode.VIDEO,
    val region: FeedRegion = FeedRegion.GLOBAL,
    val topic: String = "all",
)

class UnifiedFeedViewModel(app: Application) : AndroidViewModel(app) {

    val repo = GeneralArticleRepository(app.applicationContext)
    private val prefs = SDAINewsApp.get().prefs
    private val locationProvider = com.sdai.news.data.LocationProvider(app.applicationContext)

    private val seenIds = repo.observeSeenIds()

    // Snapshots — rank without making the feed re-sort live as the user dwells.
    private var lastSeen: Set<String> = emptySet()
    private var lastAffinity: Map<String, Float> = emptyMap()
    private var lastBlockedSources: Set<String> = emptySet()
    private var lastPreferredTopics: Set<String> = emptySet()

    /** Feed plus an optional notice (e.g. "showing regional video" when Local
     *  video falls back). */
    private data class FeedResult(val articles: List<Article>, val notice: String?)

    private fun List<Article>.ranked(): List<Article> =
        FeedRanker.rank(this, lastSeen, lastAffinity, lastBlockedSources, lastPreferredTopics)

    // ── Navigation state ────────────────────────────────────────────────
    private val _mode = MutableStateFlow(FeedMode.VIDEO)
    private val _region = MutableStateFlow(FeedRegion.GLOBAL)
    private val _topic = MutableStateFlow("all")

    private val viewFlow = combine(_mode, _region, _topic) { m, r, t -> FeedView(m, r, t) }
    val view: StateFlow<FeedView> =
        viewFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedView())

    fun setMode(mode: FeedMode) { _mode.value = mode }
    fun setRegion(region: FeedRegion) { _region.value = region }
    fun setTopic(topic: String) { _topic.value = topic }

    /** Home = the default view: Video · Global · All. */
    fun goHome() {
        _mode.value = FeedMode.VIDEO
        _region.value = FeedRegion.GLOBAL
        _topic.value = "all"
    }

    // Per-section language gate (English / Regional / Both) + resolved code.
    private data class LangGate(
        val worldEn: Boolean, val worldReg: Boolean,
        val natEn: Boolean, val natReg: Boolean,
        val regEn: Boolean, val regReg: Boolean,
        val lang: String,
    )

    private val langGate: Flow<LangGate> =
        combine(
            prefs.worldEnglish, prefs.worldRegional,
            prefs.nationalEnglish, prefs.nationalRegional,
            prefs.regionalEnglish,
        ) { we, wr, ne, nr, re -> booleanArrayOf(we, wr, ne, nr, re) }
            .let { first5 ->
                combine(first5, prefs.regionalRegional, prefs.regionalLanguageCode) { a, rr, lang ->
                    LangGate(a[0], a[1], a[2], a[3], a[4], rr, lang)
                }
            }

    private data class FeedArgs(
        val view: FeedView,
        val positiveOnly: Boolean,
        val gate: LangGate,
        val blocked: Set<String>,
    )

    /** Map (region, topic) → the (section, category) pair to query. */
    private fun resolve(view: FeedView, positiveOnly: Boolean): Triple<Boolean, String?, String?> {
        val isVideo = view.mode == FeedMode.VIDEO
        // "Positive only" overrides TEXT with Good News (never Video — that would
        // empty the section).
        if (positiveOnly && !isVideo) return Triple(false, "good", null)
        // Region is applied strictly so switching Global↔National↔Regional↔Local
        // always reloads. Sparse region+topic combos (e.g. National×Anime) are
        // handled by an emptiness fallback in [feed], not by dropping the region.
        val (section, category) = when (view.topic) {
            "all" -> view.region.key to null
            "breaking" -> "breaking" to null            // breaking is its own global section
            "inspiration" -> null to "inspiration"      // inspiration spans sections
            "weather" -> view.region.key to "climate"
            else -> view.region.key to view.topic
        }
        return Triple(isVideo, section, category)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val feedResult: StateFlow<FeedResult> =
        combine(viewFlow, prefs.positiveOnly, langGate, prefs.blockedSources) { v, po, gate, blocked ->
            FeedArgs(v, po, gate, blocked)
        }
            // DataStore emits its whole snapshot on ANY write, so dwell-time
            // affinity/streak writes re-fire these flows with identical values.
            // distinctUntilChanged stops that from re-ranking the card the user
            // is on (title1 -> title2 after ~1s).
            .distinctUntilChanged()
            .flatMapLatest { args ->
                val (isVideo, section, category) = resolve(args.view, args.positiveOnly)
                // Only fall back for a REGION-SCOPED video query (section == the
                // region's own key). Cross-region topics — Breaking (section=
                // "breaking") and Inspiration (section=null) — must query directly,
                // else Local×Video×Breaking wrongly showed every regional video.
                val regionScoped = section == args.view.region.key
                val base = if (regionScoped && args.view.region != FeedRegion.GLOBAL) {
                    if (isVideo) videoFallback(args.view.region, category)
                    else textFallback(args.view.region, category, args.view, args.gate, args.blocked)
                } else {
                    repo.observeFeed(isVideo, section, category).map { list ->
                        val filtered = applyLangFilter(args.view, args.gate, list.filterNot { it.source in args.blocked })
                        FeedResult(filtered.ranked(), null)
                    }
                }
                // Stable session order: keep already-shown cards in place (drop any
                // that were removed/blocked), and append NEW items at the END — so
                // swiping FORWARD (up) keeps revealing more, instead of refreshes
                // inserting fresh items above the current card (which made the feed
                // grow backwards / swipe-down).
                base.runningReduce { prev, cur -> stableMerge(prev, cur) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedResult(emptyList(), null))

    val feed: StateFlow<List<Article>> = feedResult
        .map { it.articles }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Video feed for Shorts and the Home "AI Daily Brief" row.
     *  Reacts to the active region/topic chip — Local uses the same
     *  videoFallback cascade (local → regional → national → global) so
     *  local videos always surface when available. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val feedVideos: StateFlow<List<Article>> = viewFlow
        .flatMapLatest { view ->
            val category = when (view.topic) {
                "all", "breaking", "inspiration" -> null
                else -> view.topic
            }
            if (view.region != FeedRegion.GLOBAL) {
                videoFallback(view.region, category).map { it.articles }
            } else {
                repo.observeFeed(isVideo = true, section = "world", category = category)
                    .map { list -> list.filterNot { it.source in lastBlockedSources }.ranked() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Non-null when the current feed is a fallback (e.g. Local video → regional). */
    val fallbackNotice: StateFlow<String?> = feedResult
        .map { it.notice }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Merge a fresh feed emission into the displayed order: keep shown cards (in
     *  order, dropping any now-removed/blocked), append new ones at the end. This
     *  keeps the feed stable and growing FORWARD instead of re-ranking each time. */
    private fun stableMerge(prev: FeedResult, cur: FeedResult): FeedResult {
        if (prev.articles.isEmpty()) return cur
        val curById = cur.articles.associateBy { it.id }
        val kept = prev.articles.mapNotNull { curById[it.id] }      // shown order, fresh data, drop removed
        val keptIds = kept.mapTo(HashSet()) { it.id }
        val appended = cur.articles.filter { it.id !in keptIds }     // genuinely new -> at the end
        return FeedResult(kept + appended, cur.notice)
    }

    /** Video for a sub-global region: show that region's video if present, else
     *  fall back to the next broader tier(s), labelled. The selected region is
     *  tried first with a null label (no notice when it actually has content). */
    private fun videoFallback(region: FeedRegion, category: String?): Flow<FeedResult> {
        // (tier, notice-label) — null label = the selected region, no notice.
        val tiers: List<Pair<FeedRegion, String?>> = when (region) {
            FeedRegion.LOCAL -> listOf(
                FeedRegion.LOCAL to null, FeedRegion.REGIONAL to "regional",
                FeedRegion.NATIONAL to "national", FeedRegion.GLOBAL to "global",
            )
            FeedRegion.REGIONAL -> listOf(
                FeedRegion.REGIONAL to null, FeedRegion.NATIONAL to "national", FeedRegion.GLOBAL to "global",
            )
            FeedRegion.NATIONAL -> listOf(
                FeedRegion.NATIONAL to null, FeedRegion.GLOBAL to "global",
            )
            FeedRegion.GLOBAL -> listOf(FeedRegion.GLOBAL to null)
        }
        val flows = tiers.map { (r, label) ->
            repo.observeFeed(isVideo = true, section = r.key, category = category)
                .map { list -> list.filterNot { it.source in lastBlockedSources } to label }
        }
        return combine(flows) { arr ->
            val hit = arr.firstOrNull { it.first.isNotEmpty() }
            when {
                hit == null -> FeedResult(emptyList(), null)
                hit.second == null -> FeedResult(hit.first.ranked(), null)               // selected region had content
                else -> FeedResult(hit.first.ranked(), "No ${region.label.lowercase()} videos — showing ${hit.second}")
            }
        }
    }

    /** Text fallback mirror of videoFallback: cascades local → regional → national → global
     *  so the headlines section always shows something even when local articles are sparse. */
    private fun textFallback(
        region: FeedRegion,
        category: String?,
        view: FeedView,
        gate: LangGate,
        blocked: Set<String>,
    ): Flow<FeedResult> {
        val tiers: List<Pair<FeedRegion, String?>> = when (region) {
            FeedRegion.LOCAL -> listOf(
                FeedRegion.LOCAL to null, FeedRegion.REGIONAL to "regional",
                FeedRegion.NATIONAL to "national", FeedRegion.GLOBAL to "global",
            )
            FeedRegion.REGIONAL -> listOf(
                FeedRegion.REGIONAL to null, FeedRegion.NATIONAL to "national", FeedRegion.GLOBAL to "global",
            )
            FeedRegion.NATIONAL -> listOf(
                FeedRegion.NATIONAL to null, FeedRegion.GLOBAL to "global",
            )
            FeedRegion.GLOBAL -> listOf(FeedRegion.GLOBAL to null)
        }
        val flows = tiers.map { (r, label) ->
            repo.observeFeed(isVideo = false, section = r.key, category = category)
                .map { list ->
                    val filtered = applyLangFilter(
                        view.copy(region = r),
                        gate,
                        list.filterNot { it.source in blocked },
                    )
                    filtered to label
                }
        }
        return combine(flows) { arr ->
            val hit = arr.firstOrNull { it.first.isNotEmpty() }
            when {
                hit == null -> FeedResult(emptyList(), null)
                hit.second == null -> FeedResult(hit.first.ranked(), null)
                else -> FeedResult(hit.first.ranked(), "No ${region.label.lowercase()} headlines — showing ${hit.second}")
            }
        }
    }

    /** Honour per-section language toggles for the regional editions. */
    private fun applyLangFilter(view: FeedView, gate: LangGate, list: List<Article>): List<Article> {
        if (gate.lang.isBlank()) return list
        fun allowed(lang: String, en: Boolean, reg: Boolean) =
            (en && lang == "en") || (reg && lang == gate.lang)
        return when (view.region) {
            FeedRegion.GLOBAL -> list.filter { allowed(it.lang, gate.worldEn, gate.worldReg) }
            FeedRegion.NATIONAL -> list.filter { allowed(it.lang, gate.natEn, gate.natReg) }
            FeedRegion.REGIONAL -> list.filter { allowed(it.lang, gate.regEn, gate.regReg) }
            FeedRegion.LOCAL -> list   // city news — shown as-is
        }
    }

    // Habit loop
    val streakCurrent = prefs.streakCurrent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val todayReadCount = prefs.todayReadCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val dailyGoal = PrefsStore.DAILY_GOAL

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        refreshAll()
        viewModelScope.launch { seenIds.collect { lastSeen = it } }
        viewModelScope.launch { prefs.affinity.collect { lastAffinity = it } }
        viewModelScope.launch { prefs.blockedSources.collect { lastBlockedSources = it } }
        viewModelScope.launch { prefs.preferredTopics.collect { lastPreferredTopics = it } }
    }

    fun refreshAll() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            runCatching { repo.refreshGeneral() }
            _isRefreshing.value = false
            com.sdai.news.ai.SummaryGenerator.get(getApplication()).generateSummariesForNewArticles()
        }
        // National + regional only when a location is shared.
        viewModelScope.launch {
            var cc = prefs.locationCountryCode.first()
            var cname = prefs.locationCountry.first()
            var region = prefs.locationRegion.first()
            var city = prefs.locationCity.first()
            var langCode = prefs.regionalLanguageCode.first()
            var langName = prefs.regionalLanguageName.first()

            // Manual entry like "Vizag" (no state/country) starves Local sources
            // and the regional language. Forward-geocode it once to fill in the
            // proper city + state + country (-> Visakhapatnam, Andhra Pradesh, IN,
            // Telugu) so Local/Regional/National all resolve.
            if (region.isBlank() && city.isNotBlank()) {
                val geo = runCatching { locationProvider.forwardGeocode(city) }.getOrNull()
                if (geo != null && (geo.region.isNotBlank() || geo.countryCode.isNotBlank())) {
                    prefs.setLocation(geo)
                    if (geo.city.isNotBlank()) city = geo.city
                    region = geo.region.ifBlank { region }
                    if (geo.countryCode.isNotBlank()) cc = geo.countryCode.uppercase()
                    if (geo.country.isNotBlank()) cname = geo.country
                    // Re-resolve language now the state is known (overrides a
                    // stale English default from when the state was blank).
                    val lang = LanguageResolver.mostSpoken(cc, region, cname)
                    prefs.setRegionalLanguage(lang.code, lang.name)
                    langCode = lang.code
                    langName = lang.name
                }
            }
            // Country fallback to the device region when still unknown, so
            // National always works (refreshNational needs a country code).
            if (cc.isBlank()) {
                cc = java.util.Locale.getDefault().country.uppercase()
                if (cname.isBlank() && cc.isNotBlank()) {
                    cname = java.util.Locale("", cc).displayCountry
                }
            }
            if (langCode.isBlank() && (region.isNotBlank() || cc.isNotBlank())) {
                val lang = LanguageResolver.mostSpoken(cc, region, cname)
                prefs.setRegionalLanguage(lang.code, lang.name)
                langCode = lang.code
                langName = lang.name
            }
            val worldReg = prefs.worldRegional.first()
            val natEn = prefs.nationalEnglish.first()
            val natReg = prefs.nationalRegional.first()
            val regEn = prefs.regionalEnglish.first()
            val regReg = prefs.regionalRegional.first()

            if (worldReg && langCode.isNotBlank()) runCatching { repo.refreshWorldRegional(langCode, cc) }
            if (cc.isNotBlank() && natEn) runCatching { repo.refreshNational(cc, cname) }
            if (cc.isNotBlank() && natReg && langCode.isNotBlank()) {
                runCatching { repo.refreshNationalRegional(cc, langCode, langName) }
            }
            if (city.isNotBlank() || langCode.isNotBlank()) {
                runCatching { repo.refreshRegional(city, region, cc, langCode, langName, regEn, regReg) }
            }
        }
    }

    /** Add a source to the block list — it disappears from the feed at once. */
    fun blockSource(source: String) {
        if (source.isBlank()) return
        viewModelScope.launch { prefs.addBlockedSource(source) }
    }

    fun onArticleSeen(article: Article) {
        if (article.id in lastSeen) return
        viewModelScope.launch {
            repo.markSeen(article.id)
            prefs.addAffinity(affinityKeys(article), DWELL_POINTS)
            prefs.recordRead()
            com.sdai.news.analytics.AnalyticsManager.get(getApplication()).trackArticleRead()
            if (!article.category.isNullOrBlank()) {
                com.sdai.news.analytics.AnalyticsManager.get(getApplication()).trackCategoryView(article.category)
            }
        }
    }

    fun onArticleOpened(article: Article) {
        viewModelScope.launch { prefs.addAffinity(affinityKeys(article), OPEN_POINTS) }
    }

    fun onArticleShared(article: Article) {
        viewModelScope.launch { prefs.addAffinity(affinityKeys(article), SHARE_POINTS) }
    }

    private fun affinityKeys(a: Article): List<String> = listOfNotNull(
        a.category?.takeIf { it.isNotBlank() }?.let { "cat:$it" },
        a.source.takeIf { it.isNotBlank() }?.let { "src:$it" },
        a.tier?.takeIf { it.isNotBlank() }?.let { "tier:$it" },
    )

    companion object {
        private const val DWELL_POINTS = 1f
        private const val OPEN_POINTS = 3f
        private const val SHARE_POINTS = 3f
    }
}
