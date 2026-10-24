package com.heru.prototype

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heru.prototype.domain.*
import java.time.format.DateTimeFormatter

@Composable
internal fun TodayScreen(model: HeruViewModel) {
    var rewardFor by remember { mutableStateOf<Routine?>(null) }
    var editPlans by remember { mutableStateOf(false) }
    val profile = model.activeChild
    val done = profile.plans.entries.sumOf { (routine, target) -> model.records.completionsOn(model.today, routine).coerceAtMost(target) }
    val target = profile.plans.values.sum()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
        Text(model.today.format(DateTimeFormatter.ofPattern("M월 d일 E요일", java.util.Locale.KOREAN)), color = SubtleText, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Text("작은 약속도\n해루와 함께", color = MainText, fontSize = 30.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Surface(color = SoftBlue, shape = RoundedCornerShape(22.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${profile.name}의 오늘", color = DeepBlue, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("함께 해낸 약속 $done / $target", color = MainText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("잘한 순간에는 바로 칭찬해 주세요.", color = SubtleText, fontSize = 12.sp)
                }
                Image(painterResource(R.drawable.heru_guardian_reward), null, Modifier.size(74.dp, 108.dp))
            }
        }
        if (model.pending.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Text("이어서 할 수 있어요", color = MainText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            model.pending.forEach { activity ->
                Surface(color = CardBlue, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderBlue),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(activity.routine.icon, fontSize = 23.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${activity.routine.label} · 진행 중", color = MainText, fontWeight = FontWeight.SemiBold)
                            Text(if (activity.startedAt.localDate() == model.today) "화면을 꺼도 여기 남아 있어요" else "이전 활동을 이어갈 수 있어요",
                                color = SubtleText, fontSize = 11.sp)
                        }
                        TextButton(onClick = { model.resumeActivity(activity.id) }, enabled = !model.busy,
                            modifier = Modifier.testTag("resume_${activity.routine.name}")) { Text("이어하기") }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("오늘의 약속", color = MainText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { editPlans = true }) { Text("활동 설정", fontSize = 12.sp) }
        }
        profile.plans.forEach { (routine, dailyTarget) ->
            val count = model.records.completionsOn(model.today, routine)
            Surface(color = Background, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, BorderBlue),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(routine.icon, fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(routine.label, color = MainText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(routine.action, color = SubtleText, fontSize = 12.sp)
                        }
                        Text(if (count >= dailyTarget) "오늘 ${count}번 ✓" else "$count / ${dailyTarget}번",
                            color = if (count >= dailyTarget) DeepBlue else SubtleText, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedButton(onClick = { model.prepareCall(routine) }, enabled = !model.busy,
                            modifier = Modifier.weight(1f).testTag("help_${routine.name}"), contentPadding = PaddingValues(horizontal = 3.dp),
                            shape = RoundedCornerShape(12.dp)) { Text("도움 요청", fontSize = 12.sp) }
                        OutlinedButton(onClick = { model.praise(routine) }, enabled = !model.busy,
                            modifier = Modifier.weight(1f).testTag("quick_praise_${routine.name}"), contentPadding = PaddingValues(horizontal = 3.dp),
                            shape = RoundedCornerShape(12.dp)) { Text("칭찬하기", fontSize = 12.sp) }
                        Button(onClick = { rewardFor = routine }, enabled = !model.busy,
                            modifier = Modifier.weight(1f).testTag("quick_star_${routine.name}"), contentPadding = PaddingValues(horizontal = 3.dp),
                            shape = RoundedCornerShape(12.dp)) { Text("별 주기", fontSize = 12.sp) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("조금씩 가까워지는 선물", color = MainText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        GoalCard(model.goal, model.stars, Modifier.clickable { model.selectTab(1) })
    }
    rewardFor?.let { selected ->
        AlertDialog(onDismissRequest = { rewardFor = null }, title = { Text("${selected.label} 약속을 해냈나요?") },
            text = { Text("부모가 확인한 활동에 별 하나를 선물해요. 해루의 별을 아이가 직접 받을 수 있어요.") },
            confirmButton = { TextButton(onClick = { rewardFor = null; model.reward(selected) }, enabled = !model.busy) { Text("네, 별 주기") } },
            dismissButton = { TextButton(onClick = { rewardFor = null }) { Text("아직이에요") } })
    }
    if (editPlans) ChildEditor(model, profile) { editPlans = false }
}
