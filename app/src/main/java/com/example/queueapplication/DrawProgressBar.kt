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
            val width = size.width
            val height = size.height
            val strokeWidthPx = density.run { strokeWidth.toPx() }
            rangeComposition.initialiseList()
            drawLine(
                start = Offset(x = 0f, y = height / 2),
                end = Offset(x = width, y = height / 2),
                color = Color.Black,
                strokeWidth = strokeWidthPx,
            )
            itemLst.forEachIndexed { index, rangeItem ->
                val endPointInFloat = rangeItem.endPoint
                activity.logE("name ${rangeItem.name} --++-- startPoint ${rangeItem.startPoint} --++-- endPoint ${rangeItem.endPoint} ")
                if (index != itemLst.lastIndex) {
                    drawLine(
                        start = Offset(x = endPointInFloat, y = 0F),
                        end = Offset(x = endPointInFloat, y = boxSize.toPx()),
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
