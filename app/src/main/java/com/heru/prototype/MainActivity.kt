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
    override fun onStart() { super.onStart(); model.hostResumed() }
    override fun onStop() { if (!isChangingConfigurations) model.hostStopped(); super.onStop() }
    override fun onDestroy() { unregisterReceiver(noisy); super.onDestroy() }
}

@Composable
private fun HeruApp(model: HeruViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(model.message) {
        model.message?.let { snackbar.showSnackbar(it); model.dismissMessage() }
    }
    val session = model.session
    val dialing = session?.phase == Phase.DIALING
    val childCall = session?.phase in listOf(Phase.GREETING, Phase.PARENT_TURN,
        Phase.SPEAKING, Phase.PRAISE_SPEAKING, Phase.CLOSING)
    val parentStep = session?.phase in listOf(Phase.WAITING, Phase.PRAISE_READY,
        Phase.SUCCESS_WAITING, Phase.STAR_REWARD, Phase.RESULT)
    val showParentTopBar = (session == null || parentStep) && !model.loading && !model.loadFailed
    val canGoBack = if (session == null) model.tab != 0 || model.preparingCall else when (session.phase) {
        Phase.PRAISE_READY, Phase.WAITING, Phase.SUCCESS_WAITING, Phase.RESULT -> true
        Phase.STAR_REWARD -> !session.starAwarded
        else -> false
    }
    val navigateBack: () -> Unit = {
        when {
            session == null && model.preparingCall -> model.closeCallSetup()
            session == null -> model.selectTab(0)
            session.phase == Phase.DIALING -> model.cancelDialing()
            session.phase == Phase.PRAISE_READY -> model.backFromPraise()
            session.phase == Phase.STAR_REWARD -> model.backFromReward()
            else -> model.home()
        }
    }
    val activity = LocalActivity.current
    SideEffect {
        activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
                isAppearanceLightStatusBars = !dialing
                isAppearanceLightNavigationBars = !childCall && !dialing
            }
        }
    }
    BackHandler(enabled = session != null || model.tab != 0 || model.preparingCall) { navigateBack() }
    Box(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Background, Color(0xFFF8FBFF)))
    )) {
        Column(if (childCall || dialing) Modifier.fillMaxSize()
            else Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val parentArea = session == null
            if (showParentTopBar) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (canGoBack) BackArrowButton(navigateBack)
                    Text("heru", color = PrimaryBlue, fontSize = 28.sp, fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp)
                    Spacer(Modifier.width(10.dp))
                    Spacer(Modifier.weight(1f))
                    if (session == null) ChildPicker(model)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    model.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    model.loadFailed -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("기록을 열지 못했어요", color = MainText, fontSize = 20.sp)
                        Button(onClick = model::load) { Text("다시 시도") }
                    }
                    session == null && model.preparingCall -> ActivitySetupScreen(model)
                    session == null && model.tab == 2 -> ActivityHistoryScreen(model)
                    session == null && model.tab == 1 -> RewardSetupScreen(model)
                    session == null -> TodayScreen(model)
                    session.phase == Phase.DIALING -> DialingScreen(model)
                    session.phase == Phase.PRAISE_COUNTDOWN -> CountdownScreen(model)
                    childCall -> ChildModeScreen(model, session)
                    session.phase == Phase.STAR_REWARD -> StarRewardScreen(model)
                    session.phase == Phase.RESULT -> ResultScreen(model)
                    session.phase == Phase.SAVED -> SavedScreen(model)
                    else -> CallScreen(model, session)
                }
            }
            if (parentArea && !model.loading && !model.loadFailed && !model.preparingCall) {
                ParentBottomBar(selectedTab = model.tab, enabled = !model.busy, onSelect = model::selectTab)
            }
        }
        if (model.busy) LinearProgressIndicator(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding())
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
            .padding(bottom = if (session == null && !model.preparingCall) 80.dp else 16.dp))
        if (model.goalReached && session?.phase != Phase.STAR_REWARD) {
            AlertDialog(
                onDismissRequest = model::dismissGoalReached,
                title = { Text("별 목표 달성!") },
                text = { Text("축하해요! 별 ${model.rewardTarget}개를 모아 '${model.rewardTitle}' 목표에 도달했어요.") },
                confirmButton = {
                    TextButton(onClick = model::dismissGoalReached, enabled = !model.busy) { Text("확인") }
                }
            )
        }
    }
}

