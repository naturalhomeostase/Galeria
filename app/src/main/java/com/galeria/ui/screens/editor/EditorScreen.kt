package com.galeria.ui.screens.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.galeria.util.FilterPreset
import kotlin.math.max
import kotlin.math.min

private enum class EditorTab(val label: String) { AJUSTAR("Ajustar"), COR("Cor"), DESENHAR("Desenhar"), TEXTO("Texto") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    photoUri: android.net.Uri,
    onClose: () -> Unit,
    onSaved: () -> Unit
) {
    val viewModel: EditorViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = photoUri.toString())
    val state by viewModel.state.collectAsState()

    LaunchedEffect(photoUri) { viewModel.load(photoUri) }

    var tab by remember { mutableStateOf(EditorTab.AJUSTAR) }
    var drawColor by remember { mutableStateOf(Color.Red) }
    var strokeWidth by remember { mutableStateOf(0.01f) }
    var currentPoints by remember { mutableStateOf(listOf<Offset>()) }
    var showTextDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar foto") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Fechar")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.save { onSaved() }
                    }) { Text("Salvar") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            val bmp = state.baseBitmap
            if (state.loading || bmp == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    val ratio = bmp.width.toFloat() / bmp.height.toFloat()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ratio)
                    ) {
                        val colorMatrix = remember(state.brightness, state.contrast, state.saturation, state.filter) {
                            buildComposeColorMatrix(state.brightness, state.contrast, state.saturation, state.filter)
                        }
                        androidx.compose.foundation.Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            colorFilter = ColorFilter.colorMatrix(colorMatrix),
                            modifier = Modifier.fillMaxSize()
                        )

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            state.strokes.forEach { s ->
                                val path = androidx.compose.ui.graphics.Path()
                                s.points.forEachIndexed { i, p ->
                                    val point = Offset(p.x * size.width, p.y * size.height)
                                    if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                                }
                                drawPath(
                                    path,
                                    color = Color(s.colorArgb),
                                    style = Stroke(width = s.widthFraction * size.width)
                                )
                            }
                            if (tab == EditorTab.DESENHAR && currentPoints.isNotEmpty()) {
                                val path = androidx.compose.ui.graphics.Path()
                                currentPoints.forEachIndexed { i, p ->
                                    val point = Offset(p.x * size.width, p.y * size.height)
                                    if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                                }
                                drawPath(path, color = drawColor, style = Stroke(width = strokeWidth * size.width))
                            }
                        }

                        state.textOverlays.forEachIndexed { index, overlay ->
                            Text(
                                text = overlay.text,
                                color = Color(overlay.colorArgb),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offsetFraction(overlay.xFraction, overlay.yFraction)
                                    .pointerInput(index) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val newX = (overlay.xFraction + dragAmount.x / size.width).coerceIn(0f, 1f)
                                            val newY = (overlay.yFraction + dragAmount.y / size.height).coerceIn(0f, 1f)
                                            viewModel.updateTextPosition(index, newX, newY)
                                        }
                                    }
                            )
                        }

                        if (tab == EditorTab.DESENHAR) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(drawColor, strokeWidth) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                currentPoints = listOf(Offset(offset.x / size.width, offset.y / size.height))
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                currentPoints = currentPoints + Offset(change.position.x / size.width, change.position.y / size.height)
                                            },
                                            onDragEnd = {
                                                if (currentPoints.size > 1) {
                                                    viewModel.addStroke(
                                                        DrawStroke(
                                                            colorArgb = drawColor.toArgb(),
                                                            widthFraction = strokeWidth,
                                                            points = currentPoints
                                                        )
                                                    )
                                                }
                                                currentPoints = emptyList()
                                            }
                                        )
                                    }
                            )
                        }
                    }
                }

                TabRow(selectedTabIndex = tab.ordinal) {
                    EditorTab.entries.forEach { t ->
                        Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label) })
                    }
                }

                when (tab) {
                    EditorTab.AJUSTAR -> AjustarPanel(
                        onRotate = { viewModel.rotate90() },
                        onFlip = { viewModel.flipHorizontal() },
                        onCrop = { l, t, r, b -> viewModel.crop(l, t, r, b) }
                    )
                    EditorTab.COR -> CorPanel(
                        brightness = state.brightness,
                        contrast = state.contrast,
                        saturation = state.saturation,
                        filter = state.filter,
                        onBrightness = viewModel::setBrightness,
                        onContrast = viewModel::setContrast,
                        onSaturation = viewModel::setSaturation,
                        onFilter = viewModel::setFilter
                    )
                    EditorTab.DESENHAR -> DesenharPanel(
                        color = drawColor,
                        widthFraction = strokeWidth,
                        onColor = { drawColor = it },
                        onWidth = { strokeWidth = it },
                        onUndo = { viewModel.undoStroke() }
                    )
                    EditorTab.TEXTO -> TextoPanel(onAdd = { showTextDialog = true })
                }
            }
        }
    }

    if (showTextDialog) {
        AddTextDialog(
            onDismiss = { showTextDialog = false },
            onConfirm = { text, color ->
                showTextDialog = false
                viewModel.addText(TextOverlay(text, color.toArgb(), 0.4f, 0.5f, 0.06f))
            }
        )
    }
}

