package io.github.remdee13.pavlovtv.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.tv.TvContract
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

data class RecProgram(
    val id: Long,
    val title: String,
    val image: String?,
    /** width / height */
    val aspect: Float,
    val intentUri: String?,
    val packageName: String,
)

data class RecRow(
    val id: Long,
    val packageName: String,
    val title: String,
    val appLink: String?,
    val programs: List<RecProgram>,
    val isWatchNext: Boolean = false,
)

enum class RecStatus { UNKNOWN, OK, EMPTY, NO_PERMISSION, UNSUPPORTED }

/**
 * Reads "preview channels" and the "Watch next" list that apps publish to the
 * system TV provider (YouTube, Netflix, Prime Video, Plex and so on).
 *
 * A sideloaded launcher only sees other apps' rows when it holds READ_TV_LISTINGS.
 * The provider also refuses WHERE clauses from such apps, so every query reads the
 * whole table and filters in code.
 */
class RecommendationsRepository(private val context: Context) {
    private val _rows = MutableStateFlow<List<RecRow>>(emptyList())
    val rows: StateFlow<List<RecRow>> = _rows
    private val _status = MutableStateFlow(RecStatus.UNKNOWN)
    val status: StateFlow<RecStatus> = _status
    private var lastRefresh = 0L

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    suspend fun refresh(force: Boolean = false) = withContext(Dispatchers.IO) {
        if (!force && System.currentTimeMillis() - lastRefresh < 60_000) return@withContext
        lastRefresh = System.currentTimeMillis()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            _status.value = RecStatus.UNSUPPORTED
            return@withContext
        }
        try {
            val rows = buildList {
                watchNext()?.let(::add)
                addAll(previewChannels())
            }
            _rows.value = rows
            _status.value = when {
                rows.isNotEmpty() -> RecStatus.OK
                !hasPermission() -> RecStatus.NO_PERMISSION
                else -> RecStatus.EMPTY
            }
        } catch (e: SecurityException) {
            _rows.value = emptyList()
            _status.value = RecStatus.NO_PERMISSION
        } catch (e: Exception) {
            _rows.value = emptyList()
            _status.value = RecStatus.UNSUPPORTED
        }
    }

    private fun query(uri: Uri, projection: Array<String>): Cursor? =
        context.contentResolver.query(uri, projection, null, null, null)

    private fun previewChannels(): List<RecRow> {
        data class Channel(val id: Long, val pkg: String, val name: String, val appLink: String?)
        val channels = LinkedHashMap<Long, Channel>()
        query(TvContract.Channels.CONTENT_URI, arrayOf("_id", "package_name", "display_name", "type", "app_link_intent_uri"))?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(3) != TvContract.Channels.TYPE_PREVIEW) continue
                val pkg = c.getString(1) ?: continue
                channels[c.getLong(0)] = Channel(c.getLong(0), pkg, c.getString(2).orEmpty(), c.getString(4))
            }
        }
        if (channels.isEmpty()) return emptyList()

        val programsByChannel = HashMap<Long, MutableList<Pair<Int, RecProgram>>>()
        val projection = arrayOf(
            "_id", "channel_id", "title", "poster_art_uri", "thumbnail_uri",
            "poster_art_aspect_ratio", "thumbnail_aspect_ratio", "intent_uri", "weight", "package_name",
        )
        query(TvContract.PreviewPrograms.CONTENT_URI, projection)?.use { c ->
            while (c.moveToNext()) {
                val channelId = c.getLong(1)
                val channel = channels[channelId] ?: continue
                val poster = c.getString(3)
                val thumb = c.getString(4)
                val image = poster ?: thumb
                val ratioColumn = if (poster != null) 5 else 6
                val program = RecProgram(
                    id = c.getLong(0),
                    title = c.getString(2).orEmpty(),
                    image = image,
                    aspect = aspectFor(if (c.isNull(ratioColumn)) -1 else c.getInt(ratioColumn)),
                    intentUri = c.getString(7),
                    packageName = c.getString(9) ?: channel.pkg,
                )
                programsByChannel.getOrPut(channelId) { mutableListOf() } += (if (c.isNull(8)) 0 else c.getInt(8)) to program
            }
        }
        return channels.values.mapNotNull { channel ->
            val programs = programsByChannel[channel.id]?.sortedByDescending { it.first }?.map { it.second }
            if (programs.isNullOrEmpty()) null
            else RecRow(channel.id, channel.pkg, channel.name, channel.appLink, programs.take(MAX_PER_ROW))
        }
    }

    private fun watchNext(): RecRow? {
        val projection = arrayOf(
            "_id", "title", "poster_art_uri", "thumbnail_uri", "poster_art_aspect_ratio",
            "thumbnail_aspect_ratio", "intent_uri", "last_engagement_time_utc_millis", "package_name",
        )
        val items = mutableListOf<Pair<Long, RecProgram>>()
        query(TvContract.WatchNextPrograms.CONTENT_URI, projection)?.use { c ->
            while (c.moveToNext()) {
                val thumb = c.getString(3)
                val poster = c.getString(2)
                // Watch next cards look best as 16:9 stills.
                val image = thumb ?: poster
                val ratioColumn = if (thumb != null) 5 else 4
                items += (if (c.isNull(7)) 0L else c.getLong(7)) to RecProgram(
                    id = c.getLong(0),
                    title = c.getString(1).orEmpty(),
                    image = image,
                    aspect = aspectFor(if (c.isNull(ratioColumn)) -1 else c.getInt(ratioColumn)),
                    intentUri = c.getString(6),
                    packageName = c.getString(8).orEmpty(),
                )
            }
        }
        if (items.isEmpty()) return null
        return RecRow(WATCH_NEXT_ID, "", "", null, items.sortedByDescending { it.first }.map { it.second }.take(MAX_PER_ROW), isWatchNext = true)
    }

    fun open(activity: Activity, program: RecProgram): Boolean {
        val uri = program.intentUri ?: return launchPackage(activity, program.packageName)
        return runCatching {
            val intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
        }.isSuccess || launchPackage(activity, program.packageName)
    }

    private fun launchPackage(activity: Activity, pkg: String): Boolean {
        val pm = activity.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        return runCatching { activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    companion object {
        const val PERMISSION = "android.permission.READ_TV_LISTINGS"
        const val WATCH_NEXT_ID = -1L
        private const val MAX_PER_ROW = 20

        /** TvContract ASPECT_RATIO_* constants -> width / height. */
        fun aspectFor(constant: Int): Float = when (constant) {
            0 -> 16f / 9f
            1 -> 3f / 2f
            2 -> 4f / 3f
            3 -> 1f
            4 -> 2f / 3f
            5 -> 1f / 1.441f
            6 -> 3f / 4f
            else -> 16f / 9f
        }
    }
}
