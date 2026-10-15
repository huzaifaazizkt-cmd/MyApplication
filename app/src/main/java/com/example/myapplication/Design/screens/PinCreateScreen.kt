package com.example.myapplication.Design.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
    val backgroundColor = Color.White
    val primaryBlue = Color(0xFF0396FF)
    val darkText = Color(0xFF555555)
    val gray = Color(0xFFCCCCCC)
    val patternDotColor = primaryBlue

    LimitFontScale {

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ---------- Responsive values ----------
            val isCompact = maxHeight < 640.dp || maxWidth < 360.dp

            val sidePadding = if (isCompact) 20.dp else 26.dp
            val titleSize = if (isCompact) 16.sp else 18.sp
            val subTitleSize = if (isCompact) 13.sp else 14.sp
            val actionSize = if (isCompact) 17.sp else 20.sp
            val stepLineWidth = if (isCompact) 70.dp else 100.dp

            val gridSize = minOf(
                maxWidth - sidePadding * 2,
                maxHeight * 0.42f,
                330.dp
            ).coerceAtLeast(200.dp)

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(
                        start = sidePadding,
                        end = sidePadding,
                        top = if (isCompact) 10.dp else 18.dp,
                        bottom = 6.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ---------- PIN TYPE DROPDOWN ----------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 35.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Row(
                            modifier = Modifier
                                .heightIn(min = 35.dp)
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
                                color = darkText,
                                fontSize = subTitleSize,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 240.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = darkText,
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

                Spacer(modifier = Modifier.height(if (isCompact) 14.dp else 22.dp))

                // ---------- STEP INDICATOR ----------
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(31.dp)
                            .background(Color(0xFFF2F2F2), CircleShape)
                            .border(2.dp,    color = Color(0xFF0396FF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(21.dp)
                                .background(Color(0xFF0396FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "1", color = Color.White, fontSize = 15.sp)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(stepLineWidth)
                            .height(2.dp)
                            .background(gray)
                    )

                    Box(
                        modifier = Modifier
                            .size(25.dp)
                            .border(2.dp, gray, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "2", color = Color(0xFF999999), fontSize = 12.sp)
                    }
                }

                // ---------- MIDDLE CONTENT (center + scroll agar jagah kam ho) ----------
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isPattern) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.create_pin, pinLength),
                                color = darkText,
                                fontSize = titleSize,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(if (isCompact) 10.dp else 14.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                repeat(pinLength) { index ->
                                    val isFilled = index < pin.length

                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(
                                                color = if (isFilled) primaryBlue else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .border(
                                                width = 1.5.dp,
                                                color = if (isFilled) primaryBlue else gray,
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(if (isCompact) 18.dp else 32.dp))

                            NumberPad(
                                onNumberClick = { number ->
                                    if (pin.length < pinLength) pin += number
                                },
                                onDelete = {
                                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                },
                                buttonColor = primaryBlue
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.create_pattern),
                                color = darkText,
                                fontSize = titleSize,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = stringResource(R.string.connect_four_dots),
                                color = Color(0xFF777777),
                                fontSize = subTitleSize,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 20.dp))

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

                // ---------- RESET / CONTINUE ----------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.reset),
                        color = if (
                            if (isPattern) selectedDots.isEmpty() else pin.isEmpty()
                        ) Color(0xFFBDBDBD) else primaryBlue,
                        fontSize = actionSize,
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
                        text = stringResource(R.string.confirm_text),
                        color = if (
                            if (isPattern) selectedDots.size >= 4 else pin.length == pinLength
                        ) primaryBlue else Color(0xFFBDBDBD),
                        fontSize = actionSize,
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
                        color = dotColor,
                        start = positions[selectedDots[i]],
                        end = positions[selectedDots[i + 1]],
                        strokeWidth = size.minDimension * 0.024f
                    )
                }
            }

            positions.forEachIndexed { index, position ->
                val selected = selectedDots.contains(index)

                drawCircle(
                    color = Color(0xFF0396FF),
                    radius = size.minDimension * 0.053f,
                    center = position
                )

                drawCircle(
                    color = backgroundColor,
                    radius = size.minDimension * 0.041f,
                    center = position
                )

                drawCircle(
                    color = if (selected) dotColor else Color(0xFF0396FF),
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