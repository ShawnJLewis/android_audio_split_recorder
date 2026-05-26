package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audio.AppViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen()
            }
        }
    }
}

/**
 * High-fidelity, Glass-morphic Audio Control Panel.
 * Features an animated fluid sphere backdrop, Material You themed tabs,
 * and standard dual-track mixer faders.
 */
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val viewModel: AppViewModel = viewModel()
    
    val state by viewModel.state.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val sessions by viewModel.sessions.collectAsState()

    // Request permissions launcher
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasMicPermission = granted
            if (!granted) {
                Toast.makeText(context, "Microphone permission is required to record audio", Toast.LENGTH_LONG).show()
            }
        }
    )

    // Ensure edge-to-edge layout matches notch safe-drawing guidelines
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                        )
                    )
                )
        ) {
            // Liquid Backdrop layer: adds subtle animated background glowing blobs for ambient fluid style
            LiquidGlowBackdrop()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Primary App Header
                HeaderBlock()

                Spacer(modifier = Modifier.height(12.dp))

                // Themed Liquid Sphere State visualizer card
                LiquidSphereVisualizerCard(viewModel)

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Glassmorphic Navigation Tabs (Material You styled)
                GlassNavigationBar(
                    activeTab = activeTab,
                    onTabSelected = { viewModel.setActiveTab(it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable main content matching active tab index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (activeTab) {
                        0 -> RecordTabContent(
                            viewModel = viewModel,
                            hasPermission = hasMicPermission,
                            onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        )
                        1 -> MixerTabContent(viewModel = viewModel)
                        2 -> AnalystTabContent(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

/**
 * Dynamic background glowing floating fluid blobs mimicking a morphing lava lamp.
 */
@Composable
fun LiquidGlowBackdrop() {
    val infiniteTransition = rememberInfiniteTransition(label = "backdrop")
    
    val pulseX1 by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "x1"
    )
    val pulseY1 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "y1"
    )
    val pulseX2 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "x2"
    )

    val primaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    val secondaryColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.06f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryColor, Color.Transparent),
                center = Offset(size.width * pulseX1, size.height * pulseY1),
                radius = size.width * 0.7f
            ),
            radius = size.width * 0.7f,
            center = Offset(size.width * pulseX1, size.height * pulseY1)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondaryColor, Color.Transparent),
                center = Offset(size.width * pulseX2, size.height * (1f - pulseY1)),
                radius = size.width * 0.6f
            ),
            radius = size.width * 0.6f,
            center = Offset(size.width * pulseX2, size.height * (1f - pulseY1))
        )
    }
}

