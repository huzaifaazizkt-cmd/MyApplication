
package com.example.myapplication.Design.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.Design.components.NumberPad
import com.example.myapplication.R
import kotlin.math.min
import kotlin.math.sqrt


@Composable
fun PinCreateScreen(
    onNext: (String, String) -> Unit
) {

    // ================================================================
    // STATE
    // ================================================================

    var pin by remember {
        mutableStateOf("")
    }

    var pinLength by remember {
        mutableIntStateOf(4)
    }

    var authType by remember {
        mutableStateOf("4")
    }

    var expanded by remember {
        mutableStateOf(false)
    }

    var selectedDots by remember {
        mutableStateOf<List<Int>>(emptyList())
    }

    val isPattern = authType == "pattern"

    val backgroundColor = Color(0xFF189FFF)
    val patternDotColor = Color(0xFF83CCFF)


    // ================================================================
    // ROOT
    // ================================================================

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 26.dp,
                    end = 26.dp,
                    top = 18.dp,
                    bottom = 6.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ========================================================
            // TOP AUTH TYPE
            // ========================================================

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(35.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box {

                    Row(
                        modifier = Modifier
                            .height(35.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember {
                                    MutableInteractionSource()
                                }
                            ) {
                                expanded = true
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            text = when (authType) {

                                "4" -> stringResource(
                                    R.string.four_digit_pin
                                )

                                "6" -> stringResource(
                                    R.string.six_digit_pin
                                )

                                "pattern" -> stringResource(
                                    R.string.pattern
                                )

                                else -> stringResource(
                                    R.string.four_digit_pin
                                )
                            },
                            color = Color.White,
                            fontSize = 14.sp
                        )

                        Icon(
                            imageVector =
                                Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }


                    // =================================================
                    // DROPDOWN
                    // =================================================

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = {
                            expanded = false
                        }
                    ) {

                        // 4 DIGIT PIN
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(
                                        R.string.four_digit_pin
                                    )
                                )
                            },
                            onClick = {

                                authType = "4"
                                pinLength = 4
                                pin = ""
                                selectedDots = emptyList()

                                expanded = false
                            }
                        )


                        // 6 DIGIT PIN
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(
                                        R.string.six_digit_pin
                                    )
                                )
                            },
                            onClick = {

                                authType = "6"
                                pinLength = 6
                                pin = ""
                                selectedDots = emptyList()

                                expanded = false
                            }
                        )


                        // PATTERN
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(
                                        R.string.pattern
                                    )
                                )
                            },
                            onClick = {

                                authType = "pattern"
                                pin = ""
                                selectedDots = emptyList()

                                expanded = false
                            }
                        )
                    }
                }
            }


            Spacer(
                modifier = Modifier.height(22.dp)
            )


            // ========================================================
            // STEP INDICATOR
            // ========================================================

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(31.dp)
                        .background(
                            Color.White.copy(alpha = 0.08f),
                            CircleShape
                        )
                        .border(
                            2.dp,
                            Color.White.copy(alpha = 0.35f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {

                    Box(
                        modifier = Modifier
                            .size(21.dp)
                            .background(
                                Color.White,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {

                        Text(
                            text = "1",
                            color = backgroundColor,
                            fontSize = 15.sp
                        )
                    }
                }


                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(2.dp)
                        .background(Color.White)
                )


                Box(
                    modifier = Modifier
                        .size(25.dp)
                        .border(
                            2.dp,
                            Color.White,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {

                    Text(
                        text = "2",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }


            // ========================================================
            // MAIN CONTENT
            //
            // weight(1f) means this section receives whatever space
            // is left between the top section and bottom buttons.
            // ========================================================

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {

                if (!isPattern) {

                    // =================================================
                    // PIN CONTENT
                    // =================================================

                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        Text(
                            text = stringResource(
                                R.string.create_pin,
                                pinLength
                            ),
                            color = Color.White,
                            fontSize = 18.sp
                        )


                        Spacer(
                            modifier = Modifier.height(14.dp)
                        )


                        // =================================================
                        // PIN DOTS
                        // =================================================

                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(14.dp)
                        ) {

                            repeat(pinLength) { index ->

                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .border(
                                            1.5.dp,
                                            Color.White,
                                            CircleShape
                                        )
                                        .background(
                                            color =
                                                if (
                                                    index < pin.length
                                                ) {
                                                    Color.White
                                                } else {
                                                    Color.Transparent
                                                },
                                            shape = CircleShape
                                        )
                                )
                            }
                        }


                        Spacer(
                            modifier = Modifier.height(32.dp)
                        )


                        // =================================================
                        // NUMBER PAD
                        // =================================================

                        NumberPad(

                            onNumberClick = { number ->

                                if (
                                    pin.length < pinLength
                                ) {
                                    pin += number
                                }
                            },

                            onDelete = {

                                if (pin.isNotEmpty()) {
                                    pin = pin.dropLast(1)
                                }
                            }
                        )
                    }

                } else {

                    // =================================================
                    // PATTERN CONTENT
                    // =================================================

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        Text(
                            text = stringResource(
                                R.string.create_pattern
                            ),
                            color = Color.White,
                            fontSize = 18.sp
                        )


                        Spacer(
                            modifier = Modifier.height(10.dp)
                        )


                        Text(
                            text = stringResource(
                                R.string.connect_four_dots
                            ),
                            color = Color.White.copy(
                                alpha = 0.85f
                            ),
                            fontSize = 14.sp
                        )


                        Spacer(
                            modifier = Modifier.height(20.dp)
                        )


                        // =================================================
                        // RESPONSIVE PATTERN GRID
                        // =================================================

                        BoxWithConstraints(
                            modifier = Modifier.fillMaxWidth()
                        ) {

                            val availableWidth =
                                maxWidth - 20.dp

                            // Never allow grid to become too large.
                            // On small phones it automatically becomes
                            // smaller according to available width.

                            val gridSize =
                                min(
                                    availableWidth.value,
                                    330f
                                ).dp


                            PatternGrid(
                                modifier = Modifier.size(
                                    gridSize
                                ),

                                selectedDots = selectedDots,

                                onPatternChanged = { dots: List<Int> ->
                                    selectedDots = dots
                                },

                                dotColor = patternDotColor,

                                backgroundColor = backgroundColor
                            )
                        }
                    }
                }
            }


            // ========================================================
            // BOTTOM RESET + CONTINUE
            //
            // This area is outside the weighted content, therefore
            // it will always remain visible.
            // ========================================================

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 8.dp,
                        end = 8.dp,
                        top = 6.dp,
                        bottom = 4.dp
                    ),

                horizontalArrangement =
                    Arrangement.spacedBy(
                        30.dp,
                        Alignment.End
                    ),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                // =====================================================
                // RESET
                // =====================================================

                Text(
                    text = stringResource(
                        R.string.reset
                    ),

                    color =
                        if (
                            if (isPattern) {
                                selectedDots.isEmpty()
                            } else {
                                pin.isEmpty()
                            }
                        ) {
                            Color.White.copy(
                                alpha = 0.35f
                            )
                        } else {
                            Color.White
                        },

                    fontSize = 20.sp,

                    modifier = Modifier
                        .clickable(
                            enabled =
                                if (isPattern) {
                                    selectedDots.isNotEmpty()
                                } else {
                                    pin.isNotEmpty()
                                },

                            indication = null,

                            interactionSource =
                                remember {
                                    MutableInteractionSource()
                                }
                        ) {

                            if (isPattern) {

                                selectedDots =
                                    emptyList()

                            } else {

                                pin = ""
                            }
                        }
                        .padding(
                            horizontal = 10.dp,
                            vertical = 8.dp
                        )
                )


                // =====================================================
                // CONTINUE
                // =====================================================

                Text(
                    text = stringResource(
                        R.string.continue_text
                    ),

                    color =
                        if (
                            if (isPattern) {
                                selectedDots.size >= 4
                            } else {
                                pin.length == pinLength
                            }
                        ) {
                            Color.White
                        } else {
                            Color.White.copy(
                                alpha = 0.35f
                            )
                        },

                    fontSize = 20.sp,

                    modifier = Modifier
                        .clickable(
                            enabled =
                                if (isPattern) {
                                    selectedDots.size >= 4
                                } else {
                                    pin.length == pinLength
                                },

                            indication = null,

                            interactionSource =
                                remember {
                                    MutableInteractionSource()
                                }
                        ) {

                            if (isPattern) {

                                onNext(
                                    "pattern",
                                    selectedDots.joinToString("-")
                                )

                            } else {

                                onNext(
                                    "pin",
                                    pin
                                )
                            }
                        }
                        .padding(
                            horizontal = 10.dp,
                            vertical = 8.dp
                        )
                )
            }
        }
    }
}


