package com.heru.prototype

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heru.prototype.domain.*
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun ActivityHistoryScreen(model: HeruViewModel) {
    var selectedDay by remember(model.activeChild.id, model.today) { mutableStateOf(model.today) }
    val weekStart = model.today.minusDays(6)
    val weekly = model.records.filter { it.finishedAt.localDate() in weekStart..model.today }
    val completed = weekly.filter { it.outcome == Outcome.COMPLETED }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
        Text("함께 쌓은 기록", color = MainText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("${model.activeChild.name}의 작은 성취를 기억해요.", color = SubtleText, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        Surface(color = SoftBlue, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("최근 7일", color = DeepBlue, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("함께 해낸 약속 ${completed.size}번", color = MainText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("해루 경고 없이 기록한 활동 ${completed.count { !it.warningPlayed }}번", color = SubtleText, fontSize = 13.sp)
                Text("아이 혼자 했는지를 판단하는 수치는 아니에요.", color = SubtleText, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0L..6L).forEach { offset ->
                val date = weekStart.plusDays(offset)
                FilterChip(selected = selectedDay == date, onClick = { selectedDay = date },
                    label = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(date.format(DateTimeFormatter.ofPattern("E", java.util.Locale.KOREAN)), fontSize = 11.sp)
                        Text("${date.dayOfMonth}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("${model.records.completionsOn(date)}번", fontSize = 10.sp)
                    } })
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(selectedDay.format(DateTimeFormatter.ofPattern("M월 d일")), color = MainText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { selectedDay = selectedDay.minusDays(1) }) { Text("이전 날") }
            TextButton(onClick = { selectedDay = selectedDay.plusDays(1) }, enabled = selectedDay < model.today) { Text("다음 날") }
        }
        val rows = model.records.filter { it.finishedAt.localDate() == selectedDay }
        if (rows.isEmpty()) Text("이날은 남긴 기록이 없어요.\n다음 작은 약속을 함께해 봐요.", color = SubtleText, fontSize = 15.sp,
            lineHeight = 24.sp, modifier = Modifier.padding(vertical = 20.dp))
        rows.forEach { record ->
            Surface(color = Background, border = BorderStroke(1.dp, BorderBlue), shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(record.routine.icon, fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${record.routine.label} · ${record.outcome.label}", color = MainText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        val source = when {
                            record.warningPlayed -> "해루 안내와 함께"
                            record.entryMode == EntryMode.PRAISE -> "칭찬으로 기록"
                            record.entryMode == EntryMode.REWARD -> "바로 별 선물"
                            else -> "사용 기록"
                        }
                        val time = Instant.ofEpochMilli(record.finishedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("a h:mm", java.util.Locale.KOREAN))
                        Text("$time · $source", color = SubtleText, fontSize = 11.sp)
                    }
                    if (record.starAwarded) Text("★ +1", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("기록과 사진은 이 기기에 보관돼요.", color = SubtleText, fontSize = 11.sp)
    }
}
