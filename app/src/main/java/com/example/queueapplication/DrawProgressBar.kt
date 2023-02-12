package com.example.queueapplication

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun DrawProgressBar() {
    val activity = LocalContext.current as AppCompatActivity
    val rangeComposition = RangeComposition()
    val itemLst = rangeComposition.bpExplained
    val boxSize = 30.dp
    Box(
        modifier = Modifier
            .background(Color.LightGray)
            .height(height = boxSize)
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val strokeWidth = 8.dp
            val canvasWidth = size.width
            val canvasHeight = size.height
            val strokeWidthPx = density.run { strokeWidth.toPx() }
            drawLine(
                start = Offset(x = 0f, y = canvasHeight / 2),
                end = Offset(x = canvasWidth, y = canvasHeight / 2),
                color = Color.Black,
                strokeWidth = strokeWidthPx,
            )
            itemLst.forEachIndexed { index, rangeItem ->
                val endPointInPixel = (rangeItem.endPoint / 100f) * canvasWidth
                if (index != itemLst.lastIndex) {
                    drawLine(
                        start = Offset(x = endPointInPixel, y = 0F),
                        end = Offset(x = endPointInPixel, y = boxSize.toPx()),
                        color = Color.Black,
                        strokeWidth = 4.dp.toPx(),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DrawProgressBarPreview() {
    DrawProgressBar()
}
