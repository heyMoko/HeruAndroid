package com.heru.prototype

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val rewardIdeas = listOf("놀이터 가기", "함께 책 읽기", "간식 고르기", "스티커 받기", "작은 장난감")

@Composable
internal fun RewardSetupScreen(model: HeruViewModel) {
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
        Text("기다려지는 선물", color = MainText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("${model.activeChild.name}와 함께 정한 작은 약속이에요.", color = SubtleText, fontSize = 13.sp)
        Spacer(Modifier.height(22.dp))
        GoalCard(model.goal, model.stars)
        Spacer(Modifier.height(16.dp))
        Surface(color = SoftBlue, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(if (model.goal.reached(model.stars)) "목표를 달성했어요!" else "목표까지 별 ${model.goal.target - model.goal.progress(model.stars)}개",
                    color = DeepBlue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("지금까지 모은 별은 모두 ${model.stars}개예요.", color = SubtleText, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text("함께 놀거나 책을 읽는 시간도 멋진 선물이 될 수 있어요.", color = SubtleText, fontSize = 12.sp, lineHeight = 19.sp)
            }
        }
        Spacer(Modifier.height(22.dp))
        Button(onClick = { editor = "edit" }, enabled = !model.busy,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("edit_reward"), shape = RoundedCornerShape(16.dp)) {
            Text("현재 목표 수정")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { editor = "new" }, enabled = !model.busy, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
            Text("새 보상 목표 만들기")
        }
        Spacer(Modifier.height(12.dp))
        Text("현재 목표를 수정하면 모은 별이 유지돼요. 새 목표를 만들면 이번 목표의 별은 0개부터 시작해요.",
            color = SubtleText, fontSize = 12.sp, lineHeight = 20.sp)
    }
    editor?.let { mode ->
        key(model.activeChild.id, model.goal.id, mode) {
            RewardEditor(model, newGoal = mode == "new", onDismiss = { editor = null }, onSaved = {
                editor = null; saved = !model.goalReached
            })
        }
    }
    if (saved) AlertDialog(onDismissRequest = { saved = false }, title = { Text("보상을 저장했어요") },
        text = { Text("${model.rewardTitle} · 목표 별 ${model.rewardTarget}개로 설정했어요.") },
        confirmButton = { TextButton(onClick = { saved = false }) { Text("확인") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RewardEditor(model: HeruViewModel, newGoal: Boolean, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(if (newGoal) "" else model.rewardTitle) }
    var imageUri by rememberSaveable { mutableStateOf(if (newGoal) null else model.rewardImageUri) }
    var target by rememberSaveable { mutableIntStateOf(if (newGoal) 8 else model.rewardTarget) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) model.importRewardPhoto(uri.toString()) { imageUri = it }
    }
    ModalBottomSheet(onDismissRequest = { if (!model.busy) onDismiss() }, containerColor = Background) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(if (newGoal) "새 보상 목표" else "현재 목표 수정", color = MainText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(if (newGoal) "새 목표의 별은 0개부터 시작해요. 지금까지 모은 전체 별은 그대로 남아요." else "보상 이름과 사진, 목표 개수를 바꿔도 이번 목표의 별은 유지돼요.",
                color = SubtleText, fontSize = 13.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(16.dp))
            Text("보상 아이디어", color = MainText, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rewardIdeas.forEach { idea -> FilterChip(selected = title == idea, onClick = { title = idea }, label = { Text(idea, fontSize = 12.sp) }) }
            }
            OutlinedTextField(value = title, onValueChange = { title = it.take(40) }, label = { Text("보상 이름") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("reward_name"))
            Spacer(Modifier.height(12.dp))
            if (imageUri != null) {
                RewardPhoto(imageUri, Modifier.fillMaxWidth().height(180.dp).testTag("reward_photo_preview"))
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !model.busy, modifier = Modifier.weight(1f)) { Text(if (imageUri == null) "보상 사진 첨부" else "다른 사진 선택") }
                if (imageUri != null) TextButton(onClick = { imageUri = null }, enabled = !model.busy) { Text("사진 빼기") }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("목표 별 개수", color = MainText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("작게 시작해도 괜찮아요.", color = SubtleText, fontSize = 12.sp)
                }
                OutlinedButton(onClick = { target-- }, enabled = target > 1 && !model.busy, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(42.dp)) { Text("−") }
                Text("${target}개", Modifier.padding(horizontal = 14.dp), color = DeepBlue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { target++ }, enabled = target < 30 && !model.busy, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(42.dp)) { Text("+") }
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { model.saveReward(title, imageUri, target, newGoal, onSaved) },
                enabled = title.isNotBlank() && !model.busy, modifier = Modifier.fillMaxWidth().height(54.dp).testTag("save_reward"), shape = RoundedCornerShape(16.dp)) {
                Text(if (model.busy) "저장 중…" else if (newGoal) "새 목표 시작" else "수정 내용 저장")
            }
        }
    }
}
