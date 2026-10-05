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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.Design.components.NumberPad
import kotlin.math.sqrt
import com.example.myapplication.R

@Composable
fun PinCreateScreen(
    onNext: (String, String) -> Unit
) {

    var pin by remember {
        mutableStateOf("")
    }

    // DEFAULT = 4 DIGIT PIN
    var pinLength by remember {
        mutableStateOf(4)
    }

    // DEFAULT = 4 DIGIT PIN
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


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(
                    start = 26.dp,
                    end = 26.dp,
                    top = 24.dp,
                    bottom = 20.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // =========================================================
            // PIN TYPE DROPDOWN
            // =========================================================

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


                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = {
                            expanded = false
                        }
                    ) {

                        // =================================================
                        // 4 DIGIT PIN
                        // =================================================

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


                        // =================================================
                        // 6 DIGIT PIN
                        // =================================================

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


                        // =================================================
                        // PATTERN
                        // =================================================

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
                modifier = Modifier.height(38.dp)
            )


            // =========================================================
            // STEP INDICATOR
            // =========================================================

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


            Spacer(
                modifier = Modifier.height(65.dp)
            )


            // =========================================================
            // PIN SCREEN
            // =========================================================

            if (!isPattern) {

                Text(
                    text = stringResource(
                        R.string.create_pin,
                        pinLength
                    ),
                    color = Color.White,
                    fontSize = 18.sp
                )


                Spacer(
                    modifier = Modifier.height(18.dp)
                )


                // =====================================================
                // PIN DOTS
                // =====================================================

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
                                    if (index < pin.length)
                                        Color.White
                                    else
                                        Color.Transparent,
                                    CircleShape
                                )
                        )
                    }
                }


                Spacer(
                    modifier = Modifier.height(82.dp)
                )


                // =====================================================
                // NUMBER PAD
                // =====================================================

                NumberPad(

                    onNumberClick = { number ->

                        if (pin.length < pinLength) {
                            pin += number
                        }
                    },

                    onDelete = {

                        if (pin.isNotEmpty()) {
                            pin = pin.dropLast(1)
                        }
                    }
                )


                Spacer(
                    modifier = Modifier.height(55.dp)
                )


                // =====================================================
                // RESET + CONTINUE
                // =====================================================

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 30.dp),

                    horizontalArrangement =
                        Arrangement.spacedBy(
                            30.dp,
                            Alignment.End
                        )
                ) {

                    // =================================================
                    // RESET
                    // =================================================

                    Text(
                        text = stringResource(
                            R.string.reset
                        ),

                        color =
                            if (pin.isEmpty())
                                Color.White.copy(alpha = 0.35f)
                            else
                                Color.White,

                        fontSize = 20.sp,

                        modifier =
                            Modifier
                                .clickable(
                                    enabled =
                                        pin.isNotEmpty(),

                                    indication = null,

                                    interactionSource =
                                        remember {
                                            MutableInteractionSource()
                                        }
                                ) {

                                    pin = ""
                                }
                                .padding(10.dp)
                    )


                    // =================================================
                    // CONTINUE
                    // =================================================

                    Text(
                        text = stringResource(
                            R.string.continue_text
                        ),

                        color =
                            if (pin.length == pinLength)
                                Color.White
                            else
                                Color.White.copy(alpha = 0.35f),

                        fontSize = 20.sp,

                        modifier =
                            Modifier
                                .clickable(
                                    enabled =
                                        pin.length == pinLength,

                                    indication = null,

                                    interactionSource =
                                        remember {
                                            MutableInteractionSource()
                                        }
                                ) {

                                    onNext(
                                        "pin",
                                        pin
                                    )
                                }
                                .padding(10.dp)
                    )
                }

            } else {

                // =========================================================
                // PATTERN SCREEN
                // =========================================================

                Text(
                    text = stringResource(
                        R.string.create_pattern
                    ),
                    color = Color.White,
                    fontSize = 18.sp
                )


                Spacer(
                    modifier = Modifier.height(18.dp)
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
                    modifier = Modifier.height(82.dp)
                )


                // =====================================================
                // PATTERN GRID
                // =====================================================

                PatternGrid(
                    selectedDots = selectedDots,

                    onPatternChanged = { dots ->
                        selectedDots = dots
                    },

                    dotColor = patternDotColor,

                    backgroundColor = backgroundColor
                )


                Spacer(
                    modifier = Modifier.height(55.dp)
                )


                // =====================================================
                // RESET + CONTINUE
                // =====================================================

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 30.dp),

                    horizontalArrangement =
                        Arrangement.spacedBy(
                            30.dp,
                            Alignment.End
                        )
                ) {

                    // =================================================
                    // RESET
                    // =================================================

                    Text(
                        text = stringResource(
                            R.string.reset
                        ),

                        color =
                            if (selectedDots.isEmpty())
                                Color.White.copy(alpha = 0.35f)
                            else
                                Color.White,

                        fontSize = 20.sp,

                        modifier =
                            Modifier
                                .clickable(
                                    enabled =
                                        selectedDots.isNotEmpty(),

                                    indication = null,

                                    interactionSource =
                                        remember {
                                            MutableInteractionSource()
                                        }
                                ) {

                                    selectedDots =
                                        emptyList()
                                }
                                .padding(10.dp)
                    )


                    // =================================================
                    // CONTINUE
                    // =================================================

                    Text(
                        text = stringResource(
                            R.string.continue_text
                        ),

                        color =
                            if (selectedDots.size >= 4)
                                Color.White
                            else
                                Color.White.copy(alpha = 0.35f),

                        fontSize = 20.sp,

                        modifier =
                            Modifier
                                .clickable(
                                    enabled =
                                        selectedDots.size >= 4,

                                    indication = null,

                                    interactionSource =
                                        remember {
                                            MutableInteractionSource()
                                        }
                                ) {

                                    onNext(
                                        "pattern",
                                        selectedDots.joinToString("-")
                                    )
                                }
                                .padding(10.dp)
                    )
                }
            }
        }
    }
}


