package com.heru.prototype

import android.content.*
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.imageResource

import androidx.compose.ui.graphics.*

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.ViewModelProvider
import com.heru.prototype.domain.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

private val Ink = Color(0xFF141B2C)
private val Panel = Color(0xFF202A3E)
private val Muted = Color(0xFFA7B3C5)
private val Cream = Color(0xFFFFF3DF)
private val Apricot = Color(0xFFF3B77D)
private val Mint = Color(0xFFAAD9C5)

class MainActivity : ComponentActivity() {
    private lateinit var model: HeruViewModel
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { model.hostStopped() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        model = ViewModelProvider(this)[HeruViewModel::class.java]
        val filter = IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(noisy, filter)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(primary = Apricot, onPrimary = Ink,
                    background = Ink, surface = Panel, onSurface = Cream,
                    onSurfaceVariant = Muted, outline = Color(0xFF43516A))
            ) { HeruApp(model) }
        }
    }
    override fun onStop() { model.hostStopped(); super.onStop() }
    override fun onDestroy() { unregisterReceiver(noisy); super.onDestroy() }
}

@Composable
private fun HeruApp(model: HeruViewModel) {
    val session = model.session
    BackHandler(enabled = session != null || model.history) {
        if (session != null && session.phase !in listOf(Phase.RESULT, Phase.SAVED)) model.stop()
        else model.home()
    }
    Box(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Color(0xFF202C43), Ink, Ink))
    )) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("heru", color = Cream, fontSize = 28.sp, fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp)
                Spacer(Modifier.width(10.dp))
                Surface(color = Apricot.copy(alpha = .13f), shape = RoundedCornerShape(6.dp)) {
                    Text("시제품", Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        color = Apricot, fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                if (session == null && !model.history) {
                    TextButton(onClick = model::showHistory, modifier = Modifier.testTag("history")) {
                        Text("기록 ${model.records.size}", color = Muted)
                    }
                } else if (model.history) {
                    TextButton(onClick = model::home) { Text("닫기", color = Cream) }
                }
            }
            when {
                model.history -> HistoryScreen(model)
                session == null -> SetupScreen(model)
                session.phase == Phase.COUNTDOWN || session.phase == Phase.PRAISE_COUNTDOWN -> CountdownScreen(model)
                session.phase == Phase.SPEAKING || session.phase == Phase.PRAISE_SPEAKING || session.phase == Phase.CLOSING -> ChildModeScreen(model, session)
                session.phase == Phase.STAR_REWARD -> StarRewardScreen(model)
                session.phase == Phase.RESULT -> ResultScreen(model)
                session.phase == Phase.SAVED -> SavedScreen(model)
                else -> CallScreen(model, session)
            }
        }
    }
}