private fun Modifier.offsetFraction(xFraction: Float, yFraction: Float): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.place(
                (xFraction * constraints.maxWidth).toInt(),
                (yFraction * constraints.maxHeight).toInt()
            )
        }
    }
)

private fun buildComposeColorMatrix(brightness: Float, contrast: Float, saturation: Float, filter: FilterPreset): ComposeColorMatrix {
    val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
    val cm = ComposeColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
    )
    val sat = ComposeColorMatrix().apply { setToSaturation(saturation) }
    cm.timesAssign(sat)
    val filterMatrixValues = filter.matrix().array
    cm.timesAssign(ComposeColorMatrix(filterMatrixValues))
    return cm
}

@Composable
private fun AjustarPanel(onRotate: () -> Unit, onFlip: () -> Unit, onCrop: (Float, Float, Float, Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ToolButton(icon = Icons.Filled.Rotate90DegreesCcw, label = "Girar", onClick = onRotate)
        ToolButton(icon = Icons.Filled.Flip, label = "Espelhar", onClick = onFlip)
        ToolButton(icon = Icons.Filled.Crop, label = "1:1", onClick = { onCrop(0.1f, 0f, 0.9f, 0.8f) })
        ToolButton(icon = Icons.Filled.Crop, label = "4:3", onClick = { onCrop(0.05f, 0.05f, 0.95f, 0.8f) })
    }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickableSimple(onClick)) {
        Icon(icon, contentDescription = label)
        Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
    }
}

private fun Modifier.clickableSimple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

@Composable
private fun CorPanel(
    brightness: Float,
    contrast: Float,
    saturation: Float,
    filter: FilterPreset,
    onBrightness: (Float) -> Unit,
    onContrast: (Float) -> Unit,
    onSaturation: (Float) -> Unit,
    onFilter: (FilterPreset) -> Unit
) {
    Column(modifier = Modifier.padding(12.dp)) {
        Text("Filtros", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            items(FilterPreset.entries) { f ->
                Text(
                    text = f.label,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (f == filter) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
                        .clickableSimple { onFilter(f) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (f == filter) Color.White else Color.Unspecified
                )
            }
        }
        Text("Brilho")
        Slider(value = brightness, onValueChange = onBrightness, valueRange = -80f..80f)
        Text("Contraste")
        Slider(value = contrast, onValueChange = onContrast, valueRange = 0.5f..1.8f)
        Text("Saturação")
        Slider(value = saturation, onValueChange = onSaturation, valueRange = 0f..2f)
    }
}

@Composable
private fun DesenharPanel(
    color: Color,
    widthFraction: Float,
    onColor: (Color) -> Unit,
    onWidth: (Float) -> Unit,
    onUndo: () -> Unit
) {
    val colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.White, Color.Black)
    Column(modifier = Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            colors.forEach { c ->
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(c)
                        .clickableSimple { onColor(c) }
                )
            }
            IconButton(onClick = onUndo) { Icon(Icons.Filled.Undo, contentDescription = "Desfazer") }
        }
        Text("Espessura")
        Slider(value = widthFraction, onValueChange = onWidth, valueRange = 0.003f..0.03f)
    }
}

@Composable
private fun TextoPanel(onAdd: () -> Unit) {
    Row(modifier = Modifier.padding(12.dp)) {
        Button(onClick = onAdd) {
            Icon(Icons.Filled.TextFields, contentDescription = null)
            Text("  Adicionar texto")
        }
    }
}

@Composable
private fun AddTextDialog(onDismiss: () -> Unit, onConfirm: (String, Color) -> Unit) {
    var text by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(Color.White) }
    val colors = listOf(Color.White, Color.Black, Color.Red, Color.Yellow, Color.Cyan)

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar texto") },
        text = {
            Column {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Texto") })
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    colors.forEach { c ->
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(c)
                                .clickableSimple { color = c }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text, color) }, enabled = text.isNotBlank()) {
                Text("Adicionar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