// =====================================================================
// PATTERN GRID
// =====================================================================

@Composable
fun PatternGrid(
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
        modifier = Modifier
            .size(330.dp)
            .pointerInput(Unit) {

                var currentDots =
                    mutableListOf<Int>()


                detectDragGestures(

                    onDragStart = { offset ->

                        currentDots =
                            mutableListOf()

                        val dot =
                            findDot(
                                offset,
                                size.width.toFloat(),
                                size.height.toFloat()
                            )

                        if (dot != null) {

                            currentDots.add(dot)

                            latestOnPatternChanged(
                                currentDots.toList()
                            )
                        }
                    },


                    onDrag = { change, _ ->

                        change.consume()

                        val dot =
                            findDot(
                                change.position,
                                size.width.toFloat(),
                                size.height.toFloat()
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
                    size.width,
                    size.height
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

                        strokeWidth = 8f
                    )
                }
            }


            // =========================================================
            // PATTERN DOTS
            // =========================================================

            positions.forEachIndexed {
                    index,
                    position ->

                val selected =
                    selectedDots.contains(index)


                // Outer circle
                drawCircle(
                    color = dotColor,
                    radius = 17.5.dp.toPx(),
                    center = position
                )


                // Inner background
                drawCircle(
                    color = backgroundColor,
                    radius = 13.5.dp.toPx(),
                    center = position
                )


                // Center dot
                drawCircle(
                    color =
                        if (selected)
                            Color.White
                        else
                            dotColor,

                    radius = 9.dp.toPx(),

                    center = position
                )
            }
        }
    }
}


// =====================================================================
// GRID POSITIONS
// =====================================================================

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


// =====================================================================
// FIND DOT
// =====================================================================

private fun findDot(
    touch: Offset,
    width: Float,
    height: Float
): Int? {

    val positions =
        getGridPositions(
            width,
            height
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


        if (distance <= 55f) {
            return index
        }
    }


    return null
}