// ====================================================================
// PATTERN GRID
// ====================================================================

@Composable
fun PatternGrid(
    modifier: Modifier = Modifier,
    selectedDots: List<Int>,
    onPatternChanged: (List<Int>) -> Unit,
    dotColor: Color,
    backgroundColor: Color
) {

    val latestOnPatternChanged by
    rememberUpdatedState(
        onPatternChanged
    )


    Box(
        modifier = modifier
            .pointerInput(Unit) {

                var currentDots =
                    mutableListOf<Int>()


                detectDragGestures(

                    // =================================================
                    // TOUCH START
                    // =================================================

                    onDragStart = { offset ->

                        currentDots =
                            mutableListOf()

                        val dot =
                            findDot(
                                touch = offset,
                                width = size.width.toFloat(),
                                height = size.height.toFloat()
                            )

                        if (dot != null) {

                            currentDots.add(dot)

                            latestOnPatternChanged(
                                currentDots.toList()
                            )
                        }
                    },


                    // =================================================
                    // DRAG
                    // =================================================

                    onDrag = { change, _ ->

                        change.consume()

                        val dot =
                            findDot(
                                touch = change.position,
                                width = size.width.toFloat(),
                                height = size.height.toFloat()
                            )

                        if (
                            dot != null &&
                            !currentDots.contains(dot)
                        ) {

                            currentDots.add(dot)

                            latestOnPatternChanged(
                                currentDots.toList()
                            )
                        }
                    },


                    onDragEnd = {},

                    onDragCancel = {}
                )
            }
    ) {

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {

            val positions =
                getGridPositions(
                    width = size.width,
                    height = size.height
                )


            // =========================================================
            // PATTERN LINES
            // =========================================================

            if (selectedDots.size >= 2) {

                for (
                i in 0 until selectedDots.size - 1
                ) {

                    drawLine(
                        color = Color.White,

                        start =
                            positions[
                                selectedDots[i]
                            ],

                        end =
                            positions[
                                selectedDots[i + 1]
                            ],

                        strokeWidth =
                            size.minDimension * 0.024f
                    )
                }
            }


            // =========================================================
            // DOTS
            // =========================================================

            positions.forEachIndexed {
                    index,
                    position ->

                val selected =
                    selectedDots.contains(index)


                // Outer circle
                drawCircle(
                    color = dotColor,

                    radius =
                        size.minDimension * 0.053f,

                    center = position
                )


                // Inner background
                drawCircle(
                    color = backgroundColor,

                    radius =
                        size.minDimension * 0.041f,

                    center = position
                )


                // Center dot
                drawCircle(
                    color =
                        if (selected) {
                            Color.White
                        } else {
                            dotColor
                        },

                    radius =
                        size.minDimension * 0.027f,

                    center = position
                )
            }
        }
    }
}


