package com.heru.prototype

import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Observer

@Composable
internal fun SelfViewPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var unavailable by remember { mutableStateOf(false) }
    var streaming by remember { mutableStateOf(false) }

    DisposableEffect(context, lifecycleOwner, previewView) {
        val streamObserver = Observer<PreviewView.StreamState> { state ->
            streaming = state == PreviewView.StreamState.STREAMING
        }
        previewView.previewStreamState.observe(lifecycleOwner, streamObserver)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var active = true
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        providerFuture.addListener({
            if (active) {
                try {
                    val readyProvider = providerFuture.get()
                    if (!readyProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        unavailable = true
                    } else {
                        val readyPreview = Preview.Builder().build().apply {
                            surfaceProvider = previewView.surfaceProvider
                        }
                        readyProvider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, readyPreview
                        )
                        provider = readyProvider
                        preview = readyPreview
                    }
                } catch (_: Exception) {
                    unavailable = true
                }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            active = false
            previewView.previewStreamState.removeObserver(streamObserver)
            preview?.let { bound -> runCatching { provider?.unbind(bound) } }
        }
    }

    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, Background), shadowElevation = 6.dp,
        color = CardBlue) {
        Box {
            if (unavailable) {
                Text("내 모습 표시 불가", Modifier.align(Alignment.Center).padding(8.dp),
                    color = SubtleText, fontSize = 12.sp)
            } else {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                if (!streaming) {
                    Text("카메라 연결 중", Modifier.align(Alignment.Center).padding(8.dp),
                        color = SubtleText, fontSize = 12.sp)
                }
            }
        }
    }
}