@Composable
private fun ParentBottomBar(selectedTab: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = Background, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.border(BorderStroke(1.dp, BorderBlue))) {
        listOf("오늘", "보상", "기록").forEachIndexed { index, label ->
            NavigationBarItem(
                enabled = enabled,
                selected = selectedTab == index,
                onClick = { onSelect(index) },
                icon = { ParentTabIcon(index) },
                label = { Text(label) },
                modifier = Modifier.testTag("tab_$index")
            )
        }
    }
}

@Composable
private fun ParentTabIcon(index: Int) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(23.dp)) {
        val line = 1.8.dp.toPx()
        when (index) {
            0 -> {
                val house = Path().apply {
                    moveTo(size.width * .1f, size.height * .45f); lineTo(size.width * .5f, size.height * .1f)
                    lineTo(size.width * .9f, size.height * .45f); moveTo(size.width * .2f, size.height * .4f)
                    lineTo(size.width * .2f, size.height * .9f); lineTo(size.width * .8f, size.height * .9f)
                    lineTo(size.width * .8f, size.height * .4f)
                }
                drawPath(house, color, style = Stroke(line, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            1 -> {
                val star = Path().apply {
                    repeat(10) { point ->
                        val angle = -Math.PI / 2 + point * Math.PI / 5
                        val radius = size.width * if (point % 2 == 0) .46f else .21f
                        val x = size.width / 2 + kotlin.math.cos(angle).toFloat() * radius
                        val y = size.height / 2 + kotlin.math.sin(angle).toFloat() * radius
                        if (point == 0) moveTo(x, y) else lineTo(x, y)
                    }; close()
                }
                drawPath(star, color, style = Stroke(line, join = StrokeJoin.Round))
            }
            else -> {
                drawRoundRect(color, topLeft = Offset(size.width * .12f, size.height * .18f),
                    size = androidx.compose.ui.geometry.Size(size.width * .76f, size.height * .72f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = Stroke(line))
                drawLine(color, Offset(size.width * .12f, size.height * .4f), Offset(size.width * .88f, size.height * .4f), line)
                drawLine(color, Offset(size.width * .32f, 0f), Offset(size.width * .32f, size.height * .3f), line, StrokeCap.Round)
                drawLine(color, Offset(size.width * .68f, 0f), Offset(size.width * .68f, size.height * .3f), line, StrokeCap.Round)
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
                    HeruDialAvatar()
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
            Button(onClick = model::connect, enabled = !model.busy, modifier = Modifier.fillMaxWidth()
                .height(58.dp).testTag("connect_call"), shape = RoundedCornerShape(18.dp)) {
                Text("연결하기", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = model::cancelDialing, enabled = !model.busy,
                modifier = Modifier.testTag("cancel_call")) {
                Text("전화 취소", color = Color.White.copy(alpha = .8f))
            }
        }
    }
}

@Composable
private fun HeruDialAvatar() {
    val portrait = ImageBitmap.imageResource(R.drawable.heru_guardian)
    Canvas(Modifier.fillMaxSize()) {
        // The call portrait uses the face and beard, not the full-body canvas.
        val cropSide = (portrait.width * .57f).toInt()
        val left = (portrait.width - cropSide) / 2 + (portrait.width * .025f).toInt()
        drawImage(
            image = portrait,
            srcOffset = IntOffset(left, 0),
            srcSize = IntSize(cropSide, cropSide),
            dstOffset = IntOffset(0, (size.height * .08f).toInt()),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.High
        )
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
        OutlinedButton(onClick = { model.pauseCurrent() }, modifier = Modifier.testTag("cancel_countdown")) {
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
                Text("“네, 우리 아이가 ${model.routine.parentExample}.”",
                    color = Color.White, fontSize = 18.sp, lineHeight = 26.sp,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = model::parentSpoke, enabled = !model.busy, modifier = Modifier.fillMaxWidth()
                    .height(54.dp).testTag("parent_spoke"),
                    shape = RoundedCornerShape(15.dp)) {
                    Text("말했어요", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { model.pauseCurrent() }) {
                    Text("통화 종료", color = Color.White.copy(alpha = .7f))
                }
            }
        } else {
            Row(Modifier.align(Alignment.BottomCenter)
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
                modifier = Modifier.testTag("end_call"), onClick = { model.pauseCurrent() })
            }
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
private fun CallScreen(model: HeruViewModel, session: CallSession) {
    val choosingPraise = session.phase == Phase.PRAISE_READY
    var repeatLevel by remember(session.id) { mutableIntStateOf(session.followUpLevel ?: session.level) }
    val warningLevels = session.followUpLevel?.let { "${session.level} → ${it}단계" }
        ?: "${session.level}단계"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp).padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(18.dp))
        Text(if (choosingPraise) "${session.routine.label} 시작 · 칭찬 선택" else "${session.routine.label} 준비 · $warningLevels",
            color = PrimaryBlue, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Text(when (session.phase) {
            Phase.CLOSING -> "${session.routine.objectPhrase} 마무리하고 있어요"
            Phase.PRAISE_READY -> "어떤 칭찬을 들려줄까요?"
            Phase.SUCCESS_WAITING -> "잘했어요. 활동을 계속해 주세요"
            Phase.WAITING -> "행동을 시작할 시간을 주세요"
            else -> "해루가 말하고 있어요"
        }, color = MainText, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center)
        if (!choosingPraise && session.phase != Phase.WAITING) {
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
                Text("잘했어요 · 칭찬 들려주기", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = model::rewardCurrent, enabled = !model.busy) { Text("약속을 확인했어요 · 바로 별 주기") }
            Spacer(Modifier.height(10.dp))
            run {
                Text("아직 오지 않았다면 강도를 다시 골라 주세요", color = MainText,
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { level ->
                        val selected = repeatLevel == level
                        Surface(
                            color = if (selected) PrimaryBlue else CardBlue,
                            shape = RoundedCornerShape(14.dp),
                            border = if (selected) null else BorderStroke(1.dp, BorderBlue),
                            modifier = Modifier.weight(1f).heightIn(min = 76.dp)
                                .testTag("repeat_level_$level")
                                .semantics { role = Role.RadioButton; this.selected = selected }
                                .clickable { repeatLevel = level }
                        ) {
                            Column(Modifier.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$level", fontSize = 21.sp, fontWeight = FontWeight.Bold,
                                    color = if (selected) Background else MainText)
                                Spacer(Modifier.height(4.dp))
                                Text(ScriptCatalog.labels[level - 1], fontSize = 10.sp,
                                    color = if (selected) Background else SubtleText,
                                    textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Surface(color = CardBlue, shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderBlue), modifier = Modifier.fillMaxWidth()) {
                    Text(ScriptCatalog.main(repeatLevel, session.guardian, session.routine),
                        modifier = Modifier.padding(16.dp).testTag("repeat_script"),
                        color = MainText, fontSize = 15.sp, lineHeight = 24.sp)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { model.followUp(repeatLevel) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("followup"),
                    shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderBlue)) {
                    Text("${repeatLevel}단계로 다시 안내", color = DeepBlue)
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
                            Text(ScriptCatalog.followUp(level, session.routine),
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
            Text("약속을 해냈다면 별을 선물해 주세요. 활동이 더 남았다면 나중에 이어갈 수 있어요.",
                color = MainText, fontSize = 16.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Button(onClick = model::complete, modifier = Modifier.fillMaxWidth()
                .heightIn(min = 56.dp).testTag("complete"), shape = RoundedCornerShape(16.dp)) {
                Text("오늘 약속을 해냈어요", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
        }
        OutlinedButton(onClick = { model.pauseCurrent() }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()
            .heightIn(min = 54.dp).testTag("pause_activity"), shape = RoundedCornerShape(16.dp)) {
            Text("나중에 이어하기")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = model::stop, enabled = !model.busy, modifier = Modifier.fillMaxWidth()
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
        Text("이번 활동은 어땠나요?\n부모가 확인한 결과만 기록해요.",
            color = SubtleText, fontSize = 14.sp, lineHeight = 24.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Button(onClick = model::rewardCurrent, enabled = !model.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("약속을 해냈어요 · 별 주기") }
        Spacer(Modifier.height(10.dp))
        listOf(Outcome.UNCHANGED, Outcome.STOPPED).forEach { outcome ->
            OutlinedButton(onClick = { model.save(outcome) }, enabled = !model.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                    .padding(bottom = 8.dp).testTag("outcome_${outcome.name}"),
                shape = RoundedCornerShape(16.dp)) {
                Text(outcome.label, color = if (outcome == Outcome.STARTED) DeepBlue else MainText,
                    fontSize = 15.sp)
            }
        }
        TextButton(onClick = model::home, modifier = Modifier.testTag("discard")) {
            Text("아직 진행 중이에요 · 나중에 이어하기", color = SubtleText)
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
        Text(if (model.session?.starAwarded == true) "별 하나와 성취를 기록했어요" else "기록했어요", color = MainText,
            fontSize = 25.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("이 기기에만 저장돼요", color = SubtleText, fontSize = 14.sp)
        Spacer(Modifier.height(32.dp))
        Button(onClick = model::home, modifier = Modifier.fillMaxWidth().height(56.dp)
            .testTag("home"), shape = RoundedCornerShape(16.dp)) { Text("오늘로 돌아가기") }
    }
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
