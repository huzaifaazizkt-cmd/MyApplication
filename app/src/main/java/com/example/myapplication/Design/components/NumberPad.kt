package com.example.myapplication.Design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NumberPad(
    onNumberClick: (String) -> Unit,
    onDelete: () -> Unit,
    buttonColor: Color = Color(0xFF69B9F3),
    buttonSize: Dp = 64.dp,
    rowSpacing: Dp = 20.dp
) {
    val numbers = listOf(
        "1", "2", "3",
        "4", "5", "6",
        "7", "8", "9",
        "", "0", ""
    )

    val innerSize = buttonSize * 0.81f
    val textSize = (buttonSize.value * 0.53f).sp

    Column(verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
        numbers.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { number ->
                    if (number.isEmpty()) {
                        Spacer(modifier = Modifier.size(buttonSize))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(buttonSize)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                .border(2.dp, buttonColor.copy(alpha = 0.95f), CircleShape)
                                .clickable { onNumberClick(number) },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(innerSize)
                                    .background(buttonColor.copy(alpha = 0.75f), CircleShape)
                                    .border(1.5.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = number,
                                    color = Color.White,
                                    fontSize = textSize
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}