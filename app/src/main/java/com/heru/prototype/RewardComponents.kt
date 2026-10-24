package com.heru.prototype

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heru.prototype.data.readRewardImage
import com.heru.prototype.domain.RewardGoal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun RewardPhoto(uri: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { uri?.let { runCatching { readRewardImage(context, it, 768)?.asImageBitmap() }.getOrNull() } }
    }
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(SoftBlue), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!, "보상 사진", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Text("🎁", fontSize = 30.sp)
    }
}

@Composable
internal fun GoalCard(goal: RewardGoal, totalStars: Int, modifier: Modifier = Modifier) {
    Surface(color = Background, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, BorderBlue), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RewardPhoto(goal.imageUri, Modifier.size(68.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("지금 도전하는 선물", color = SubtleText, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(goal.title, color = MainText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { goal.progress(totalStars).toFloat() / goal.target },
                    color = PrimaryBlue, trackColor = SoftBlue, modifier = Modifier.fillMaxWidth().height(7.dp))
                Spacer(Modifier.height(6.dp))
                Text(if (goal.reached(totalStars)) "목표를 달성했어요!" else "별 ${goal.progress(totalStars)} / ${goal.target}개",
                    color = DeepBlue, fontSize = 12.sp)
            }
        }
    }
}
