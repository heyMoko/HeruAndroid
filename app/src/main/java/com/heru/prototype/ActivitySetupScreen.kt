package com.heru.prototype

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heru.prototype.domain.ScriptCatalog

@Composable
internal fun ActivitySetupScreen(model: HeruViewModel) {
    val context = LocalContext.current
    var cameraNotice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) model.chooseSelfView(false)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        model.chooseSelfView(it)
        cameraNotice = if (it) null else "카메라 없이도 해루와 통화할 수 있어요."
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            TextButton(onClick = model::closeCallSetup) { Text("‹ 오늘로 돌아가기") }
            Text("${model.routine.icon} ${model.routine.label} 도움 요청", color = MainText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("대사를 확인하고 아이에게 맞는 표현을 골라 주세요.", color = SubtleText, fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
            Text("표현 강도", color = MainText, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { value ->
                    val active = model.level == value
                    Surface(color = if (active) PrimaryBlue else CardBlue, shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 74.dp).testTag("level_$value")
                            .semantics { role = Role.RadioButton; selected = active }.clickable { model.chooseLevel(value) }) {
                        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$value", color = if (active) Background else MainText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(ScriptCatalog.labels[value - 1], color = if (active) Background else SubtleText, fontSize = 10.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(color = CardBlue, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, BorderBlue)) {
                Column(Modifier.padding(20.dp)) {
                    Text("해루가 이렇게 말해요", color = PrimaryBlue, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(ScriptCatalog.main(model.level, model.guardian, model.routine), color = MainText,
                        fontSize = 18.sp, lineHeight = 29.sp, modifier = Modifier.testTag("script"))
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("아이 모습도 함께 보기", color = MainText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("카메라 미리보기 · 저장하거나 보내지 않아요", color = SubtleText, fontSize = 11.sp)
                }
                Switch(checked = model.selfViewEnabled, onCheckedChange = { enabled ->
                    cameraNotice = null
                    if (!enabled) model.chooseSelfView(false)
                    else if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
                        model.chooseSelfView(false); cameraNotice = "이 기기에는 전면카메라가 없어요."
                    }
                    else if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) model.chooseSelfView(true)
                    else permission.launch(Manifest.permission.CAMERA)
                })
            }
            cameraNotice?.let { Text(it, color = SubtleText, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp)) }
            if (!model.routine.voiceReady) Text("음성은 준비 중이에요. 화면의 대사로 전체 통화 흐름을 볼 수 있어요.", color = SubtleText, fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 12.dp))
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            OutlinedButton(onClick = model::preview, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) {
                Text(if (model.previewing) "미리보기 멈추기" else if (model.routine.voiceReady) "대사 미리 듣기" else "대사 화면 미리 보기")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = model::start, enabled = !model.busy, modifier = Modifier.fillMaxWidth().height(54.dp).testTag("start"), shape = RoundedCornerShape(16.dp)) {
                Text("해루에게 전화하기", fontWeight = FontWeight.Bold)
            }
        }
    }
}
