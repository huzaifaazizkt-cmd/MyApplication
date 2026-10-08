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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.Design.components.NumberPad
import com.example.myapplication.R
import kotlin.math.min
import kotlin.math.sqrt

@Composable
fun PinCreateScreen(onNext: (String, String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var pinLength by remember { mutableIntStateOf(4) }
    var authType by remember { mutableStateOf("4") }
    var expanded by remember { mutableStateOf(false) }
    var selectedDots by remember { mutableStateOf<List<Int>>(emptyList()) }

    val isPattern = authType == "pattern"
    val backgroundColor = Color(0xFF189FFF)
    val patternDotColor = Color(0xFF83CCFF)

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
                .padding(start = 26.dp, end = 26.dp, top = 18.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                                interactionSource = remember { MutableInteractionSource() }
                            ) { expanded = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (authType) {
                                "4" -> stringResource(R.string.four_digit_pin)
                                "6" -> stringResource(R.string.six_digit_pin)
                                "pattern" -> stringResource(R.string.pattern)
                                else -> stringResource(R.string.four_digit_pin)
                            },
                            color = Color.White,
                            fontSize = 14.sp,
                            // FIX: lamba text ek line me, end me "..."
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 240.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.four_digit_pin),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.six_digit_pin),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.pattern),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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

            Spacer(modifier = Modifier.height(22.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(31.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(21.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "1", color = backgroundColor, fontSize = 15.sp)
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
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "2", color = Color.White, fontSize = 12.sp)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (!isPattern) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.create_pin, pinLength),
                            color = Color.White,
                            fontSize = 18.sp,
                            // FIX: lamba title 2 lines tak, center me
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            repeat(pinLength) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .border(1.5.dp, Color.White, CircleShape)
                                        .background(
                                            if (index < pin.length) Color.White else Color.Transparent,
                                            CircleShape
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        NumberPad(
                            onNumberClick = { number ->
                                if (pin.length < pinLength) pin += number
                            },
                            onDelete = {
                                if (pin.isNotEmpty()) pin = pin.dropLast(1)
                            }
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.create_pattern),
                            color = Color.White,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = stringResource(R.string.connect_four_dots),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        BoxWithConstraints(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            val availableWidth = maxWidth - 20.dp
                            val gridSize = minOf(availableWidth, 330.dp)

                            PatternGrid(
                                modifier = Modifier.size(gridSize),
                                selectedDots = selectedDots,
                                onPatternChanged = { dots -> selectedDots = dots },
                                dotColor = patternDotColor,
                                backgroundColor = backgroundColor
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
                // FIX: spacing 30 se 12 kiya taake lamba text ko jagah mile
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.reset),
                    color = if (
                        if (isPattern) selectedDots.isEmpty() else pin.isEmpty()
                    ) Color.White.copy(alpha = 0.35f) else Color.White,
                    fontSize = 20.sp,
                    // FIX: ek line + "..."; weight se dono ko jagah milti hai
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable(
                            enabled = if (isPattern) selectedDots.isNotEmpty() else pin.isNotEmpty(),
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            if (isPattern) selectedDots = emptyList() else pin = ""
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )

                Text(
                    text = stringResource(R.string.continue_text),
                    color = if (
                        if (isPattern) selectedDots.size >= 4 else pin.length == pinLength
                    ) Color.White else Color.White.copy(alpha = 0.35f),
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable(
                            enabled = if (isPattern) selectedDots.size >= 4 else pin.length == pinLength,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            if (isPattern) {
                                onNext("pattern", selectedDots.joinToString("-"))
                            } else {
                                onNext("pin", pin)
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun PatternGrid(
    modifier: Modifier = Modifier,
    selectedDots: List<Int>,
    onPatternChanged: (List<Int>) -> Unit,
    dotColor: Color,
    backgroundColor: Color
) {
    val latestOnPatternChanged by rememberUpdatedState(onPatternChanged)

    Box(
        modifier = modifier.pointerInput(Unit) {
            var currentDots = mutableListOf<Int>()

            detectDragGestures(
                onDragStart = { offset ->
                    currentDots = mutableListOf()

                    val dot = findDot(
                        touch = offset,
                        width = size.width.toFloat(),
                        height = size.height.toFloat()
                    )

                    if (dot != null) {
                        currentDots.add(dot)
                        latestOnPatternChanged(currentDots.toList())
                    }
                },
                onDrag = { change, _ ->
                    change.consume()

                    val dot = findDot(
                        touch = change.position,
                        width = size.width.toFloat(),
                        height = size.height.toFloat()
                    )

                    if (dot != null && !currentDots.contains(dot)) {
                        currentDots.add(dot)
                        latestOnPatternChanged(currentDots.toList())
                    }
                },
                onDragEnd = {},
                onDragCancel = {}
            )
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val positions = getGridPositions(size.width, size.height)

            if (selectedDots.size >= 2) {
                for (i in 0 until selectedDots.size - 1) {
                    drawLine(
                        color = Color.White,
                        start = positions[selectedDots[i]],
                        end = positions[selectedDots[i + 1]],
                        strokeWidth = size.minDimension * 0.024f
                    )
                }
            }

            positions.forEachIndexed { index, position ->
                val selected = selectedDots.contains(index)

                drawCircle(
                    color = dotColor,
                    radius = size.minDimension * 0.053f,
                    center = position
                )

                drawCircle(
                    color = backgroundColor,
                    radius = size.minDimension * 0.041f,
                    center = position
                )

                drawCircle(
                    color = if (selected) Color.White else dotColor,
                    radius = size.minDimension * 0.027f,
                    center = position
                )
            }
        }
    }
}

private fun getGridPositions(width: Float, height: Float): List<Offset> {
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

private fun findDot(touch: Offset, width: Float, height: Float): Int? {
    val positions = getGridPositions(width, height)

    positions.forEachIndexed { index, dot ->
        val dx = touch.x - dot.x
        val dy = touch.y - dot.y
        val distance = sqrt(dx * dx + dy * dy)
        val touchRadius = min(width, height) * 0.17f

        if (distance <= touchRadius) return index
    }

    return null
}