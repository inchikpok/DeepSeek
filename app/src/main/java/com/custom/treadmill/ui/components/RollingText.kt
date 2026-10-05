package com.custom.treadmill.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit

/**
 * Текст с эффектом «прокрутки»: старое значение уезжает вверх,
 * новое приезжает снизу.
 */
@Composable
fun RollingText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    durationMs: Int = 180
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(
                animationSpec = tween(durationMs),
                initialOffsetY = { it }
            ) + fadeIn(tween(durationMs))) togetherWith
                    (slideOutVertically(
                        animationSpec = tween(durationMs),
                        targetOffsetY = { -it }
                    ) + fadeOut(tween(durationMs)))
        },
        label = "rolling"
    ) { value ->
        Text(
            value,
            modifier = modifier,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            textAlign = textAlign
        )
    }
}
