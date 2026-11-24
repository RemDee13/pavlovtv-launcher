package io.github.remdee13.pavlovtv.data

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.Collator

data class AppEntry(
    /** Stable key used in settings (favorites, hidden). One entry per package. */
    val key: String,
    val packageName: String,
    val component: ComponentName,
    val label: String,
    /** Has a LEANBACK_LAUNCHER activity, i.e. built for TV. */
    val isTv: Boolean,
    val isSystem: Boolean,
)

class AppRepository(private val context: Context, private val scope: CoroutineScope) {
    private val pm = context.packageManager
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps
    private var pending: Job? = null

    init {
        reload()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) = reload()
            },
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun reload() {
        pending?.cancel()
        pending = scope.launch(Dispatchers.IO) {
            delay(150) // package broadcasts come in bursts
            runCatching { load() }.onSuccess { _apps.value = it }
        }
    }

    private fun load(): List<AppEntry> {
        val tv = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
        val phone = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val byPackage = LinkedHashMap<String, AppEntry>()
        fun add(info: ResolveInfo, isTv: Boolean) {
            val activity = info.activityInfo ?: return
            val pkg = activity.packageName
            if (pkg == context.packageName || pkg.startsWith("io.github.remdee13.pavlovtv")) return
            if (byPackage.containsKey(pkg)) return
            byPackage[pkg] = AppEntry(
                key = pkg,
                packageName = pkg,
                component = ComponentName(pkg, activity.name),
                label = info.loadLabel(pm).toString().trim().ifEmpty { pkg },
                isTv = isTv,
                isSystem = (activity.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            )
        }
        tv.forEach { add(it, isTv = true) }
        phone.forEach { add(it, isTv = false) }
        val collator = Collator.getInstance()
        return byPackage.values.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    fun launch(activity: Activity, app: AppEntry): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(if (app.isTv) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching { activity.startActivity(intent) }.isSuccess || run {
            val fallback = pm.getLeanbackLaunchIntentForPackage(app.packageName) ?: pm.getLaunchIntentForPackage(app.packageName)
            fallback != null && runCatching { activity.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
    }

    fun openAppInfo(activity: Activity, app: AppEntry) {
        runCatching {
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun uninstall(activity: Activity, app: AppEntry) {
        runCatching {
            @Suppress("DEPRECATION")
            activity.startActivity(
                Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.fromParts("package", app.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
