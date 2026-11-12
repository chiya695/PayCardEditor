package com.luoshui.paycardeditor.feature.studio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.luoshui.paycardeditor.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun LogoPickerDialog(onPick: (LogoResource) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { LogoRepository(context) }
    val scope = rememberCoroutineScope()
    var resources by remember { mutableStateOf<List<LogoResource>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf("全部") }
    var query by rememberSaveable { mutableStateOf("") }
    val loadError = stringResource(R.string.logo_load_failed)
    val importError = stringResource(R.string.logo_import_failed)
    LaunchedEffect(repository) {
        try { resources = withContext(Dispatchers.IO) { repository.list() } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = loadError }
        finally { loading = false }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            error = null
            try {
                val imported = withContext(Dispatchers.IO) { repository.import(uri) }
                onPick(imported)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = importError }
            finally { importing = false }
        }
    }
    Dialog(onDismissRequest = { if (!importing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.logo_choose_title), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss, enabled = !importing) { Text(stringResource(android.R.string.cancel)) }
                }
                TextButton(onClick = { picker.launch(arrayOf("image/*")) }, enabled = !importing && !loading) {
                    Text(stringResource(if (importing) R.string.logo_importing else R.string.logo_import))
                }
                OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.logo_search)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !importing)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("全部", "我的 Logo", "金融", "交通卡", "街机卡", "其他标识", "待确认")) { label ->
                        FilterChip(selected = category == label, onClick = { category = label }, label = { Text(label) }, enabled = !importing)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (loading || importing) Text(stringResource(R.string.logo_processing))
                val filtered = resources.filter { (category == "全部" || it.category == category) &&
                    (it.name.contains(query.trim(), ignoreCase = true) || it.group.contains(query.trim(), ignoreCase = true)) }
                if (!loading && !importing && filtered.isEmpty()) Text(stringResource(R.string.logo_empty))
                LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { logo ->
                        Column(Modifier.fillMaxWidth().clickable(enabled = !importing) { onPick(logo) }.padding(4.dp)) {
                            Box(Modifier.fillMaxWidth().height(72.dp)) {
                                Checkerboard(Modifier.matchParentSize())
                                AsyncImage(model = logo.imageModel, contentDescription = logo.name, modifier = Modifier.fillMaxSize().padding(4.dp))
                            }
                            Text(logo.name, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            Text(logo.group.ifBlank { logo.category }, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Checkerboard(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cell = 12.dp.toPx()
        clipRect {
            for (row in 0..(size.height / cell).toInt()) for (column in 0..(size.width / cell).toInt()) {
                drawRect(if ((row + column) % 2 == 0) Color(0xFF707070) else Color(0xFFAAAAAA),
                    topLeft = Offset(column * cell, row * cell), size = Size(cell, cell))
            }
        }
    }
}