@Composable
private fun CountdownScreen(model: HeruViewModel) {
    Column(Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text(if (model.session?.phase == Phase.PRAISE_COUNTDOWN) "잠시 후 칭찬을 보여줄게요" else "잠시 후 해루를 보여줄게요", color = Cream, fontSize = 24.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Text("아이에게 화면을 보여주세요", color = Muted, fontSize = 16.sp)
        Spacer(Modifier.height(28.dp))
        Text("${model.countdownSeconds}", color = Apricot, fontSize = 96.sp,
            fontWeight = FontWeight.Black)
        Spacer(Modifier.height(28.dp))
        OutlinedButton(onClick = model::stop, modifier = Modifier.testTag("cancel_countdown")) {
            Text("취소")
        }
    }
}

@Composable
private fun ChildModeScreen(model: HeruViewModel, session: CallSession) {
    val speaking = session.phase in listOf(Phase.SPEAKING, Phase.PRAISE_SPEAKING, Phase.CLOSING)
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        HeruCharacter(speaking, Modifier.weight(1f).fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Surface(color = Panel, shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 210.dp)) {
            Text(model.sessionScript,
                Modifier.verticalScroll(rememberScrollState()).padding(18.dp), color = Cream,
                fontSize = 21.sp, lineHeight = 31.sp, textAlign = TextAlign.Center)
        }
    }
}
@Composable
private fun StarRewardScreen(model: HeruViewModel) {
    val progress = model.stars % 7
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("오늘도 잘했어요!", color = Cream, fontSize = 30.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("별을 눌러 해루에게 선물을 받아보세요", color = Muted,
            fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Surface(color = Apricot.copy(alpha = .16f), shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("이번 선물", color = Apricot, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text("🎁", fontSize = 52.sp)
                Text("별 7개를 모으면 선물이 열려요", color = Cream,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            (0 until 7).forEach { index ->
                Text(if (index < progress) "★" else "☆",
                    color = if (index < progress) Apricot else Muted,
                    fontSize = 27.sp)
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(onClick = model::awardStar, modifier = Modifier.size(180.dp)
            .testTag("award_star"), shape = CircleShape) {
            Text("★\n별 받기", textAlign = TextAlign.Center, fontSize = 24.sp,
                fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Text("지금까지 모은 별 ${model.stars}개", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun SetupScreen(model: HeruViewModel) {
    Column(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp).padding(bottom = 12.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("필요한 순간, 해루를 불러요", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Text("오늘의 약속을\n들려줄 시간", color = Cream, fontSize = 32.sp,
            fontWeight = FontWeight.Bold, lineHeight = 42.sp)
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Surface(color = Mint.copy(alpha = .12f), shape = RoundedCornerShape(8.dp)) {
                    Text("01  식사 준비", Modifier.padding(10.dp), color = Mint, fontSize = 12.sp)
                }
                Spacer(Modifier.height(14.dp))
                Text("장난감 내려놓고\n식탁에 앉기", color = Cream, fontSize = 19.sp,
                    lineHeight = 29.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("한 번에 행동 하나만", color = Muted, fontSize = 12.sp)
            }
            HeruCharacter(model.previewing, Modifier.size(width = 148.dp, height = 210.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("표현 강도", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("부모가 직접 선택해요", color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { level ->
                val selected = level == model.level
                Surface(
                    color = if (selected) Apricot else Panel,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 80.dp)
                        .testTag("level_$level")
                        .semantics { role = Role.RadioButton; this.selected = selected }
                        .clickable { model.chooseLevel(level) }
                ) {
                    Column(Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$level", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            color = if (selected) Ink else Cream)
                        Spacer(Modifier.height(4.dp))
                        Text(ScriptCatalog.labels[level - 1], fontSize = 10.sp,
                            color = if (selected) Ink else Muted, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text("${model.level}단계 · ${ScriptCatalog.labels[model.level - 1]}",
                    color = Apricot, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(ScriptCatalog.main(model.level, model.guardian), color = Cream,
                    fontSize = 16.sp, lineHeight = 27.sp, modifier = Modifier.testTag("script"))
                Spacer(Modifier.height(10.dp))
                Text("임시 합성 음성 · 도깨비 연기 녹음 전", color = Muted, fontSize = 11.sp)
            }
        }
    }
    Column(Modifier.fillMaxWidth().background(Ink)
        .padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 16.dp)) {
        model.message?.let { Message(it) }
        OutlinedButton(onClick = model::preview, modifier = Modifier.fillMaxWidth()
            .heightIn(min = 52.dp).testTag("preview"),
            border = BorderStroke(1.dp, Color(0xFF53617B))) {
            Text(if (model.previewing) "미리 듣기 멈추기" else "대사 미리 듣기", color = Cream)
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = model::start, modifier = Modifier.fillMaxWidth()
            .heightIn(min = 56.dp).testTag("start"), shape = RoundedCornerShape(16.dp)) {
            Text("해루 부르기", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text("이야기 속 도깨비의 상황극이에요. 먼저 부모가 들어보고 보여주세요.",
            color = Muted, fontSize = 11.sp, lineHeight = 18.sp)
    }
    }
}

@Composable
private fun CallScreen(model: HeruViewModel, session: CallSession) {
    val choosingPraise = session.phase == Phase.PRAISE_READY
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp).padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(18.dp))
        Text(if (choosingPraise) "식사 시작 · 칭찬 선택" else "식사 준비 · ${session.level}단계", color = Apricot, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Text(when (session.phase) {
            Phase.CLOSING -> "식사를 마무리하고 있어요"
            Phase.PRAISE_READY -> "어떤 칭찬을 들려줄까요?"
            Phase.SUCCESS_WAITING -> "잘했어요. 식사를 계속해 주세요"
            Phase.WAITING -> "행동을 시작할 시간을 주세요"
            else -> "해루가 말하고 있어요"
        }, color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center)
        if (!choosingPraise) {
            Spacer(Modifier.height(28.dp))
            HeruCharacter(false, Modifier.size(width = 180.dp, height = 240.dp))
            Spacer(Modifier.height(24.dp))
            Surface(color = Panel, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Text(model.sessionScript, Modifier.padding(22.dp), color = Cream,
                    fontSize = 20.sp, lineHeight = 33.sp, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("부모가 옆에서 함께 조작해 주세요", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(24.dp))
        if (session.phase == Phase.WAITING) {
            Button(onClick = model::finish, modifier = Modifier.fillMaxWidth()
                .heightIn(min = 56.dp).testTag("finish"), shape = RoundedCornerShape(16.dp)) {
                Text("행동을 시작했어요", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            if (!session.followUpUsed) {
                OutlinedButton(onClick = model::followUp, modifier = Modifier.fillMaxWidth()
                    .heightIn(min = 52.dp).testTag("followup")) {
                    Text("같은 강도로 한 번 더 안내", color = Cream)
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        if (session.phase == Phase.PRAISE_READY) {
            Text("대사를 읽고 칭찬을 선택해 주세요", color = Cream, fontSize = 16.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                (1..5).forEach { level ->
                    val selected = level == model.praiseLevel
                    Surface(
                        color = if (selected) Mint else Panel,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                            .testTag("praise_$level")
                            .semantics { role = Role.RadioButton; this.selected = selected }
                            .clickable { model.choosePraiseLevel(level) }
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("칭찬 $level${if (selected) " · 선택됨" else ""}",
                                color = if (selected) Ink else Mint,
                                fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(ScriptCatalog.followUp(level),
                                color = if (selected) Ink else Cream,
                                fontSize = 16.sp, lineHeight = 25.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = model::playPraise, modifier = Modifier.fillMaxWidth()
                .heightIn(min = 52.dp).testTag("play_praise")) {
                Text("칭찬 ${model.praiseLevel} · 아이에게 보여주기", color = Cream)
            }
            Spacer(Modifier.height(10.dp))
        }
        if (session.phase == Phase.SUCCESS_WAITING) {
            Text("칭찬을 잘 들었어요. 식사를 끝냈다면 눌러 주세요.",
                color = Cream, fontSize = 16.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Button(onClick = model::complete, modifier = Modifier.fillMaxWidth()
                .heightIn(min = 56.dp).testTag("complete"), shape = RoundedCornerShape(16.dp)) {
                Text("식사를 끝냈어요", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
        }
        OutlinedButton(onClick = model::stop, modifier = Modifier.fillMaxWidth()
            .heightIn(min = 56.dp).testTag("stop"), border = BorderStroke(1.dp, Color(0xFFB77E7A))) {
            Text("지금 종료", color = Color(0xFFFFC3B8), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text("언제든 끝낼 수 있어요", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ResultScreen(model: HeruViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HeruCharacter(false, Modifier.size(width = 144.dp, height = 192.dp))
        Spacer(Modifier.height(24.dp))
        Text("방금은 어땠나요?", color = Cream, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("부모가 확인한 결과만 기록해요.\n포인트가 자동으로 적립되지는 않아요.",
            color = Muted, fontSize = 14.sp, lineHeight = 24.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Outcome.entries.forEach { outcome ->
            OutlinedButton(onClick = { model.save(outcome) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                    .padding(bottom = 8.dp).testTag("outcome_${outcome.name}"),
                shape = RoundedCornerShape(16.dp)) {
                Text(outcome.label, color = if (outcome == Outcome.STARTED) Mint else Cream,
                    fontSize = 15.sp)
            }
        }
        model.message?.let { Message(it) }
        TextButton(onClick = model::home, modifier = Modifier.testTag("discard")) {
            Text("기록하지 않고 돌아가기", color = Muted)
        }
    }
}

@Composable
private fun SavedScreen(model: HeruViewModel) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Surface(color = Mint.copy(alpha = .15f), shape = CircleShape) {
            Text("✓", Modifier.padding(24.dp), color = Mint, fontSize = 36.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text("기록했어요", color = Cream, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("이 기기에만 저장돼요", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(32.dp))
        Button(onClick = model::home, modifier = Modifier.fillMaxWidth().height(56.dp)
            .testTag("home"), shape = RoundedCornerShape(16.dp)) { Text("처음으로") }
    }
}

@Composable
private fun HistoryScreen(model: HeruViewModel) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("기록을 모두 지울까요?") },
        text = { Text("이 기기에 저장된 사용 기록을 지워요. 되돌릴 수 없어요.") },
        confirmButton = { TextButton(onClick = {
            model.clearHistory(); confirmDelete = false
        }) { Text("모두 지우기") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
        Text("우리의 사용 기록", color = Cream, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("최근 200건 · 이 기기에만 저장", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(22.dp))
        if (model.records.isEmpty()) {
            Text("아직 기록이 없어요.\n해루를 사용한 뒤 결과를 남겨 보세요.",
                color = Muted, fontSize = 16.sp, lineHeight = 27.sp)
        }
        val format = remember { SimpleDateFormat("M월 d일 a h:mm", Locale.KOREAN) }
        model.records.forEach { row ->
            Surface(color = Panel, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text(format.format(Date(row.startedAt)), color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(row.outcome.label, color = if (row.outcome == Outcome.STARTED) Mint else Cream,
                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("식사 준비 · ${row.level}단계 · ${row.guardian.label}",
                        color = Muted, fontSize = 12.sp)
                }
            }
        }
        model.message?.let { Message(it) }
        if (model.records.isNotEmpty()) TextButton(onClick = { confirmDelete = true }) {
            Text("기록 모두 지우기", color = Color(0xFFFFC3B8))
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, Modifier.padding(vertical = 12.dp), color = Color(0xFFFFC3B8),
        fontSize = 13.sp, lineHeight = 21.sp)
}

@Composable
private fun HeruCharacter(speaking: Boolean, modifier: Modifier = Modifier) {
    var mouthTarget by remember { mutableFloatStateOf(0f) }
    val mouth by animateFloatAsState(mouthTarget, tween(65), label = "speakingPose")
    LaunchedEffect(speaking) {
        mouthTarget = 0f
        if (speaking) {
            val rhythm = kotlin.random.Random(31)
            delay(100)
            while (true) {
                // Short speech-like phrases, with pauses instead of a metronome.
                repeat(rhythm.nextInt(3, 6)) {
                    mouthTarget = 1f
                    delay(rhythm.nextLong(140, 240))
                    mouthTarget = 0f
                    delay(rhythm.nextLong(100, 180))
                }
                delay(rhythm.nextLong(330, 620))
            }
        }
    }
    val transition = rememberInfiniteTransition(label = "guardian")
    val breath by transition.animateFloat(
        initialValue = 1f, targetValue = if (speaking) 1.004f else 1f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
        label = "guardianBreathing"
    )
    val closed = ImageBitmap.imageResource(R.drawable.heru_guardian)
    val opened = ImageBitmap.imageResource(R.drawable.heru_guardian_speaking)
    Canvas(
        modifier = modifier.clip(RoundedCornerShape(20.dp)).semantics {
            contentDescription = "해루 — 전통 의상을 입은 전신 수호 도깨비"
        }.graphicsLayer {
            scaleX = breath
            scaleY = breath
        }
    ) {
        val fit = minOf(size.width / closed.width, size.height / closed.height)
        val width = closed.width * fit
        val height = closed.height * fit
        val left = (size.width - width) / 2
        val top = (size.height - height) / 2
        val destination = IntOffset(left.toInt(), top.toInt())
        val dimensions = IntSize(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1))
        drawImage(closed, dstOffset = destination, dstSize = dimensions, filterQuality = FilterQuality.High)
        val opening = if (speaking) mouth.coerceIn(0f, 1f) else 0f
        if (opening > 0.015f) {
            val center = Offset(left + width * 0.557f, top + height * 0.202f)
            drawIntoCanvas { canvas ->
                // Feather only the mouth and chin; the rest of the original never changes.
                canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { alpha = opening })
                // Keep the authored mouth shape intact: no stretching of lips/tusks/beard.
                drawImage(opened, dstOffset = destination, dstSize = dimensions,
                    filterQuality = FilterQuality.High)
                withTransform({ scale(1f, 0.60f, center) }) {
                    drawRect(
                        brush = Brush.radialGradient(
                            0f to Color.White, 0.60f to Color.White, 1f to Color.Transparent,
                            center = center, radius = width * 0.105f
                        ),
                        topLeft = Offset(-size.width, -size.height * 2),
                        size = androidx.compose.ui.geometry.Size(size.width * 3, size.height * 5),
                        blendMode = BlendMode.DstIn
                    )
                }
                canvas.restore()
            }
        }
    }
}