@Composable
fun HeaderBlock() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Liquid Audio",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Text(
                text = "Eco-Cancelled Vocal & Beat Splitter",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.size(36.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Status",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Elegant Header Display showing the Morphing Fluid Glass Ball and recording properties.
 */
@Composable
fun LiquidSphereVisualizerCard(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val liveMicLevel by viewModel.liveMicLevel.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playProgress by viewModel.playbackProgress.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "liquid_glass")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rot"
    )

    val breathingFactor by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    // Compute dynamic bubble scale: expands based on live audio metrics
    val targetScale = when {
        isRecording -> 1.0f + liveMicLevel * 1.1f
        isPlaying -> 1.0f + (sin(playProgress * PI * 18).toFloat() * 0.08f)
        else -> breathingFactor
    }
    
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    val activeGlassColor = when {
        isRecording -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        isPlaying -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        state == AppViewModel.AppState.PROCESSING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    }

    val glowColor = when {
        isRecording -> Color(0xFFFF5252)
        isPlaying -> Color(0xFF64FFDA)
        state == AppViewModel.AppState.PROCESSING -> Color(0xFFFFD740)
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(activeGlassColor, activeGlassColor.copy(alpha = 0.08f))
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f))
                ),
                RoundedCornerShape(24.dp)
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Draw the Live Liquid Glass Sphere
        Canvas(
            modifier = Modifier
                .size(105.dp)
                .testTag("liquid_sphere")
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = (size.width / 2) * animatedScale

            // Draw underlying subtle blur glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor.copy(alpha = 0.25f), Color.Transparent),
                    center = center,
                    radius = radius * 1.5f
                ),
                radius = radius * 1.4f,
                center = center
            )

            // Draw frosted liquid glass shape
            val path = Path()
            val points = 8
            val angleStep = (2f * PI.toFloat()) / points
            
            for (i in 0 until points) {
                val currentAngle = i * angleStep + (rotation * PI.toFloat() / 180f)
                // Mutate radius at each node dynamically if recording to look like a fluid bubble
                val modRadius = if (isRecording) {
                    radius * (1f + sin(currentAngle * 3f + liveMicLevel * 10f) * 0.15f)
                } else if (state == AppViewModel.AppState.PROCESSING) {
                    radius * (1f + cos(currentAngle * 4f + (rotation * PI.toFloat() / 60f)) * 0.08f)
                } else {
                    radius
                }

                val x = center.x + modRadius * cos(currentAngle)
                val y = center.y + modRadius * sin(currentAngle)

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            path.close()

            // Fill glass body with high-contrast semi-transparent gradient
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.38f),
                        glowColor.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.03f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
            )

            // Dynamic glassy border line with light reflections
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.65f),
                style = Stroke(width = 2.dp.toPx())
            )

            // Draw a subtle inner 3D highlights
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.5f), Color.Transparent),
                    center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f),
                    radius = radius * 0.4f
                ),
                radius = radius * 0.35f,
                center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f)
            )
        }

        // Overlaying core telemetry stats
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = 0.6f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val pulseSec by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "sec_dot"
                        )
                        
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isRecording) Color.Red.copy(alpha = pulseSec)
                                    else if (isPlaying) Color.Green.copy(alpha = pulseSec)
                                    else Color.Gray
                                )
                        )
                        Text(
                            text = if (isRecording) "RECORDING"
                                   else if (isPlaying) "MIXER PLAYING"
                                   else if (state == AppViewModel.AppState.PROCESSING) "SPLITTING CHANNELS"
                                   else "AUDIO IDLE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        )
                    }
                }

                Text(
                    text = if (isRecording) "AEC Active" else "Echo Shield",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isRecording) Color(0xFF81C784) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "SESSION ACTIVE",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    )
                    Text(
                        text = "44.1kHz / 16b Stereo",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                    )
                }

                Text(
                    text = if (isRecording) "LVL ${String.format("%.0f%%", liveMicLevel * 100f)}"
                           else if (isPlaying) "POS ${String.format("%.0f%%", playProgress * 100f)}"
                           else if (state == AppViewModel.AppState.PROCESSING) "DSP BUSY"
                           else "MUTE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = glowColor
                    )
                )
            }
        }
    }
}

/**
 * Beautiful Material You glassmorphic tab navigator
 */
@Composable
fun GlassNavigationBar(activeTab: Int, onTabSelected: (Int) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabButton(
                icon = Icons.Outlined.Mic,
                label = "Capture",
                isSelected = activeTab == 0,
                modifier = Modifier.weight(1f).testTag("tab_capture"),
                onClick = { onTabSelected(0) }
            )
            TabButton(
                icon = Icons.Outlined.Tune,
                label = "Mixer",
                isSelected = activeTab == 1,
                modifier = Modifier.weight(1f).testTag("tab_mixer"),
                onClick = { onTabSelected(1) }
            )
            TabButton(
                icon = Icons.Outlined.AutoAwesome,
                label = "AI Analyst",
                isSelected = activeTab == 2,
                modifier = Modifier.weight(1f).testTag("tab_analyst"),
                onClick = { onTabSelected(2) }
            )
        }
    }
}

@Composable
fun RowScope.TabButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val animatedBg by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f) else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "bg"
    )
    val animatedTint by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tint"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = 64.dp),
                onClick = onClick
            )
            .background(animatedBg)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = animatedTint,
                modifier = Modifier.size(18.dp)
            )
            AnimatedVisibility(visible = isSelected) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = animatedTint
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Capture / Record Screen: User can toggle mic with AEC active, select filter cutoff, and do separator DSP.
 */
