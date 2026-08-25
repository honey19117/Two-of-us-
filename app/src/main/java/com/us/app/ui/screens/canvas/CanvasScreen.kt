package com.us.app.ui.screens.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.us.app.UsViewModel
import com.us.app.data.model.StrokePath
import com.us.app.data.model.Point

@Composable
fun CanvasScreen(viewModel: UsViewModel) {
    var paths by remember { mutableStateOf(listOf<StrokePath>()) }
    var currentPath by remember { mutableStateOf<StrokePath?>(null) }
    
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { paths = paths.dropLast(1) }) { Text("Undo") }
            Button(onClick = { paths = emptyList() }) { Text("Clear") }
        }

        Canvas(
            modifier = Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> currentPath = StrokePath(points = listOf(Point(offset.x, offset.y))) },
                    onDrag = { change, _ -> currentPath = currentPath?.copy(points = currentPath!!.points + Point(change.position.x, change.position.y)) },
                    onDragEnd = { currentPath?.let { paths = paths + it }; currentPath = null }
                )
            }
        ) {
            paths.forEach { strokePath ->
                val path = Path()
                if (strokePath.points.isNotEmpty()) {
                    path.moveTo(strokePath.points.first().x, strokePath.points.first().y)
                    strokePath.points.drop(1).forEach { pt -> path.lineTo(pt.x, pt.y) }
                    drawPath(path = path, color = Color(strokePath.color), style = Stroke(width = strokePath.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            currentPath?.let { strokePath ->
                val path = Path()
                if (strokePath.points.isNotEmpty()) {
                    path.moveTo(strokePath.points.first().x, strokePath.points.first().y)
                    strokePath.points.drop(1).forEach { pt -> path.lineTo(pt.x, pt.y) }
                    drawPath(path = path, color = Color(strokePath.color), style = Stroke(width = strokePath.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }

        Button(onClick = { viewModel.sendDrawing(paths) }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Send ❤️") }
    }
}
