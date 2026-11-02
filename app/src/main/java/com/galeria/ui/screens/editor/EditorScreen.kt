package com.galeria.ui.screens.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.galeria.util.FileUtils
import com.galeria.util.FilterPreset
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.style.TextAlign
import com.galeria.util.CropCorner
import com.galeria.util.CropFrame
import com.galeria.util.CropGeometry
import com.galeria.util.ImageTransform
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Estado do corte livre, fora do FreeCropOverlay para que os botões de cancelar/aplicar
 * (que ficam no painel de baixo, e não por cima da imagem) consigam ler o resultado.
 * Toda a matemática fica em CropGeometry (testada à parte).
 */
private class CropUiState {
    var frame by mutableStateOf(CropGeometry.DEFAULT_FRAME)
    var transform by mutableStateOf(ImageTransform())

    fun reset() {
        frame = CropGeometry.DEFAULT_FRAME
        transform = ImageTransform()
    }

    fun moveFrame(dx: Float, dy: Float) { frame = CropGeometry.moveFrame(frame, dx, dy) }
    fun dragCorner(corner: CropCorner, dx: Float, dy: Float) { frame = CropGeometry.dragCorner(frame, corner, dx, dy) }
    fun panImage(dx: Float, dy: Float) { transform = CropGeometry.panBy(transform, dx, dy) }
    fun zoomAndPan(focalX: Float, focalY: Float, zoom: Float, dx: Float, dy: Float) {
        transform = CropGeometry.panBy(CropGeometry.zoomAbout(transform, focalX, focalY, zoom), dx, dy)
    }

    /** Região da imagem original (em frações 0..1) que fica dentro da moldura agora. */
    fun imageRegion(): CropFrame = CropGeometry.toImageFractions(frame, transform)
}

private enum class EditorTab(val label: String) { AJUSTAR("Ajustar"), COR("Cor"), DESENHAR("Desenhar"), TEXTO("Texto"), COMPRIMIR("Comprimir") }

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
    var cropMode by remember { mutableStateOf(false) }
    val cropState = remember { CropUiState() }

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
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    val ratio = bmp.width.toFloat() / bmp.height.toFloat()
                    // A imagem é ajustada para caber INTEIRA na área visível (largura e altura),
                    // como ContentScale.Fit. Antes ela sempre ocupava a largura toda, e uma foto
                    // alta (ex.: print de tela) passava da altura disponível: o topo e o rodapé
                    // ficavam escondidos atrás da barra superior e das opções de baixo.
                    val fitsByWidth = maxWidth.value / maxHeight.value.coerceAtLeast(1f) <= ratio
                    val imageWidth = if (fitsByWidth) maxWidth else maxHeight * ratio
                    val imageHeight = if (fitsByWidth) maxWidth / ratio else maxHeight
                    var containerWidthPx by remember { mutableStateOf(1f) }
                    var containerHeightPx by remember { mutableStateOf(1f) }
                    Box(
                        modifier = Modifier
                            .size(imageWidth, imageHeight)
                            .onGloballyPositioned {
                                containerWidthPx = it.size.width.toFloat().coerceAtLeast(1f)
                                containerHeightPx = it.size.height.toFloat().coerceAtLeast(1f)
                            }
                    ) {
                        // Durante o corte livre, a imagem (com traços e textos) pode receber zoom
                        // e ser movida por baixo da moldura; fora disso a transformação é neutra.
                        // O estado é lido dentro do graphicsLayer (e não aqui) para que a pinça
                        // só redesenhe a camada, sem recompor a tela inteira a cada quadro.
                        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val tr = if (cropMode) cropState.transform else ImageTransform()
                                        transformOrigin = TransformOrigin(0f, 0f)
                                        scaleX = tr.scale
                                        scaleY = tr.scale
                                        translationX = tr.tx * size.width
                                        translationY = tr.ty * size.height
                                    }
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
                                            .pointerInput(index, containerWidthPx, containerHeightPx) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    val newX = (overlay.xFraction + dragAmount.x / containerWidthPx).coerceIn(0f, 1f)
                                                    val newY = (overlay.yFraction + dragAmount.y / containerHeightPx).coerceIn(0f, 1f)
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

                        if (cropMode) {
                            FreeCropOverlay(state = cropState)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                ) {
                    if (cropMode) {
                        // Cancelar (X) e aplicar (check) ficam aqui, no painel de baixo, e não por
                        // cima da imagem -- assim nunca cobrem nem ficam escondidos atrás de nada.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { cropMode = false }) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancelar corte")
                            }
                            Text(
                                "Arraste os cantos ou a área para ajustar. Use a pinça para dar zoom.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = {
                                    val region = cropState.imageRegion()
                                    viewModel.crop(region.left, region.top, region.right, region.bottom)
                                    cropMode = false
                                },
                                modifier = Modifier.background(
                                    androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Aplicar corte",
                                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                } else {
                    TabRow(selectedTabIndex = tab.ordinal) {
                        EditorTab.entries.forEach { t ->
                            Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label) })
                        }
                    }

                    when (tab) {
                        EditorTab.AJUSTAR -> AjustarPanel(
                            onRotate = { viewModel.rotate90() },
                            onFlip = { viewModel.flipHorizontal() },
                            onCropRatio = { ratio -> viewModel.cropToAspectRatio(ratio) },
                            onCropFree = {
                                cropState.reset()
                                cropMode = true
                            }
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
                        EditorTab.COMPRIMIR -> ComprimirPanel(
                            originalSizeBytes = state.originalSizeBytes,
                            estimatedSizeBytes = state.estimatedSizeBytes,
                            estimating = state.estimating,
                            quality = state.compressQuality,
                            maxDimensionOption = state.maxDimensionOption,
                            onQualityChange = viewModel::setCompressQuality,
                            onMaxDimensionChange = viewModel::setMaxDimension
                        )
                    }
                }
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
private fun AjustarPanel(onRotate: () -> Unit, onFlip: () -> Unit, onCropRatio: (Float) -> Unit, onCropFree: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ToolButton(icon = Icons.Filled.Rotate90DegreesCcw, label = "Girar", onClick = onRotate)
        ToolButton(icon = Icons.Filled.Flip, label = "Espelhar", onClick = onFlip)
        ToolButton(icon = Icons.Filled.Crop, label = "Livre", onClick = onCropFree)
        ToolButton(icon = Icons.Filled.Crop, label = "1:1", onClick = { onCropRatio(1f) })
        ToolButton(icon = Icons.Filled.Crop, label = "4:3", onClick = { onCropRatio(4f / 3f) })
        ToolButton(icon = Icons.Filled.Crop, label = "16:9", onClick = { onCropRatio(16f / 9f) })
    }
}

