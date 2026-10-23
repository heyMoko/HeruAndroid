package com.heru.prototype

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.WindowCompat
import com.heru.prototype.domain.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var model: HeruViewModel
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { model.hostStopped() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.rgb(32, 52, 80)
            )
        )
        model = ViewModelProvider(this)[HeruViewModel::class.java]
        val filter = IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(noisy, filter)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = PrimaryBlue, onPrimary = Background,
                    secondary = DeepBlue, onSecondary = Background,
                    background = Background, onBackground = MainText,
                    surface = Background, onSurface = MainText,
                    surfaceVariant = CardBlue, onSurfaceVariant = SubtleText,
                    outline = BorderBlue, error = ErrorRed
                )
            ) { HeruApp(model) }
        }
    }
    override fun onStop() { model.hostStopped(); super.onStop() }
    override fun onDestroy() { unregisterReceiver(noisy); super.onDestroy() }
}

@Composable
private fun HeruApp(model: HeruViewModel) {
    val session = model.session
    val dialing = session?.phase == Phase.DIALING
    val childCall = session?.phase in listOf(Phase.GREETING, Phase.PARENT_TURN,
        Phase.SPEAKING, Phase.PRAISE_SPEAKING, Phase.CLOSING)
    val activity = LocalActivity.current
    SideEffect {
        activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
                isAppearanceLightStatusBars = !dialing
                isAppearanceLightNavigationBars = !childCall && !dialing
            }
        }
    }
    BackHandler(enabled = session != null || model.history) {
        if (dialing) model.cancelDialing()
        else if (session != null && session.phase !in listOf(Phase.RESULT, Phase.SAVED)) model.stop()
        else model.home()
    }
    Box(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Background, Color(0xFFF8FBFF)))
    )) {
        Column(if (childCall || dialing) Modifier.fillMaxSize()
            else Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (session?.phase != Phase.STAR_REWARD && !childCall && !dialing) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("heru", color = PrimaryBlue, fontSize = 28.sp, fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp)
                Spacer(Modifier.width(10.dp))
                Surface(color = SoftBlue, shape = RoundedCornerShape(6.dp)) {
                    Text("시제품", Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        color = DeepBlue, fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                if (session == null && !model.history) {
                    TextButton(onClick = model::showHistory, modifier = Modifier.testTag("history")) {
                        Text("기록 ${model.records.size}", color = SubtleText)
                    }
                } else if (model.history) {
                    TextButton(onClick = model::home) { Text("닫기", color = DeepBlue) }
                }
            }
            }
            when {
                model.history -> HistoryScreen(model)
                session == null -> SetupScreen(model)
                session.phase == Phase.DIALING -> DialingScreen(model)
                session.phase == Phase.PRAISE_COUNTDOWN -> CountdownScreen(model)
                childCall -> ChildModeScreen(model, session)
                session.phase == Phase.STAR_REWARD -> StarRewardScreen(model)
                session.phase == Phase.RESULT -> ResultScreen(model)
                session.phase == Phase.SAVED -> SavedScreen(model)
                else -> CallScreen(model, session)
            }
        }
    }
}

