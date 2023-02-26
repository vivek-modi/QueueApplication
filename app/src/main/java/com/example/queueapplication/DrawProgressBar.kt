package com.example.queueapplication

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalTextApi::class)
@Composable
fun DrawProgressBar() {
    val rangeComposition = RangeComposition()
    val itemLst = rangeComposition.bpExplained
    val brush = Brush.horizontalGradient(listOf(Color.Red, Color.Blue))
    val progressBarPointer = rangeComposition.findReadingWithPointer(142, 90).second
    val textMeasurer = rememberTextMeasurer()
//    val rangeName = "Extremely high"
    val rangeName = "Low"
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Color.White)
    ) {

        //triangle size

        val rectSize = Size(12.dp.toPx(), 9.dp.toPx())
        val strokeWidth = 8.dp
        val canvasWidth = size.width
        val canvasHeight = size.height
        val strokeWidthPx = density.run { strokeWidth.toPx() }
        val dashedPathEffect =
            PathEffect.dashPathEffect(floatArrayOf(canvasHeight / 38, canvasHeight / 38), 0f)
        val rect = Rect(Offset.Zero, rectSize)
        val trianglePath = Path().apply {
            moveTo(rect.bottomCenter.x, rect.bottomCenter.y)
            lineTo(rect.topRight.x, rect.topRight.y)
            lineTo(rect.topLeft.x, rect.topLeft.y)
            close()
        }
        val progressBarPointerInPixel = (progressBarPointer / 100f) * canvasWidth
        val textLayoutResult: TextLayoutResult =
            textMeasurer.measure(
                text = AnnotatedString(rangeName),
                style = TextStyle(color = Color.Blue, fontSize = 12.sp)
            )
        val textSize = textLayoutResult.size
        val triangleCenterX = progressBarPointerInPixel + rectSize.width / 2

        drawIntoCanvas { canvas ->

            translate(progressBarPointerInPixel, 20.dp.toPx()) {
                canvas.drawOutline(
                    outline = Outline.Generic(trianglePath),
                    paint = Paint().apply {
                        color = Color.DarkGray
                        pathEffect = PathEffect.cornerPathEffect(rect.maxDimension / 3)
                    }
                )
            }

            drawText(
                textMeasurer = textMeasurer,
                text = rangeName,
                topLeft = Offset((triangleCenterX - textSize.width / 2f), 1.dp.toPx()),
            )

            drawLine(
                start = Offset(x = 0f, y = (canvasHeight / 4) * 3),
                end = Offset(x = canvasWidth, y = (canvasHeight / 4) * 3),
                color = Color.Gray,
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White,
                start = Offset(x = progressBarPointerInPixel, y = (canvasHeight / 4) * 3),
                end = Offset(x = progressBarPointerInPixel + strokeWidthPx / 2, y = (canvasHeight / 4) * 3),
                strokeWidth = strokeWidthPx,
            )
            drawLine(
                brush = brush,
                start = Offset(x = 0f, y = (canvasHeight / 4) * 3),
                end = Offset(x = progressBarPointerInPixel, y = (canvasHeight / 4) * 3),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round,
            )
            drawArc(
                topLeft = Offset(
                    x = progressBarPointerInPixel,
                    y = ((canvasHeight / 4) * 3) - strokeWidthPx / 2
                ),
                size = Size(strokeWidthPx, strokeWidthPx),
                color = Color.White,
                startAngle = -90f,
                sweepAngle = 180f,
                useCenter = true
            )
            itemLst.forEachIndexed { index, rangeItem ->
                val endPointInPixel = (rangeItem.endPoint / 100f) * canvasWidth
                if (index != itemLst.lastIndex) {
                    drawLine(
                        start = Offset(x = endPointInPixel, y = 30.dp.toPx()),
                        end = Offset(x = endPointInPixel, y = canvasHeight),
                        color = Color.Black,
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = dashedPathEffect
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