// ====================================================================
// GRID POSITIONS
// ====================================================================

private fun getGridPositions(
    width: Float,
    height: Float
): List<Offset> {

    val x1 = width * 0.1667f
    val x2 = width * 0.5f
    val x3 = width * 0.8333f

    val y1 = height * 0.1667f
    val y2 = height * 0.5f
    val y3 = height * 0.8333f

    return listOf(

        Offset(x1, y1),
        Offset(x2, y1),
        Offset(x3, y1),

        Offset(x1, y2),
        Offset(x2, y2),
        Offset(x3, y2),

        Offset(x1, y3),
        Offset(x2, y3),
        Offset(x3, y3)
    )
}


// ====================================================================
// FIND DOT
// ====================================================================

private fun findDot(
    touch: Offset,
    width: Float,
    height: Float
): Int? {

    val positions =
        getGridPositions(
            width = width,
            height = height
        )


    positions.forEachIndexed {
            index,
            dot ->

        val dx =
            touch.x - dot.x

        val dy =
            touch.y - dot.y

        val distance =
            sqrt(
                dx * dx +
                        dy * dy
            )


        // Touch area also scales with grid size.
        val touchRadius =
            min(width, height) * 0.17f


        if (distance <= touchRadius) {
            return index
        }
    }


    return null
}

