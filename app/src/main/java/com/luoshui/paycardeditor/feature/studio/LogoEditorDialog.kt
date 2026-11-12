package com.luoshui.paycardeditor.feature.studio

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luoshui.paycardeditor.R
import com.luoshui.paycardeditor.data.CardAsset
import com.luoshui.paycardeditor.data.CardAssetRepository
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun LogoEditorDialog(
    asset: CardAsset,
    saving: Boolean,
    onSave: (File, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var savedLayers by rememberSaveable(asset.id) { mutableStateOf("[]") }
    var restored by remember { mutableStateOf(false) }
    var base by remember(asset.id) { mutableStateOf<Bitmap?>(null) }
    var layers by remember(asset.id) { mutableStateOf<List<LogoLayer>>(emptyList()) }
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable(asset.id) { mutableStateOf(asset.displayName.take(70) + "（添加标识）") }
    var processing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    val busy = processing || saving
    val imageError = stringResource(R.string.logo_load_failed)
    val saveError = stringResource(R.string.error_save_asset_failed)
    LaunchedEffect(asset.id) {
        try {
            val loaded = withContext(Dispatchers.IO) {
                val image = LogoRepository.decodeFile(File(asset.absolutePath), 4096)
                val repository = LogoRepository(context)
                val resources = repository.list().associateBy { it.id }
                val array = org.json.JSONArray(savedLayers)
                val restoredLayers = (0 until array.length()).map { index ->
                    val item = array.getJSONObject(index)
                    val resource = requireNotNull(resources[item.getString("id")])
                    val logo = repository.load(resource)
                    LogoLayer(resource, logo, LogoPlacement(item.getDouble("x").toFloat(), item.getDouble("y").toFloat(),
                        item.getDouble("width").toFloat(), item.getDouble("opacity").toFloat())
                        .bounded(image.width.toFloat() / image.height, logo.width.toFloat() / logo.height))
                }
                image to restoredLayers
            }
            base = loaded.first
            layers = loaded.second
            restored = true
        }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = imageError }
    }
    SideEffect {
        if (restored) {
            val array = org.json.JSONArray()
            layers.forEach { layer -> array.put(org.json.JSONObject().put("id", layer.resource.id)
                .put("x", layer.placement.x).put("y", layer.placement.y)
                .put("width", layer.placement.width).put("opacity", layer.placement.opacity)) }
            savedLayers = array.toString()
        }
    }
    // Use the latest placements inside the long-lived gesture detector.
    val latestLayers by rememberUpdatedState(layers)
    val latestBusy by rememberUpdatedState(busy)
    val latestSelected by rememberUpdatedState(selected)
    fun moveSelected(pan: Offset = Offset.Zero, zoom: Float = 1f) {
        val image = base ?: return
        val index = latestSelected
        val current = latestLayers.getOrNull(index) ?: return
        if (latestBusy || previewSize.width == 0 || previewSize.height == 0) return
        val next = current.placement.copy(x = current.placement.x + pan.x / previewSize.width,
            y = current.placement.y + pan.y / previewSize.height, width = current.placement.width * zoom)
            .bounded(image.width.toFloat() / image.height, current.bitmap.width.toFloat() / current.bitmap.height)
        layers = latestLayers.toMutableList().also { it[index] = current.copy(placement = next) }
    }
    fun saveCard() {
        naming = false
        processing = true
        error = null
        scope.launch {
            var output: File? = null
            try {
                output = withContext(Dispatchers.IO) {
                    val file = CardAssetRepository.createTempFile("logo_card_")
                    try { LogoComposer.write(requireNotNull(base), layers, file); file }
                    catch (e: Exception) { file.delete(); throw e }
                }
                onSave(requireNotNull(output), name.trim())
                output = null // ownership passes to ViewModel, which deletes the temporary file
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = saveError }
            finally { output?.delete(); processing = false }
        }

    }
    Dialog(onDismissRequest = { if (!busy) { if (naming) naming = false else onDismiss() } },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        if (naming) Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(24.dp), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.logo_save_new), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.logo_card_name)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, isError = name.isBlank(), enabled = !busy)
                Text(stringResource(R.string.logo_save_help), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { naming = false }, enabled = !busy) { Text(stringResource(android.R.string.cancel)) }
                    TextButton(onClick = ::saveCard, enabled = !busy && name.isNotBlank()) { Text(stringResource(R.string.logo_save)) }
                }
            }
        } else Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(android.R.string.cancel)) }
                    TextButton(onClick = { naming = true }, enabled = !busy && base != null && layers.isNotEmpty()) {
                        Text(stringResource(R.string.logo_save_new))
                    }
                }
                Text(stringResource(R.string.logo_editor_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.logo_editor_help), style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (base == null && error == null || busy) Text(stringResource(R.string.logo_processing))
                val image = base
                if (image != null) {
                    val aspect = image.width.toFloat() / image.height
                    val nativePaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG) }
                    Box(Modifier.fillMaxWidth().aspectRatio(aspect).onSizeChanged { previewSize = it }
                        .pointerInput(asset.id) {
                            detectTapGestures { tap ->
                                if (!latestBusy) {
                                    val found = latestLayers.indexOfLast { layer ->
                                        val p = layer.placement
                                        val w = p.width * previewSize.width
                                        val h = w * layer.bitmap.height / layer.bitmap.width
                                        kotlin.math.abs(tap.x - p.x * previewSize.width) <= w / 2 &&
                                            kotlin.math.abs(tap.y - p.y * previewSize.height) <= h / 2
                                    }
                                    if (found >= 0) selected = found
                                }
                            }
                        }
                        .pointerInput(asset.id) { detectTransformGestures { _, pan, zoom, _ -> moveSelected(pan, zoom) } }) {
                        Checkerboard(Modifier.matchParentSize())
                        Canvas(Modifier.matchParentSize()) {
                            drawImage(image.asImageBitmap(), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                            layers.forEachIndexed { index, layer ->
                                val p = layer.placement
                                val width = p.width * size.width
                                val height = width * layer.bitmap.height / layer.bitmap.width
                                val left = p.x * size.width - width / 2
                                val top = p.y * size.height - height / 2
                                drawIntoCanvas { canvas ->
                                    nativePaint.alpha = (p.opacity * 255).toInt()
                                    canvas.nativeCanvas.drawBitmap(layer.bitmap, null, android.graphics.RectF(left, top, left + width, top + height), nativePaint)
                                }
                                if (index == selected) drawRect(Color(0xFF3388FF), Offset(left, top), Size(width, height), style = Stroke(2.dp.toPx()))
                            }
                        }
                    }
                    Button(onClick = { picking = true }, enabled = !busy && layers.size < 12) { Text(stringResource(R.string.logo_add)) }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(layers) { index, layer ->
                            FilterChip(selected == index, { selected = index }, label = { Text(layer.resource.name) }, enabled = !busy)
                        }
                    }
                    val layer = layers.getOrNull(selected)
                    if (layer != null) {
                        if (layer.resource.note.isNotEmpty()) Text(layer.resource.note, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.logo_size))
                        val maxWidth = minOf(0.95f, 0.95f * layer.bitmap.width / layer.bitmap.height / aspect)
                        Slider(layer.placement.width, { width ->
                            val next = layer.placement.copy(width = width).bounded(aspect, layer.bitmap.width.toFloat() / layer.bitmap.height)
                            layers = layers.toMutableList().also { it[selected] = layer.copy(placement = next) }
                        }, valueRange = minOf(0.03f, maxWidth)..maxWidth, enabled = !busy)
                        Text(stringResource(R.string.logo_opacity, (layer.placement.opacity * 100).toInt()))
                        Slider(layer.placement.opacity, { alpha ->
                            layers = layers.toMutableList().also { it[selected] = layer.copy(placement = layer.placement.copy(opacity = alpha)) }
                        }, enabled = !busy)
                        Row {
                            TextButton(onClick = {
                                layers = layers.toMutableList().also { it[selected] = layer.copy(placement = LogoPlacement().bounded(aspect, layer.bitmap.width.toFloat() / layer.bitmap.height)) }
                            }, enabled = !busy) { Text(stringResource(R.string.logo_reset)) }
                            TextButton(onClick = {
                                layers = layers.filterIndexed { index, _ -> index != selected }
                                selected = layers.lastIndex
                            }, enabled = !busy) { Text(stringResource(R.string.logo_remove)) }
                        }
                    }
                }
            }
        }
    }
    if (picking) LogoPickerDialog(onPick = { resource ->
        picking = false
        processing = true
        error = null
        scope.launch {
            try {
                val logo = withContext(Dispatchers.IO) { LogoRepository(context).load(resource) }
                val image = requireNotNull(base)
                layers = layers + LogoLayer(resource, logo, LogoPlacement().bounded(image.width.toFloat() / image.height, logo.width.toFloat() / logo.height))
                selected = layers.lastIndex
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = imageError }
            finally { processing = false }
        }
    }, onDismiss = { picking = false })
}
