package com.heru.prototype

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.heru.prototype.domain.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChildPicker(model: HeruViewModel) {
    var open by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var editChild by remember { mutableStateOf<ChildProfile?>(null) }
    OutlinedButton(onClick = { open = true }, shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), enabled = !model.busy) {
        Text("${model.activeChild.name} ▾", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
    }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }, containerColor = Background) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("함께하는 아이", color = MainText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            model.children.forEach { child ->
                Surface(color = if (child.id == model.activeChild.id) SoftBlue else Background,
                    border = BorderStroke(1.dp, BorderBlue), shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(child.name, Modifier.weight(1f).clickable { model.selectChild(child.id); open = false }, color = MainText,
                            fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { editChild = child; open = false; editing = true }) { Text("수정") }
                        TextButton(onClick = { model.selectChild(child.id); open = false }) { Text(if (child.id == model.activeChild.id) "선택됨" else "선택") }
                    }
                }
            }
            Button(onClick = { editChild = null; open = false; editing = true }, modifier = Modifier.fillMaxWidth()) { Text("아이 추가") }
        }
    }
    if (editing) ChildEditor(model, editChild) { editing = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChildEditor(model: HeruViewModel, child: ChildProfile?, onDismiss: () -> Unit) {
    var name by rememberSaveable(child?.id) { mutableStateOf(child?.name ?: "") }
    var counts by rememberSaveable(child?.id) { mutableStateOf(Routine.entries.map { child?.plans?.get(it) ?: if (child == null) defaultPlans().getValue(it) else 0 }) }
    ModalBottomSheet(onDismissRequest = { if (!model.busy) onDismiss() }, containerColor = Background) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(if (child == null) "아이와 약속 만들기" else "아이와 오늘의 약속", color = MainText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("별명만으로 시작할 수 있어요. 활동 횟수는 우리 집에 맞게 정해 주세요.", color = SubtleText, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = name, onValueChange = { name = it.take(24) }, singleLine = true,
                label = { Text("아이 이름 또는 별명") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Routine.entries.forEachIndexed { index, routine ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = counts[index] > 0, onCheckedChange = { checked -> counts = counts.toMutableList().also { it[index] = if (checked) 1 else 0 } })
                    Text("${routine.icon} ${routine.label}", Modifier.weight(1f), color = MainText)
                    if (counts[index] > 0) {
                        TextButton(onClick = { counts = counts.toMutableList().also { it[index] = (it[index] - 1).coerceAtLeast(1) } },
                            enabled = counts[index] > 1 && !model.busy, contentPadding = PaddingValues(0.dp)) { Text("−") }
                        Text("${counts[index]}번", color = DeepBlue, fontSize = 13.sp)
                        TextButton(onClick = { counts = counts.toMutableList().also { it[index] = (it[index] + 1).coerceAtMost(3) } },
                            enabled = counts[index] < 3 && !model.busy, contentPadding = PaddingValues(0.dp)) { Text("+") }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                val plans = Routine.entries.mapIndexedNotNull { i, routine -> counts[i].takeIf { it > 0 }?.let { routine to it } }.toMap()
                model.saveChild(child?.id, name, plans, onDismiss)
            }, enabled = name.isNotBlank() && counts.any { it > 0 } && !model.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (model.busy) "저장 중…" else "저장")
            }
        }
    }
}