@Composable
private fun DialingScreen(model: HeruViewModel) {
    val pulse = rememberInfiniteTransition(label = "dialing")
    val ringSize by pulse.animateFloat(1f, 1.11f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dialingRing")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
        listOf(Color(0xFF183D66), Color(0xFF071828))))) {
        Column(Modifier.align(Alignment.Center).padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(236.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(218.dp).graphicsLayer {
                    scaleX = ringSize; scaleY = ringSize
                }.border(1.dp, Color.White.copy(alpha = .26f), CircleShape))
                Surface(Modifier.size(186.dp), shape = CircleShape,
                    color = Color(0xFFB8D6E8), border = BorderStroke(2.dp, Color.White.copy(alpha = .8f))) {
                    HeruCharacter(false, Modifier.fillMaxSize(), fillFrame = true)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("해루", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("전화 거는 중…", color = Color.White.copy(alpha = .82f), fontSize = 17.sp)
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 26.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = model::connect, modifier = Modifier.fillMaxWidth()
                .height(58.dp).testTag("connect_call"), shape = RoundedCornerShape(18.dp)) {
                Text("연결하기", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = model::cancelDialing,
                modifier = Modifier.testTag("cancel_call")) {
                Text("전화 취소", color = Color.White.copy(alpha = .8f))
            }
        }
    }
}

@Composable
private fun CountdownScreen(model: HeruViewModel) {
    Column(Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("해루와 연결 중", color = MainText, fontSize = 27.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Text("아이에게 화면을 보여주세요", color = SubtleText, fontSize = 16.sp)
        Spacer(Modifier.height(28.dp))
        Text("${model.countdownSeconds}", color = PrimaryBlue, fontSize = 96.sp,
            fontWeight = FontWeight.Black)
        Spacer(Modifier.height(28.dp))
        OutlinedButton(onClick = model::stop, modifier = Modifier.testTag("cancel_countdown")) {
            Text("취소")
        }
    }
}

@Composable
private fun ChildModeScreen(model: HeruViewModel, session: CallSession) {
    val speaking = session.phase in listOf(Phase.GREETING, Phase.SPEAKING,
        Phase.PRAISE_SPEAKING, Phase.CLOSING)
    val parentTurn = session.phase == Phase.PARENT_TURN
    val context = LocalContext.current
    val hasFrontCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
    val showSelfView = model.selfViewEnabled && hasFrontCamera &&
        context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.chooseSelfView(granted)
    }
    Box(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Color(0xFFB7D5EB), Color(0xFFEAF4FA), Color(0xFF799DB7)))
    )) {
        HeruCharacter(speaking, Modifier.fillMaxSize(), fillFrame = true)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Transparent,
            .68f to Color.Transparent,
            1f to Color(0xC5081D31)
        )))
        if (showSelfView) {
            SelfViewPreview(Modifier.align(Alignment.BottomStart)
                .padding(start = 18.dp, bottom = if (parentTurn) 280.dp else 136.dp)
                .size(width = 136.dp, height = 188.dp)
                .testTag("self_view"))
        } else {
            Surface(Modifier.align(Alignment.BottomStart)
                .padding(start = 18.dp, bottom = if (parentTurn) 280.dp else 136.dp)
                .size(width = 136.dp, height = 188.dp),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(2.dp, Color.White.copy(alpha = .8f)),
                color = Color(0xCC12283E)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("카메라 꺼짐", color = Color.White, fontSize = 12.sp)
                }
            }
        }
        if (parentTurn) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .background(Color(0xE5122B42), RoundedCornerShape(22.dp))
                .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("부모가 말할 차례", color = Color.White.copy(alpha = .75f),
                    fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Text("“네, 우리 ○○이가 밥을 안 먹어요.”",
                    color = Color.White, fontSize = 18.sp, lineHeight = 26.sp,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = model::parentSpoke, modifier = Modifier.fillMaxWidth()
                    .height(54.dp).testTag("parent_spoke"),
                    shape = RoundedCornerShape(15.dp)) {
                    Text("말했어요", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = model::stop) {
                    Text("통화 종료", color = Color.White.copy(alpha = .7f))
                }
            }
        } else Row(Modifier.align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalAlignment = Alignment.CenterVertically) {
            VideoCallAction(camera = true, active = showSelfView, enabled = hasFrontCamera,
                label = if (showSelfView) "카메라 끄기" else "카메라 켜기",
                modifier = Modifier.testTag("toggle_camera")) {
                if (showSelfView) model.chooseSelfView(false)
                else if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    model.chooseSelfView(true)
                } else cameraPermission.launch(Manifest.permission.CAMERA)
            }
            VideoCallAction(camera = false, active = true, label = "통화 종료",
                modifier = Modifier.testTag("end_call"), onClick = model::stop)
        }
    }
}

