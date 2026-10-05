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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NumberPad(
    onNumberClick: (String) -> Unit,
    onDelete: () -> Unit,
    buttonColor: Color = Color(0xFF69B9F3)
) {

    val numbers = listOf(
        "1", "2", "3",
        "4", "5", "6",
        "7", "8", "9",
        "", "0", ""
    )

    Column(
        verticalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {

        numbers
            .chunked(3)
            .forEach { row ->

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.SpaceEvenly
                ) {

                    row.forEach { number ->

                        if (number.isEmpty()) {

                            // EMPTY SPACE

                            Spacer(
                                modifier =
                                    Modifier.size(70.dp)
                            )

                        } else {

                            // NUMBER BUTTON

                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(CircleShape)
                                    .background(
                                        color =
                                            Color.White.copy(
                                                alpha = 0.08f
                                            ),
                                        shape =
                                            CircleShape
                                    )

                                    .border(
                                        width = 2.dp,
                                        color =
                                            buttonColor.copy(
                                                alpha = 0.95f
                                            ),
                                        shape =
                                            CircleShape
                                    )

                                    .clickable {
                                        onNumberClick(number)
                                    },

                                contentAlignment =
                                    Alignment.Center
                            ) {

                                // INNER CIRCLE

                                Box(
                                    modifier = Modifier
                                        .size(58.dp)
                                        .background(
                                            color =
                                                buttonColor.copy(
                                                    alpha = 0.75f
                                                ),
                                            shape =
                                                CircleShape
                                        )
                                        .border(
                                            width = 1.5.dp,
                                            color =
                                                Color.White.copy(
                                                    alpha = 0.18f
                                                ),
                                            shape =
                                                CircleShape
                                        ),

                                    contentAlignment =
                                        Alignment.Center
                                ) {

                                    Text(
                                        text = number,
                                        color = Color.White,
                                        fontSize = 40.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
    }
}