@Composable
fun RecordTabContent(
    viewModel: AppViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val separationProgress by viewModel.separationProgress.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val crossoverSetting by viewModel.separationCrossover.collectAsState()
    val widthSetting by viewModel.vocalWidthSetting.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            // Echo cancellation permission banner or information
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Shield",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Acoustic Echo Canceler (AEC)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "App feeds live cancel frames to block speaker feedback when recording alongside musical instrumentals.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                        )
                    }

                    if (!hasPermission) {
                        Button(
                            onClick = onRequestPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Grant", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Active",
                            tint = Color(0xFF66BB6A),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        item {
            // Main Recording controls
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isRecording) "Tap to pause microphone" else "Push to start recording",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bouncy Record Trigger Button
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = if (isRecording) listOf(
                                        Color(0xFFFF8A80),
                                        Color(0xFFD32F2F)
                                    ) else listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.primary
                                    )
                                )
                            )
                            .clickable {
                                if (hasPermission) {
                                    viewModel.toggleRecord()
                                } else {
                                    onRequestPermission()
                                }
                            }
                            .testTag("record_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isRecording) "Stop" else "Record",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Expert DSP tuning controls
                    Text(
                        text = "Expert DSP Filter Calibration",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Split Crossover Center:",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${crossoverSetting.toInt()} Hz",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        Slider(
                            value = crossoverSetting,
                            onValueChange = { viewModel.separationCrossover.value = it },
                            valueRange = 500f..2000f,
                            modifier = Modifier.fillMaxWidth().testTag("crossover_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                            )
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Speech Range Width (Q):",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = String.format("%.2f", widthSetting),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        Slider(
                            value = widthSetting,
                            onValueChange = { viewModel.vocalWidthSetting.value = it },
                            valueRange = 0.2f..1.2f,
                            modifier = Modifier.fillMaxWidth().testTag("speech_width_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Execute Separation
                    if (state == AppViewModel.AppState.PROCESSING) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LinearProgressIndicator(
                                progress = { separationProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Biquad IIR Math separation: ${String.format("%.0f%%", separationProgress * 100f)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    } else {
                        Button(
                            onClick = { viewModel.processAndSeparate() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("process_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AltRoute,
                                contentDescription = "Separate",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Separate Vocal & Instruments", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // History list: loads previous files
        item {
            Text(
                text = "📁 Saved Recording History",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (sessions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recordings yet. Hit the record button above!",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        } else {
            items(sessions) { file ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Size: ${String.format("%.1f KB", file.length() / 1024f)} | Type: Direct RIFF",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            )
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    // Copy selected file to active workspace slot for processing
                                    val destination = File(context.filesDir, "session_raw.wav")
                                    try {
                                        file.copyTo(destination, overwrite = true)
                                        viewModel.processAndSeparate()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error loading session file", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(36.dp).testTag("session_separate_${file.name.take(4)}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Load to Mixer",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    file.delete()
                                    Toast.makeText(context, "Session deleted", Toast.LENGTH_SHORT).show()
                                    viewModel.processAndSeparate() // Trigger visual reload safely
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Mixer Tab: Multi-fader control panel for setting volume, balance panning, solo, mute, effects,
 * combined with audio waveforms & volume output meters.
 */
@Composable
fun MixerTabContent(viewModel: AppViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isPlaying by viewModel.isPlaying.collectAsState()
    val progress by viewModel.playbackProgress.collectAsState()
    val vMeter by viewModel.liveVocalPlayMeter.collectAsState()
    val mMeter by viewModel.liveMusicPlayMeter.collectAsState()

    val vVol by viewModel.vocalVolume.collectAsState()
    val vPan by viewModel.vocalPan.collectAsState()
    val vMute by viewModel.vocalMute.collectAsState()
    val vSolo by viewModel.vocalSolo.collectAsState()
    val vReverb by viewModel.vocalReverb.collectAsState()

    val mVol by viewModel.musicVolume.collectAsState()
    val mPan by viewModel.musicPan.collectAsState()
    val mMute by viewModel.musicMute.collectAsState()
    val mSolo by viewModel.musicSolo.collectAsState()

    val vWave by viewModel.vocalWaveform.collectAsState()
    val mWave by viewModel.musicWaveform.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Output Peak Meter indicators
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🎚️ Live Stereo Output Peak Meters",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Vocals Output Meter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "VOX",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.width(36.dp)
                        )
                        LiveVolumeMeterBar(
                            level = vMeter,
                            color = Color(0xFF40C4FF),
                            modifier = Modifier.weight(1f).testTag("vox_meter")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Music Output Meter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BEAT",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.width(36.dp)
                        )
                        LiveVolumeMeterBar(
                            level = mMeter,
                            color = Color(0xFFFF8F00),
                            modifier = Modifier.weight(1f).testTag("beat_meter")
                        )
                    }
                }
            }
        }

        // Channel Fader 1: Isolated Speech / Vocals
        item {
            MixerChannelFaderCard(
                title = "🎙️ Microscopic Vocal Track",
                volume = vVol,
                pan = vPan,
                isMuted = vMute,
                isSolo = vSolo,
                isReverb = vReverb,
                onVolumeChange = { viewModel.updateVocalVolume(it) },
                onPanChange = { viewModel.updateVocalPan(it) },
                onMuteClick = { viewModel.toggleVocalMute() },
                onSoloClick = { viewModel.toggleVocalSolo() },
                onReverbClick = { viewModel.toggleVocalReverb() },
                accentColor = Color(0xFF40C4FF),
                showVocalFx = true,
                testTagPrefix = "vocal"
            )
        }

        // Channel Fader 2: Isolated Background Music Instruments
        item {
            MixerChannelFaderCard(
                title = "🎵 Backing Instrumental Track",
                volume = mVol,
                pan = mPan,
                isMuted = mMute,
                isSolo = mSolo,
                isReverb = false,
                onVolumeChange = { viewModel.updateMusicVolume(it) },
                onPanChange = { viewModel.updateMusicPan(it) },
                onMuteClick = { viewModel.toggleMusicMute() },
                onSoloClick = { viewModel.toggleMusicSolo() },
                onReverbClick = {},
                accentColor = Color(0xFFFF8F00),
                showVocalFx = false,
                testTagPrefix = "music"
            )
        }

        // Sync Waveform Viewer & Master seekbar section
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🌊 Synchronized Soundwave Grid",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Draw overlapping Waveforms
                    DualWaveformComposite(
                        vocWave = vWave,
                        musWave = mWave,
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Scrub seekbar
                    Slider(
                        value = progress,
                        onValueChange = { viewModel.seekPlayback(it) },
                        modifier = Modifier.fillMaxWidth().testTag("playback_seekbar")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Media Mixer triggers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledIconButton(
                                onClick = { viewModel.toggleMixerPlayback() },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.size(46.dp).testTag("play_mixer_button")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Toggle Play back"
                                )
                            }

                            IconButton(
                                onClick = { viewModel.stopMixerPlayback() },
                                modifier = Modifier
                                    .size(46.dp)
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }

                        // Export mix
                        Button(
                            onClick = {
                                viewModel.exportMixedSession { exportedFile ->
                                    if (exportedFile != null) {
                                        Toast.makeText(context, "Mix exported: ${exportedFile.name}", Toast.LENGTH_LONG).show()

                                        // Launch native android File sharing picker dialogue
                                        try {
                                            val fileUri: Uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                exportedFile
                                            )
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "audio/*"
                                                putExtra(Intent.EXTRA_STREAM, fileUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Mix File"))
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            Toast.makeText(context, "Exported successfully, file path saved", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Export failed. Please record and separate tracks first.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(42.dp).testTag("export_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.IosShare,
                                contentDescription = "Export Link",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Mix", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated slider and volume channel card with dynamic panning sliders.
 */
@Composable
fun MixerChannelFaderCard(
    title: String,
    volume: Float,
    pan: Float,
    isMuted: Boolean,
    isSolo: Boolean,
    isReverb: Boolean,
    onVolumeChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    onMuteClick: () -> Unit,
    onSoloClick: () -> Unit,
    onReverbClick: () -> Unit,
    accentColor: Color,
    showVocalFx: Boolean,
    testTagPrefix: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = accentColor)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // MUTE Button (Active target size is 48dp through padded icon bounds)
                    TextButton(
                        onClick = onMuteClick,
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (isMuted) Color.Red.copy(alpha = 0.2f) else Color.Transparent
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp).testTag("${testTagPrefix}_mute")
                    ) {
                        Text(
                            "MUTE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                color = if (isMuted) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    // SOLO Button
                    TextButton(
                        onClick = onSoloClick,
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (isSolo) accentColor.copy(alpha = 0.25f) else Color.Transparent
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp).testTag("${testTagPrefix}_solo")
                    ) {
                        Text(
                            "SOLO",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                color = if (isSolo) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    // Native Reverb FX button
                    if (showVocalFx) {
                        TextButton(
                            onClick = onReverbClick,
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (isReverb) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp).testTag("${testTagPrefix}_reverb")
                        ) {
                            Text(
                                "FX: REVERB",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    color = if (isReverb) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Volume Sliders
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VolumeDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.weight(1f).testTag("${testTagPrefix}_volume_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = accentColor.copy(alpha = 0.2f)
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = String.format("%.0f%%", volume * 100f),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.End
                )
            }

            // Spatial Panning Dial Slider
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = "L",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.width(16.dp)
                )
                Slider(
                    value = pan,
                    onValueChange = onPanChange,
                    valueRange = -1.0f..1.0f,
                    modifier = Modifier.weight(1f).testTag("${testTagPrefix}_pan_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        activeTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "R",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.width(16.dp),
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (pan < -0.1f) "Pan: ${String.format("%.0f%% L", -pan * 100f)}"
                           else if (pan > 0.1f) "Pan: ${String.format("%.0f%% R", pan * 100f)}"
                           else "Center",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)),
                    modifier = Modifier.width(85.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

/**
 * Highly responsive live horizontal neon decibel bars.
 */
@Composable
fun LiveVolumeMeterBar(level: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .height(10.dp)
            .padding(vertical = 1.dp)
    ) {
        val radius = size.height / 2
        // Draw underlying track background
        drawRoundRect(
            color = color.copy(alpha = 0.15f),
            size = size,
            cornerRadius = CornerRadius(radius, radius)
        )
        // Draw active level progress
        drawRoundRect(
            color = color,
            size = Size(size.width * level.coerceIn(0f, 1f), size.height),
            cornerRadius = CornerRadius(radius, radius)
        )
    }
}

/**
 * Draws overlaying composite waveforms in high color contrast.
 */
@Composable
fun DualWaveformComposite(
    vocWave: List<Float>,
    musWave: List<Float>,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val barCount = vocWave.size.coerceAtMost(musWave.size).coerceAtLeast(30)
        
        val barSpacing = 4f
        val totalSpacing = barSpacing * (barCount - 1)
        val barWidth = (width - totalSpacing) / barCount

        val playProgressIndex = (progress * barCount).toInt()

        for (i in 0 until barCount) {
            val vHeightFactor = vocWave.getOrNull(i) ?: 0.12f
            val mHeightFactor = musWave.getOrNull(i) ?: 0.1f

            val vBarHeight = height * 0.45f * vHeightFactor
            val mBarHeight = height * 0.45f * mHeightFactor

            val x = i * (barWidth + barSpacing)

            // Dynamic colors to reflect seekbar positioning
            val finished = i < playProgressIndex
            val vColor = if (finished) Color(0xFF00E5FF) else Color(0xFF00E5FF).copy(alpha = 0.3f)
            val mColor = if (finished) Color(0xFFFF9100) else Color(0xFFFF9100).copy(alpha = 0.3f)

            // Draw Top: Voice Track values
            drawRoundRect(
                color = vColor,
                topLeft = Offset(x, height / 2 - vBarHeight - 1f),
                size = Size(barWidth, vBarHeight),
                cornerRadius = CornerRadius(6f, 6f)
            )

            // Draw Bottom: Instrument/Music values
            drawRoundRect(
                color = mColor,
                topLeft = Offset(x, height / 2 + 1f),
                size = Size(barWidth, mBarHeight),
                cornerRadius = CornerRadius(6f, 6f)
            )
        }

        // Playhead indicator
        val playheadX = progress * width
        drawLine(
            color = Color.White,
            start = Offset(playheadX, 0f),
            end = Offset(playheadX, height),
            strokeWidth = 2.dp.toPx()
        )
    }
}

/**
 * Gemini Prompt Analyzer Tab Screen.
 */
@Composable
fun AnalystTabContent(viewModel: AppViewModel) {
    val aiResponse by viewModel.aiAnalysisText.collectAsState()
    val isAnalyzing by viewModel.isAnalyzingSpeech.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "💡 Expert AI Sound Analyst",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = "Real-time prompt engineering feedback",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    )
                }

                FilledTonalButton(
                    onClick = { viewModel.requestAiAnalysis() },
                    modifier = Modifier.height(34.dp).testTag("ai_reanalyze")
                ) {
                    Text("Reanalyze", style = MaterialTheme.typography.labelSmall)
                }
            }

            Divider(color = Color.White.copy(alpha = 0.1f))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = if (isAnalyzing) Alignment.Center else Alignment.TopStart
            ) {
                if (isAnalyzing) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Analyzing audio spectral metrics...",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                } else if (aiResponse.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = "AI Waiting",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No analysis loaded yet. Please complete a vocal separation session to load AI advice.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.05f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = "Analysis Check",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Gemini Studio Mastering Proposal",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                )
                            }
                            
                            Text(
                                text = aiResponse,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
