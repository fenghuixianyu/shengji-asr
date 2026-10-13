package com.shengji.asr.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.shengji.asr.AsrModels
import com.shengji.asr.AsrUiState
import com.shengji.asr.AsrViewModel
import com.shengji.asr.TranscriptExporter
import com.shengji.asr.Utterance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

private const val TAB_FILE = 0
private const val TAB_MIC = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsrApp(vm: AsrViewModel) {
    val state by vm.state.collectAsState()
    var tab by remember { mutableIntStateOf(TAB_FILE) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(state.error) {
        val msg = state.error
        if (!msg.isNullOrBlank()) {
            snackbar.showSnackbar(msg)
            vm.dismissError()
        }
    }

    val pickAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = queryDisplayName(context, uri) ?: "已选文件"
            vm.transcribeFile(uri, name)
        }
    }

    val exportTxt = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? -> if (uri != null) writeText(context, uri, vm.exportPlain()) }

    val exportSrt = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-subrip")
    ) { uri: Uri? -> if (uri != null) writeText(context, uri, vm.exportSrt()) }

    val exportTxtTime = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? -> if (uri != null) writeText(context, uri, vm.exportTimestamped()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                title = {
                    Column {
                        Text(
                            text = AsrModels.appTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "本地离线 · ${state.modelName} · ${state.languageName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ModelSelector(
                state = state,
                onSelect = { vm.selectModel(it) },
            )

            LanguageSelector(
                state = state,
                onSelect = { vm.selectLanguage(it) },
            )

            TabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Tab(
                    selected = tab == TAB_FILE,
                    onClick = { tab = TAB_FILE },
                    text = { Text("导入文件") },
                    icon = { Icon(Icons.Rounded.Description, contentDescription = null) },
                )
                Tab(
                    selected = tab == TAB_MIC,
                    onClick = { tab = TAB_MIC },
                    text = { Text("实时录音") },
                    icon = { Icon(Icons.Rounded.Mic, contentDescription = null) },
                )
            }

            if (state.loading || state.switching) {
                LoadingBanner(text = if (state.switching) "正在切换档位 / 语言模式, 请稍候…" else null)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (tab == TAB_FILE) {
                    FilePanel(
                        state = state,
                        onPick = { pickAudio.launch(arrayOf("audio/*", "video/*")) },
                        onCancel = { vm.cancel() },
                    )
                } else {
                    MicPanel(
                        state = state,
                        onToggle = { vm.toggleRecording() },
                        onCancel = { vm.cancel() },
                    )
                }

                Spacer(Modifier.height(12.dp))

                ResultPanel(
                    state = state,
                    onCopy = {
                        copyToClipboard(context, vm.exportPlain())
                        scope.launch { snackbar.showSnackbar("已复制到剪贴板") }
                    },
                    onExportTxt = { exportTxt.launch(suggestName(state, "txt")) },
                    onExportTxtTime = { exportTxtTime.launch(suggestName(state, "txt", "带时间戳")) },
                    onExportSrt = { exportSrt.launch(suggestName(state, "srt")) },
                    onClear = { vm.clearResult() },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 精度档位

@Composable
private fun ModelSelector(
    state: AsrUiState,
    onSelect: (AsrModels.ModelSpec) -> Unit,
) {
    val enabled = !state.busy && !state.recording && !state.loading && !state.switching
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Speed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "识别精度",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (AsrModels.bundled.size > 1) "默认标准档, 性价比最高"
                else "已内置标准档, 性价比最高",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (AsrModels.bundled.size > 1) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AsrModels.bundled.forEach { spec ->
                    val selected = state.modelId == spec.id
                    FilterChip(
                        selected = selected,
                        enabled = enabled,
                        onClick = { onSelect(spec) },
                        shape = RoundedCornerShape(12.dp),
                        label = {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text = spec.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = spec.speedHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        }
        val current = AsrModels.bundled.firstOrNull { it.id == state.modelId }
        if (current != null) {
            Text(
                text = current.description,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------------ 语言模式

@Composable
private fun LanguageSelector(
    state: AsrUiState,
    onSelect: (AsrModels.LanguageMode) -> Unit,
) {
    val enabled = !state.busy && !state.recording && !state.loading && !state.switching
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "识别语言",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "默认中文优先",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AsrModels.languages.forEach { mode ->
                val selected = state.languageId == mode.id
                FilterChip(
                    selected = selected,
                    enabled = enabled,
                    onClick = { onSelect(mode) },
                    shape = RoundedCornerShape(12.dp),
                    label = {
                        Text(
                            text = mode.displayName,
                            modifier = Modifier.padding(vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                )
            }
        }
        val current = AsrModels.languages.firstOrNull { it.id == state.languageId }
        if (current != null) {
            Text(
                text = current.description,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------------ 组件

@Composable
private fun LoadingBanner(text: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = text ?: "正在加载离线模型, 首次启动需要十几秒…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilePanel(
    state: AsrUiState,
    onPick: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "导入音频 / 视频",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "支持 mp3 / wav / m4a / aac / flac / mp4 / mkv 等常见格式, " +
                    "自动抽音轨并转成 16kHz 单声道后离线识别。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.sourceName.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = state.sourceName,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (state.busy) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPick,
                    enabled = !state.busy && !state.loading && !state.switching,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("选择文件")
                }
                if (state.busy) {
                    FilledTonalButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("停止")
                    }
                }
            }
        }
    }
}

@Composable
private fun MicPanel(
    state: AsrUiState,
    onToggle: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    val active = state.recording
    val accent = if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (active) "正在聆听, 说完停顿一下即可出文字" else "实时麦克风转文字",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = if (active) 0.18f else 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = {
                        if (!hasPermission) {
                            requestPermission.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            onToggle()
                        }
                    },
                    enabled = !state.loading && !state.switching,
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(accent),
                ) {
                    Icon(
                        imageVector = if (active) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = when {
                    state.loading -> "模型加载中…"
                    state.status.isNotBlank() -> state.status
                    active -> "识别中…"
                    else -> "点击麦克风开始"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (active) {
                Spacer(Modifier.height(14.dp))
                FilledTonalButton(onClick = onCancel, shape = RoundedCornerShape(14.dp)) {
                    Text("结束并整理结果")
                }
            }
        }
    }
}

@Composable
private fun ResultPanel(
    state: AsrUiState,
    onCopy: () -> Unit,
    onExportTxt: () -> Unit,
    onExportTxtTime: () -> Unit,
    onExportSrt: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Subtitles,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "识别结果",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${state.utterances.size} 段",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ResultAction(
                    icon = Icons.Rounded.ContentCopy,
                    label = "复制",
                    enabled = state.hasResult,
                    onClick = onCopy,
                )
                ResultAction(
                    icon = Icons.Rounded.Save,
                    label = "导出纯文本 TXT",
                    enabled = state.hasResult,
                    onClick = onExportTxt,
                )
                ResultAction(
                    icon = Icons.Rounded.Schedule,
                    label = "导出带时间戳 TXT",
                    enabled = state.hasResult,
                    onClick = onExportTxtTime,
                )
                ResultAction(
                    icon = Icons.Rounded.Subtitles,
                    label = "导出 SRT 字幕",
                    enabled = state.hasResult,
                    onClick = onExportSrt,
                )
                ResultAction(
                    icon = Icons.Rounded.Delete,
                    label = "清空",
                    enabled = state.hasResult,
                    onClick = onClear,
                )
            }

            if (state.status.isNotBlank() && !state.busy) {
                Text(
                    text = state.status,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (state.utterances.isEmpty()) {
                    EmptyHint(state)
                } else {
                    val listState = rememberLazyListState()
                    LaunchedEffect(state.utterances.size) {
                        if (state.utterances.isNotEmpty()) {
                            listState.animateScrollToItem(state.utterances.size - 1)
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.utterances) { item -> UtteranceRow(item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(42.dp),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun EmptyHint(state: AsrUiState) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (state.busy) "正在识别, 结果会实时出现在这里…" else "还没有识别结果",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UtteranceRow(item: Utterance) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = item.startLabel(),
            modifier = Modifier
                .width(76.dp)
                .padding(top = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ------------------------------------------------------------------ 工具

private fun suggestName(state: AsrUiState, ext: String, suffix: String? = null): String {
    val base = state.sourceName.substringBeforeLast('.').ifBlank { "transcript" }
    val tag = if (suffix.isNullOrBlank()) "" else "_$suffix"
    return "$base$tag.$ext"
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    } catch (_: Throwable) {
        null
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("transcript", text))
}

private fun writeText(context: Context, uri: Uri, text: String) {
    try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(text.toByteArray(StandardCharsets.UTF_8))
            out.flush()
        }
    } catch (_: Throwable) {
    }
}

private fun AsrViewModel.exportPlain(): String = state.value.let { TranscriptExporter.toPlainText(it.utterances) }

private fun AsrViewModel.exportSrt(): String = state.value.let { TranscriptExporter.toSrt(it.utterances) }

private fun AsrViewModel.exportTimestamped(): String =
    state.value.let { TranscriptExporter.toTimestampedText(it.utterances) }