@Composable
private fun VideoCallAction(camera: Boolean, active: Boolean, label: String,
    modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(modifier.size(if (camera) 56.dp else 68.dp)
        .clip(CircleShape)
        .background(if (camera) Color(0xCC152C42) else Color(0xFFE53951))
        .clickable(enabled = enabled, onClick = onClick)
        .semantics { contentDescription = label },
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(27.dp)) {
            if (camera) {
                val line = 2.dp.toPx()
                drawRoundRect(Color.White, topLeft = Offset(size.width * .12f, size.height * .24f),
                    size = androidx.compose.ui.geometry.Size(size.width * .55f, size.height * .52f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                    style = Stroke(width = line))
                val lens = Path().apply {
                    moveTo(size.width * .68f, size.height * .37f)
                    lineTo(size.width * .89f, size.height * .25f)
                    lineTo(size.width * .89f, size.height * .75f)
                    lineTo(size.width * .68f, size.height * .63f)
                    close()
                }
                drawPath(lens, Color.White)
                if (!active) drawLine(Color.White, Offset(size.width * .08f, size.height * .90f),
                    Offset(size.width * .94f, size.height * .08f),
                    strokeWidth = line + 1.dp.toPx(), cap = StrokeCap.Round)
            } else {
                drawArc(Color.White, startAngle = 205f, sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(size.width * .10f, size.height * .31f),
                    size = androidx.compose.ui.geometry.Size(size.width * .80f, size.height * .75f),
                    style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}
@Composable
private fun SetupScreen(model: HeruViewModel) {
    val context = LocalContext.current
    var cameraNotice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(model.selfViewEnabled) {
        if (model.selfViewEnabled &&
            context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            model.chooseSelfView(false)
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.chooseSelfView(granted)
        cameraNotice = if (granted) null else "카메라 없이도 해루를 볼 수 있어요."
    }
    Column(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp).padding(bottom = 12.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("필요한 순간, 해루를 불러요", color = SubtleText, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Text("오늘의 약속을\n들려줄 시간", color = MainText, fontSize = 32.sp,
            fontWeight = FontWeight.Bold, lineHeight = 42.sp)
        Surface(color = SoftBlue, shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, BorderBlue),
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
            Row(Modifier.padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Surface(color = Background, shape = RoundedCornerShape(8.dp)) {
                        Text("01  식사 준비", Modifier.padding(10.dp), color = DeepBlue, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("장난감 내려놓고\n식탁에 앉기", color = MainText, fontSize = 19.sp,
                        lineHeight = 29.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("한 번에 행동 하나만", color = SubtleText, fontSize = 12.sp)
                }
                HeruCharacter(model.previewing, Modifier.size(width = 132.dp, height = 188.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("표현 강도", color = MainText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("부모가 직접 선택해요", color = SubtleText, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { level ->
                val selected = level == model.level
                Surface(
                    color = if (selected) PrimaryBlue else CardBlue,
                    shape = RoundedCornerShape(14.dp),
                    border = if (selected) null else BorderStroke(1.dp, BorderBlue),
                    modifier = Modifier.weight(1f).heightIn(min = 80.dp)
                        .testTag("level_$level")
                        .semantics { role = Role.RadioButton; this.selected = selected }
                        .clickable { model.chooseLevel(level) }
                ) {
                    Column(Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$level", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            color = if (selected) Background else MainText)
                        Spacer(Modifier.height(4.dp))
                        Text(ScriptCatalog.labels[level - 1], fontSize = 10.sp,
                            color = if (selected) Background else SubtleText, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Surface(color = CardBlue, shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, BorderBlue),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text("${model.level}단계 · ${ScriptCatalog.labels[model.level - 1]}",
                    color = PrimaryBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(ScriptCatalog.main(model.level, model.guardian), color = MainText,
                    fontSize = 16.sp, lineHeight = 27.sp, modifier = Modifier.testTag("script"))
                Spacer(Modifier.height(10.dp))
                Text("해루의 녹음 목소리로 들려줘요", color = SubtleText, fontSize = 11.sp)
            }
        }
    }
    Column(Modifier.fillMaxWidth().background(Background)
        .padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 16.dp)) {
        model.message?.let { Message(it) }
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("아이 모습도 함께 보기", color = MainText,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("전면카메라 미리보기 · 저장하거나 전송하지 않아요",
                    color = SubtleText, fontSize = 11.sp)
            }
            Switch(checked = model.selfViewEnabled, modifier = Modifier.testTag("self_view_toggle"),
                onCheckedChange = { enabled ->
                    cameraNotice = null
                    if (!enabled) model.chooseSelfView(false)
                    else if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
                        cameraNotice = "이 기기에는 전면카메라가 없어요."
                    } else if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        model.chooseSelfView(true)
                    } else cameraPermission.launch(Manifest.permission.CAMERA)
                })
        }
        cameraNotice?.let { Text(it, color = SubtleText, fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp)) }
        OutlinedButton(onClick = model::preview, modifier = Modifier.fillMaxWidth()
            .heightIn(min = 52.dp).testTag("preview"), shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, BorderBlue)) {
            Text(if (model.previewing) "미리 듣기 멈추기" else "대사 미리 듣기", color = DeepBlue)
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = model::start, modifier = Modifier.fillMaxWidth()
            .heightIn(min = 56.dp).testTag("start"), shape = RoundedCornerShape(16.dp)) {
            Text("해루 부르기", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text("이야기 속 도깨비의 상황극이에요. 먼저 부모가 들어보고 보여주세요.",
            color = SubtleText, fontSize = 11.sp, lineHeight = 18.sp)
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
        Text(if (choosingPraise) "식사 시작 · 칭찬 선택" else "식사 준비 · ${session.level}단계", color = PrimaryBlue, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Text(when (session.phase) {
            Phase.CLOSING -> "식사를 마무리하고 있어요"
            Phase.PRAISE_READY -> "어떤 칭찬을 들려줄까요?"
            Phase.SUCCESS_WAITING -> "잘했어요. 식사를 계속해 주세요"
            Phase.WAITING -> "행동을 시작할 시간을 주세요"
            else -> "해루가 말하고 있어요"
        }, color = MainText, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center)
        if (!choosingPraise) {
            Spacer(Modifier.height(28.dp))
            HeruCharacter(false, Modifier.size(width = 180.dp, height = 240.dp))
            Spacer(Modifier.height(24.dp))
            Surface(color = CardBlue, shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, BorderBlue), modifier = Modifier.fillMaxWidth()) {
                Text(model.sessionScript, Modifier.padding(22.dp), color = MainText,
                    fontSize = 20.sp, lineHeight = 33.sp, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("부모가 옆에서 함께 조작해 주세요", color = SubtleText, fontSize = 12.sp)
        Spacer(Modifier.height(24.dp))
        if (session.phase == Phase.WAITING) {
            Button(onClick = model::finish, modifier = Modifier.fillMaxWidth()
                .heightIn(min = 56.dp).testTag("finish"), shape = RoundedCornerShape(16.dp)) {
                Text("행동을 시작했어요", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            if (!session.followUpUsed) {
                OutlinedButton(onClick = model::followUp, modifier = Modifier.fillMaxWidth()
                    .heightIn(min = 52.dp).testTag("followup"), shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderBlue)) {
                    Text("같은 강도로 한 번 더 안내", color = DeepBlue)
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        if (session.phase == Phase.PRAISE_READY) {
            Text("대사를 읽고 칭찬을 선택해 주세요", color = MainText, fontSize = 16.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                (1..5).forEach { level ->
                    val selected = level == model.praiseLevel
                    Surface(
                        color = if (selected) DeepBlue else CardBlue,
                        shape = RoundedCornerShape(12.dp),
                        border = if (selected) null else BorderStroke(1.dp, BorderBlue),
                        modifier = Modifier.fillMaxWidth()
                            .testTag("praise_$level")
                            .semantics { role = Role.RadioButton; this.selected = selected }
                            .clickable { model.choosePraiseLevel(level) }
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("칭찬 $level${if (selected) " · 선택됨" else ""}",
                                color = if (selected) Background else DeepBlue,
                                fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(ScriptCatalog.followUp(level),
                                color = if (selected) Background else MainText,
                                fontSize = 16.sp, lineHeight = 25.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = model::playPraise, modifier = Modifier.fillMaxWidth()
                .heightIn(min = 52.dp).testTag("play_praise"),
                shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderBlue)) {
                Text("칭찬 ${model.praiseLevel} · 아이에게 보여주기", color = DeepBlue)
            }
            Spacer(Modifier.height(10.dp))
        }
        if (session.phase == Phase.SUCCESS_WAITING) {
            Text("칭찬을 잘 들었어요. 식사를 끝냈다면 눌러 주세요.",
                color = MainText, fontSize = 16.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Button(onClick = model::complete, modifier = Modifier.fillMaxWidth()
                .heightIn(min = 56.dp).testTag("complete"), shape = RoundedCornerShape(16.dp)) {
                Text("식사를 끝냈어요", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
        }
        OutlinedButton(onClick = model::stop, modifier = Modifier.fillMaxWidth()
            .heightIn(min = 56.dp).testTag("stop"), shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, ErrorRed.copy(alpha = .38f))) {
            Text("지금 종료", color = ErrorRed, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text("언제든 끝낼 수 있어요", color = SubtleText, fontSize = 12.sp)
    }
}

@Composable
private fun ResultScreen(model: HeruViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HeruCharacter(false, Modifier.size(width = 144.dp, height = 192.dp))
        Spacer(Modifier.height(24.dp))
        Text("방금은 어땠나요?", color = MainText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("이번 식사는 어땠나요?\n부모가 확인한 결과만 기록해요.",
            color = SubtleText, fontSize = 14.sp, lineHeight = 24.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Outcome.entries.forEach { outcome ->
            OutlinedButton(onClick = { model.save(outcome) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                    .padding(bottom = 8.dp).testTag("outcome_${outcome.name}"),
                shape = RoundedCornerShape(16.dp)) {
                Text(outcome.label, color = if (outcome == Outcome.STARTED) DeepBlue else MainText,
                    fontSize = 15.sp)
            }
        }
        model.message?.let { Message(it) }
        TextButton(onClick = model::home, modifier = Modifier.testTag("discard")) {
            Text("기록하지 않고 돌아가기", color = SubtleText)
        }
    }
}

@Composable
private fun SavedScreen(model: HeruViewModel) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Surface(color = SoftBlue, shape = CircleShape) {
            Text("✓", Modifier.padding(24.dp), color = DeepBlue, fontSize = 36.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text("기록했어요", color = MainText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("이 기기에만 저장돼요", color = SubtleText, fontSize = 14.sp)
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
        Text("우리의 사용 기록", color = MainText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("최근 200건 · 이 기기에만 저장", color = SubtleText, fontSize = 12.sp)
        Spacer(Modifier.height(22.dp))
        if (model.records.isEmpty()) {
            Text("아직 기록이 없어요.\n해루를 사용한 뒤 결과를 남겨 보세요.",
                color = SubtleText, fontSize = 16.sp, lineHeight = 27.sp)
        }
        val format = remember { SimpleDateFormat("M월 d일 a h:mm", Locale.KOREAN) }
        model.records.forEach { row ->
            Surface(color = CardBlue, shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderBlue),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text(format.format(Date(row.startedAt)), color = SubtleText, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(row.outcome.label, color = if (row.outcome == Outcome.STARTED) DeepBlue else MainText,
                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("식사 준비 · ${row.level}단계 · ${row.guardian.label}",
                        color = SubtleText, fontSize = 12.sp)
                }
            }
        }
        model.message?.let { Message(it) }
        if (model.records.isNotEmpty()) TextButton(onClick = { confirmDelete = true }) {
            Text("기록 모두 지우기", color = ErrorRed)
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, Modifier.padding(vertical = 12.dp), color = ErrorRed,
        fontSize = 13.sp, lineHeight = 21.sp)
}

@Composable
private fun HeruCharacter(speaking: Boolean, modifier: Modifier = Modifier, fillFrame: Boolean = false) {
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
        modifier = modifier.then(if (fillFrame) Modifier else Modifier.clip(RoundedCornerShape(20.dp))).semantics {
            contentDescription = "해루 — 전통 의상을 입은 전신 수호 도깨비"
        }.graphicsLayer {
            scaleX = breath
            scaleY = breath
        }
    ) {
        val fit = if (fillFrame) maxOf(size.width / closed.width, size.height / closed.height) * .84f
            else minOf(size.width / closed.width, size.height / closed.height)
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
