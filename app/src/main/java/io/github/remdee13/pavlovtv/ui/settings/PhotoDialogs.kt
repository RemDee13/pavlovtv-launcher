package io.github.remdee13.pavlovtv.ui.settings

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.remdee13.pavlovtv.R
import io.github.remdee13.pavlovtv.graph
import io.github.remdee13.pavlovtv.ui.components.GlassButton
import io.github.remdee13.pavlovtv.ui.components.GlassCard
import io.github.remdee13.pavlovtv.ui.components.GlassDialog
import io.github.remdee13.pavlovtv.ui.components.QrCode
import io.github.remdee13.pavlovtv.ui.theme.Palette
import io.github.remdee13.pavlovtv.ui.theme.Type
import io.github.remdee13.pavlovtv.wallpaper.UploadPageText
import io.github.remdee13.pavlovtv.wallpaper.UploadServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Shows a QR code; the phone opens it and sends pictures straight to the TV. */
@Composable
fun UploadDialog(onDismiss: () -> Unit, onUploaded: (File) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var received by remember { mutableStateOf(0) }
    DisposableEffect(Unit) {
        val text = UploadPageText(
            title = context.getString(R.string.upload_page_title),
            hint = context.getString(R.string.upload_page_hint),
            choose = context.getString(R.string.upload_page_choose),
            sending = context.getString(R.string.upload_page_sending),
            done = context.getString(R.string.upload_page_done),
            failed = context.getString(R.string.upload_page_failed),
        )
        val server = UploadServer(context.graph.photos, text) { file ->
            scope.launch {
                received++
                onUploaded(file)
            }
        }
        address = runCatching { server.start(scope) }.getOrNull()
        failed = address == null
        onDispose { server.stop() }
    }
    GlassDialog(onDismiss = onDismiss, modifier = Modifier.width(620.dp)) {
        Text(stringResource(R.string.upload_title), style = Type.title)
        val url = address
        if (url == null) {
            Text(stringResource(if (failed) R.string.upload_no_network else R.string.weather_loading), style = Type.body, color = Palette.TextSecondary)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QrCode(url, Modifier.size(180.dp))
                Spacer(Modifier.width(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.upload_step1), style = Type.body)
                    Text(stringResource(R.string.upload_step2), style = Type.body)
                    Text(stringResource(R.string.upload_step3), style = Type.body)
                    Spacer(Modifier.height(4.dp))
                    Text(url, style = Type.small, color = Palette.Accent)
                    if (received > 0) Text(stringResource(R.string.upload_received, received), style = Type.small, color = Palette.Warm)
                }
            }
        }
        GlassButton(stringResource(R.string.action_done), onDismiss, accent = true)
    }
}

private data class DeviceImage(val uri: Uri, val rotation: Int)

private suspend fun queryImages(context: Context): List<DeviceImage> = withContext(Dispatchers.IO) {
    val volumes: Collection<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.getExternalVolumeNames(context) else listOf("external")
    volumes.flatMap { volume ->
        val collection = MediaStore.Images.Media.getContentUri(volume)
        val result = mutableListOf<Pair<Long, DeviceImage>>()
        runCatching {
            context.contentResolver.query(collection, arrayOf("_id", "orientation", "date_modified"), null, null, "date_modified DESC")?.use { c ->
                while (c.moveToNext() && result.size < 150) {
                    result += c.getLong(2) to DeviceImage(ContentUris.withAppendedId(collection, c.getLong(0)), if (c.isNull(1)) 0 else c.getInt(1))
                }
            }
        }
        result
    }.sortedByDescending { it.first }.map { it.second }.take(150)
}

private fun storagePermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

/** Pick a picture from TV storage or a USB drive. */
@Composable
fun StorageImportDialog(onDismiss: () -> Unit, onImported: (File) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, storagePermission()) == PackageManager.PERMISSION_GRANTED) }
    var images by remember { mutableStateOf<List<DeviceImage>?>(null) }
    var importing by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(granted) {
        if (granted) images = queryImages(context) else launcher.launch(storagePermission())
    }
    GlassDialog(onDismiss = onDismiss, modifier = Modifier.width(760.dp).height(470.dp)) {
        Text(stringResource(R.string.storage_title), style = Type.title)
        val list = images
        when {
            !granted -> {
                Text(stringResource(R.string.storage_permission), style = Type.body, color = Palette.TextSecondary)
                GlassButton(stringResource(R.string.recs_permission_button), { launcher.launch(storagePermission()) }, accent = true)
            }
            list == null -> Text(stringResource(R.string.weather_loading), style = Type.body, color = Palette.TextSecondary)
            list.isEmpty() -> Text(stringResource(R.string.storage_empty), style = Type.body, color = Palette.TextSecondary)
            importing -> Text(stringResource(R.string.storage_importing), style = Type.body, color = Palette.TextSecondary)
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(6.dp),
            ) {
                items(list, key = { it.uri.toString() }) { image ->
                    GlassCard(
                        onClick = {
                            importing = true
                            scope.launch {
                                val file = context.graph.photos.importUri(image.uri, image.rotation)
                                importing = false
                                if (file != null) {
                                    onImported(file)
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, R.string.storage_failed, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.size(124.dp, 70.dp),
                        shape = RoundedCornerShape(12.dp),
                        focusedScale = 1.1f,
                        rimOnTop = true,
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(image.uri).size(248, 140).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.05f)),
                        )
                    }
                }
            }
        }
    }
}
