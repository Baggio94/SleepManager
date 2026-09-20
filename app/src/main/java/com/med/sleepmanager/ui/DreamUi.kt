package com.med.sleepmanager.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.dreamBackground(): Modifier {
    val dark = isSystemInDarkTheme()
    val top = MaterialTheme.colorScheme.background
    val bottom =
        if (dark) androidx.compose.ui.graphics.Color(0xFF111A34)
        else androidx.compose.ui.graphics.Color(0xFFDDEBFF)
    val star =
        if (dark) androidx.compose.ui.graphics.Color(0xFFDDE7FF)
        else androidx.compose.ui.graphics.Color(0xFF5B72E8)
    val moon =
        if (dark) androidx.compose.ui.graphics.Color(0xFFF3D98B)
        else androidx.compose.ui.graphics.Color(0xFF88A8FF)

    return this
        .background(Brush.verticalGradient(listOf(top, bottom)))
        .drawBehind {
            val stars = listOf(
                0.08f to 0.14f,
                0.18f to 0.08f,
                0.32f to 0.18f,
                0.47f to 0.10f,
                0.62f to 0.20f,
                0.73f to 0.08f,
                0.82f to 0.25f,
                0.94f to 0.18f,
                0.12f to 0.58f,
                0.88f to 0.66f,
                0.26f to 0.82f,
                0.70f to 0.88f
            )
            stars.forEachIndexed { index, (x, y) ->
                drawCircle(
                    color = star.copy(alpha = if (dark) 0.18f else 0.12f),
                    radius = if (index % 3 == 0) 2.4f else 1.5f,
                    center = Offset(size.width * x, size.height * y)
                )
            }

            val radius = size.minDimension * 0.045f
            val center = Offset(size.width * 0.91f, size.height * 0.10f)
            drawCircle(
                color = moon.copy(alpha = if (dark) 0.17f else 0.11f),
                radius = radius,
                center = center
            )
            drawCircle(
                color = top,
                radius = radius * 0.86f,
                center = Offset(
                    center.x + radius * 0.38f,
                    center.y - radius * 0.16f
                )
            )
        }
}

@Composable
fun SleepMascot(
    modifier: Modifier = Modifier
) {
    val moon = MaterialTheme.colorScheme.primaryContainer
    val face = MaterialTheme.colorScheme.onPrimaryContainer
    val star = MaterialTheme.colorScheme.secondary

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val d = size.minDimension
            val center = Offset(d * 0.44f, d * 0.58f)
            val radius = d * 0.30f

            drawCircle(
                color = moon,
                radius = radius,
                center = center
            )

            val eyeY = center.y - radius * 0.12f
            val eyeHalf = radius * 0.16f
            val eyeOffset = radius * 0.34f
            val eyeStroke = (d * 0.028f).coerceAtLeast(1.5f)

            drawLine(
                color = face,
                start = Offset(center.x - eyeOffset - eyeHalf, eyeY),
                end = Offset(center.x - eyeOffset + eyeHalf, eyeY + radius * 0.03f),
                strokeWidth = eyeStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = face,
                start = Offset(center.x + eyeOffset - eyeHalf, eyeY + radius * 0.03f),
                end = Offset(center.x + eyeOffset + eyeHalf, eyeY),
                strokeWidth = eyeStroke,
                cap = StrokeCap.Round
            )

            drawArc(
                color = face.copy(alpha = 0.80f),
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(
                    center.x - radius * 0.18f,
                    center.y + radius * 0.05f
                ),
                size = Size(radius * 0.36f, radius * 0.24f),
                style = Stroke(width = eyeStroke * 0.72f)
            )

            val starCenter = Offset(d * 0.78f, d * 0.30f)
            val starArm = d * 0.055f
            drawLine(
                color = star,
                start = Offset(starCenter.x - starArm, starCenter.y),
                end = Offset(starCenter.x + starArm, starCenter.y),
                strokeWidth = eyeStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = star,
                start = Offset(starCenter.x, starCenter.y - starArm),
                end = Offset(starCenter.x, starCenter.y + starArm),
                strokeWidth = eyeStroke,
                cap = StrokeCap.Round
            )
        }

        Text(
            "zZ",
            modifier = Modifier.align(Alignment.TopEnd),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun HorizontalBatteryGauge(
    percent: Int?,
    charging: Boolean,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val normalized = (percent ?: 0).coerceIn(0, 100)
    val fillFraction by animateFloatAsState(
        targetValue = normalized / 100f,
        label = "Battery level"
    )
    val levelColor = when {
        charging -> MaterialTheme.colorScheme.secondary
        percent == null -> MaterialTheme.colorScheme.outline
        normalized <= 15 -> MaterialTheme.colorScheme.error
        normalized <= 30 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .semantics {
                contentDescription =
                    if (percent == null) {
                        "Battery level unavailable. Open battery stats."
                    } else {
                        "Battery $percent percent. Open battery stats."
                    }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(58.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .border(
                    width = 2.dp,
                    color = levelColor.copy(alpha = 0.72f),
                    shape = shape
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fillFraction)
                    .background(levelColor.copy(alpha = 0.27f))
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        percent?.let { "$it%" } ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    if (charging) "Charging" else "Stats ›",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = levelColor
                )
            }
        }

        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .width(7.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(0.dp, 5.dp, 5.dp, 0.dp))
                .background(levelColor.copy(alpha = 0.72f))
        )
    }
}
