package com.galeria.ui.screens.editor

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galeria.util.FilterPreset
import com.galeria.util.ImageEditUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DrawStroke(val colorArgb: Int, val widthFraction: Float, val points: List<Offset>)
data class TextOverlay(val text: String, val colorArgb: Int, val xFraction: Float, val yFraction: Float, val sizeFraction: Float)

data class EditorUiState(
    val loading: Boolean = true,
    val baseBitmap: Bitmap? = null,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val filter: FilterPreset = FilterPreset.NORMAL,
    val strokes: List<DrawStroke> = emptyList(),
    val textOverlays: List<TextOverlay> = emptyList(),
    val saved: Boolean = false
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state

    fun load(uri: Uri) {
        if (_state.value.baseBitmap != null) return
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                ImageEditUtils.loadBitmap(getApplication(), uri)
            }
            _state.value = _state.value.copy(baseBitmap = bmp, loading = false)
        }
    }

    fun rotate90() {
        val bmp = _state.value.baseBitmap ?: return
        _state.value = _state.value.copy(baseBitmap = ImageEditUtils.rotateBitmap(bmp, 90f), strokes = emptyList(), textOverlays = emptyList())
    }

    fun flipHorizontal() {
        val bmp = _state.value.baseBitmap ?: return
        _state.value = _state.value.copy(baseBitmap = ImageEditUtils.flipBitmap(bmp, true), strokes = emptyList(), textOverlays = emptyList())
    }

    fun cropToAspectRatio(targetRatio: Float) {
        val bmp = _state.value.baseBitmap ?: return
        val currentRatio = bmp.width.toFloat() / bmp.height.toFloat()
        val (left, top, right, bottom) = if (currentRatio > targetRatio) {
            val newWidthFraction = targetRatio / currentRatio
            val excess = (1f - newWidthFraction) / 2f
            listOf(excess, 0f, 1f - excess, 1f)
        } else {
            val newHeightFraction = currentRatio / targetRatio
            val excess = (1f - newHeightFraction) / 2f
            listOf(0f, excess, 1f, 1f - excess)
        }
        crop(left, top, right, bottom)
    }

    fun crop(leftPct: Float, topPct: Float, rightPct: Float, bottomPct: Float) {
        val bmp = _state.value.baseBitmap ?: return
        _state.value = _state.value.copy(
            baseBitmap = ImageEditUtils.cropBitmap(bmp, leftPct, topPct, rightPct, bottomPct),
            strokes = emptyList(),
            textOverlays = emptyList()
        )
    }

    fun setBrightness(v: Float) { _state.value = _state.value.copy(brightness = v) }
    fun setContrast(v: Float) { _state.value = _state.value.copy(contrast = v) }
    fun setSaturation(v: Float) { _state.value = _state.value.copy(saturation = v) }
    fun setFilter(f: FilterPreset) { _state.value = _state.value.copy(filter = f) }

    fun addStroke(stroke: DrawStroke) {
        _state.value = _state.value.copy(strokes = _state.value.strokes + stroke)
    }

    fun undoStroke() {
        _state.value = _state.value.copy(strokes = _state.value.strokes.dropLast(1))
    }

    fun addText(overlay: TextOverlay) {
        _state.value = _state.value.copy(textOverlays = _state.value.textOverlays + overlay)
    }

    fun updateTextPosition(index: Int, xFraction: Float, yFraction: Float) {
        val list = _state.value.textOverlays.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(xFraction = xFraction, yFraction = yFraction)
            _state.value = _state.value.copy(textOverlays = list)
        }
    }

    fun save(onSaved: (Uri?) -> Unit) {
        val s = _state.value
        val base = s.baseBitmap ?: return
        viewModelScope.launch {
            val resultUri = withContext(Dispatchers.Default) {
                val result = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(result)

                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val combined = ImageEditUtils.buildColorMatrix(s.brightness, s.contrast, s.saturation)
                combined.postConcat(s.filter.matrix())
                paint.colorFilter = ColorMatrixColorFilter(combined)
                canvas.drawBitmap(base, 0f, 0f, paint)

                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                s.strokes.forEach { stroke ->
                    strokePaint.color = stroke.colorArgb
                    strokePaint.strokeWidth = stroke.widthFraction * base.width
                    val path = android.graphics.Path()
                    stroke.points.forEachIndexed { i, p ->
                        val x = p.x * base.width
                        val y = p.y * base.height
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    canvas.drawPath(path, strokePaint)
                }

                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                s.textOverlays.forEach { overlay ->
                    textPaint.color = overlay.colorArgb
                    textPaint.textSize = overlay.sizeFraction * base.width
                    canvas.drawText(
                        overlay.text,
                        overlay.xFraction * base.width,
                        overlay.yFraction * base.height,
                        textPaint
                    )
                }

                val name = "galeria_edit_${System.currentTimeMillis()}.jpg"
                ImageEditUtils.saveToGallery(getApplication(), result, name)
            }
            _state.value = _state.value.copy(saved = true)
            onSaved(resultUri)
        }
    }
}