/**
 * Overlay de corte livre. Preenche exatamente o mesmo Box da imagem, então as frações
 * (0f..1f) daqui correspondem 1:1 à área onde a imagem é exibida. Gestos:
 *  - arrastar uma alça de canto: redimensiona a moldura;
 *  - arrastar com 1 dedo começando DENTRO da moldura: move a moldura;
 *  - arrastar com 1 dedo começando FORA da moldura: move a imagem (quando há zoom);
 *  - pinça (2 dedos): dá zoom na imagem em torno dos dedos (e 2 dedos juntos movem a imagem).
 * Os botões de cancelar/aplicar não ficam mais aqui: estão no painel de baixo da tela.
 */
@Composable
private fun FreeCropOverlay(state: CropUiState) {
    val frame = state.frame

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val startFrame = state.frame

                    // Toque em cima de uma alça de canto: quem trata é a própria alça.
                    val handleRadiusPx = 28.dp.toPx()
                    val startedOnHandle = listOf(
                        startFrame.left to startFrame.top,
                        startFrame.right to startFrame.top,
                        startFrame.left to startFrame.bottom,
                        startFrame.right to startFrame.bottom
                    ).any { (fx, fy) ->
                        hypot(down.position.x - fx * w, down.position.y - fy * h) <= handleRadiusPx
                    }
                    val startedInsideFrame = startFrame.contains(down.position.x / w, down.position.y / h)

                    do {
                        val event = awaitPointerEvent()
                        if (!startedOnHandle && event.changes.none { it.isConsumed }) {
                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount >= 2) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                state.zoomAndPan(centroid.x / w, centroid.y / h, zoom, pan.x / w, pan.y / h)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (pressedCount == 1) {
                                val pan = event.calculatePan()
                                if (startedInsideFrame) {
                                    state.moveFrame(pan.x / w, pan.y / h)
                                } else {
                                    state.panImage(pan.x / w, pan.y / h)
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val l = frame.left * size.width
            val t = frame.top * size.height
            val r = frame.right * size.width
            val b = frame.bottom * size.height
            val dim = Color.Black.copy(alpha = 0.55f)
            drawRect(color = dim, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(size.width, t))
            drawRect(color = dim, topLeft = Offset(0f, b), size = androidx.compose.ui.geometry.Size(size.width, size.height - b))
            drawRect(color = dim, topLeft = Offset(0f, t), size = androidx.compose.ui.geometry.Size(l, b - t))
            drawRect(color = dim, topLeft = Offset(r, t), size = androidx.compose.ui.geometry.Size(size.width - r, b - t))
            drawRect(
                color = Color.White,
                topLeft = Offset(l, t),
                size = androidx.compose.ui.geometry.Size(r - l, b - t),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        CropHandle(xFraction = frame.left, yFraction = frame.top) { dxPx, dyPx, widthPx, heightPx ->
            state.dragCorner(CropCorner.TOP_LEFT, dxPx / widthPx, dyPx / heightPx)
        }
        CropHandle(xFraction = frame.right, yFraction = frame.top) { dxPx, dyPx, widthPx, heightPx ->
            state.dragCorner(CropCorner.TOP_RIGHT, dxPx / widthPx, dyPx / heightPx)
        }
        CropHandle(xFraction = frame.left, yFraction = frame.bottom) { dxPx, dyPx, widthPx, heightPx ->
            state.dragCorner(CropCorner.BOTTOM_LEFT, dxPx / widthPx, dyPx / heightPx)
        }
        CropHandle(xFraction = frame.right, yFraction = frame.bottom) { dxPx, dyPx, widthPx, heightPx ->
            state.dragCorner(CropCorner.BOTTOM_RIGHT, dxPx / widthPx, dyPx / heightPx)
        }
    }
}

/**
 * Alça arrastável de um canto do retângulo de corte. Fica centralizada exatamente sobre o
 * ponto (xFraction, yFraction) do Box pai, e reporta o arraste em pixels — a conversão pra
 * fração é feita pelo chamador, que também decide como limitar cada lado.
 */
@Composable
private fun CropHandle(
    xFraction: Float,
    yFraction: Float,
    onDrag: (dxPx: Float, dyPx: Float, containerWidthPx: Float, containerHeightPx: Float) -> Unit
) {
    var containerWidthPx by remember { mutableStateOf(1f) }
    var containerHeightPx by remember { mutableStateOf(1f) }
    val handleSize = 36.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                containerWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                containerHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                val placeable = measurable.measure(
                    androidx.compose.ui.unit.Constraints.fixed(handleSize.roundToPx(), handleSize.roundToPx())
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        (xFraction * constraints.maxWidth - placeable.width / 2f).toInt(),
                        (yFraction * constraints.maxHeight - placeable.height / 2f).toInt()
                    )
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y, containerWidthPx, containerHeightPx)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
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

/**
 * Aba de compressão: reduz a qualidade JPEG e/ou a resolução da imagem antes de salvar.
 * O tamanho estimado é recalculado (com um pequeno atraso) toda vez que a pessoa mexe no
 * slider ou troca a opção de redimensionamento, comprimindo a imagem em memória sem gravar
 * nada em disco -- só pra mostrar a previsão antes de confirmar com "Salvar".
 */
@Composable
private fun ComprimirPanel(
    originalSizeBytes: Long?,
    estimatedSizeBytes: Long?,
    estimating: Boolean,
    quality: Int,
    maxDimensionOption: Int?,
    onQualityChange: (Int) -> Unit,
    onMaxDimensionChange: (Int?) -> Unit
) {
    Column(modifier = Modifier.padding(12.dp)) {
        Text("Qualidade (${quality}%)", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        Slider(
            value = quality.toFloat(),
            onValueChange = { onQualityChange(it.toInt()) },
            valueRange = 10f..100f
        )

        Text(
            "Redimensionar",
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            listOf(null to "Original", 1920 to "1920px", 1280 to "1280px", 1024 to "1024px").forEach { (value, label) ->
                val selected = value == maxDimensionOption
                Text(
                    text = label,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (selected) androidx.compose.material3.MaterialTheme.colorScheme.primary
                            else androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickableSimple { onMaxDimensionChange(value) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (selected) Color.White else Color.Unspecified
                )
            }
        }

        Column(modifier = Modifier.padding(top = 8.dp)) {
            if (originalSizeBytes != null) {
                Text(
                    "Tamanho original: ${FileUtils.formatSize(originalSizeBytes)}",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                estimating -> Text(
                    "Calculando tamanho estimado…",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                estimatedSizeBytes != null -> {
                    val reduction = originalSizeBytes?.takeIf { it > 0 }
                        ?.let { orig -> (100 - (estimatedSizeBytes * 100 / orig)).coerceIn(0, 100) }
                    Text(
                        "Tamanho estimado: ${FileUtils.formatSize(estimatedSizeBytes)}" +
                            (reduction?.let { " (-$it%)" } ?: ""),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
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
