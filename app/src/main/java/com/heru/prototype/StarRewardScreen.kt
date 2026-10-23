package com.heru.prototype

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TRAIL_LENGTH = 7

@Composable
internal fun StarRewardScreen(model: HeruViewModel) {
    var accepted by remember { mutableStateOf(false) }
    var trailLit by remember { mutableStateOf(false) }
    val currentTrail = model.stars % TRAIL_LENGTH

    LaunchedEffect(accepted) {
        if (accepted) {
            delay(620)
            trailLit = true
            delay(1200)
            model.awardStar()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(color = SoftBlue, shape = RoundedCornerShape(50)) {
            Text("해루의 선물", Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                color = DeepBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text("식사 약속을 지켰구나!", color = MainText,
            fontSize = 27.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(if (accepted) "해루가 별 하나를 선물했어" else "해루의 손에서 별을 받아 봐",
            color = SubtleText, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))

        RewardScene(accepted = accepted, onAccept = { accepted = true })

        Spacer(Modifier.height(14.dp))
        Text(if (accepted) "반짝! 오늘의 별이 별길에 놓였어" else "반짝이는 별을 톡 눌러 줘",
            color = DeepBlue, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))

        Surface(color = Background, shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, BorderBlue), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("우리의 별길", color = MainText, fontSize = 16.sp,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("🎁", fontSize = 23.sp)
                }
                Spacer(Modifier.height(16.dp))
                StarTrail(currentTrail + if (trailLit) 1 else 0)
                Spacer(Modifier.height(14.dp))
                Text("지금까지 모은 별 ${model.stars + if (trailLit) 1 else 0}개",
                    color = SubtleText, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun RewardScene(accepted: Boolean, onAccept: () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    var starReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        entered = true
        delay(380)
        starReady = true
    }
    val characterScale by animateFloatAsState(
        if (entered) 1f else .93f, tween(500), label = "rewardCharacterScale"
    )
    val characterAlpha by animateFloatAsState(
        if (entered) 1f else 0f, tween(500), label = "rewardCharacterAlpha"
    )
    val starAlpha by animateFloatAsState(
        if (accepted) 0f else if (starReady) 1f else 0f,
        tween(if (accepted) 580 else 330), label = "rewardStarAlpha"
    )
    val starBurst by animateFloatAsState(
        if (accepted) 1.8f else 1f, tween(580), label = "rewardStarBurst"
    )
    val pulse by rememberInfiniteTransition(label = "rewardStarPulse").animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "rewardStarPulseScale"
    )

    BoxWithConstraints(
        Modifier.fillMaxWidth().height(370.dp).clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(SoftBlue, CardBlue, Background)))
            .border(1.dp, BorderBlue, RoundedCornerShape(28.dp))
    ) {
        // The authored image is 2:3. Keep the star centered over its open palm at every width.
        val imageWidth = minOf(maxWidth, maxHeight * (2f / 3f))
        val imageHeight = imageWidth * 1.5f
        val imageLeft = (maxWidth - imageWidth) / 2f
        val imageTop = (maxHeight - imageHeight) / 2f
        val starX = imageLeft + imageWidth * .18f
        val starY = imageTop + imageHeight * .23f

        Image(
            painter = painterResource(R.drawable.heru_guardian_reward),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = characterScale
                scaleY = characterScale
                alpha = characterAlpha
            }
        )
        Box(
            Modifier.offset(x = starX - 44.dp, y = starY - 44.dp)
                .size(88.dp)
                .graphicsLayer {
                    scaleX = starBurst * pulse
                    scaleY = starBurst * pulse
                    alpha = starAlpha
                }
                .testTag("award_star")
                .semantics { contentDescription = "해루의 손에 있는 별 받기" }
                .clickable(enabled = starReady && !accepted, role = Role.Button, onClick = onAccept),
            contentAlignment = Alignment.Center
        ) {
            BlueStar(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun BlueStar(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0x809DE4FF), .65f to Color(0x333178DF),
                1f to Color.Transparent,
                center = center, radius = size.minDimension * .48f
            ),
            radius = size.minDimension * .48f, center = center
        )
        val outer = size.minDimension * .30f
        val inner = outer * .46f
        val star = Path().apply {
            repeat(10) { point ->
                val angle = -PI / 2 + point * PI / 5
                val radius = if (point % 2 == 0) outer else inner
                val x = center.x + cos(angle).toFloat() * radius
                val y = center.y + sin(angle).toFloat() * radius
                if (point == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(star, brush = Brush.verticalGradient(
            listOf(Color(0xFFB3ECFF), Color(0xFF4AB8FF), DeepBlue),
            startY = center.y - outer, endY = center.y + outer
        ))
        drawPath(star, color = Color.White.copy(alpha = .9f),
            style = Stroke(width = size.minDimension * .018f))
        drawCircle(Color.White.copy(alpha = .8f), radius = size.minDimension * .035f,
            center = Offset(center.x - outer * .15f, center.y - outer * .22f))
    }
}

@Composable
private fun StarTrail(filled: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        repeat(TRAIL_LENGTH) { index ->
            val lit = index < filled
            Surface(
                color = if (lit) PrimaryBlue else SoftBlue,
                shape = CircleShape,
                border = if (lit) null else BorderStroke(1.dp, BorderBlue),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("★", color = if (lit) Background else BorderBlue, fontSize = 16.sp)
                }
            }
            if (index < TRAIL_LENGTH - 1) {
                Box(Modifier.weight(1f).height(2.dp)
                    .background(if (index + 1 < filled) PrimaryBlue else BorderBlue))
            }
        }
    }
}
