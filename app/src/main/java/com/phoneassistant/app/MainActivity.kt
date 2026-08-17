package com.phoneassistant.app

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.phoneassistant.app.accessibility.PhoneAssistAccessibilityService
import com.phoneassistant.app.guidance.GuidanceTargetStore
import com.phoneassistant.app.ui.theme.PhoneAssistantTheme

class MainActivity : ComponentActivity() {
    private var accessibilityEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhoneAssistantTheme {
                PhoneAssistHome(accessibilityEnabled = accessibilityEnabled)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accessibilityEnabled = isAccessibilityServiceEnabled(
            context = this,
            serviceClass = PhoneAssistAccessibilityService::class.java,
        )
    }
}

@Composable
private fun PhoneAssistHome(accessibilityEnabled: Boolean) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var guidanceStarted by remember { mutableStateOf(false) }
    var showGoalKeyboard by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf("") }

    LaunchedEffect(showGoalKeyboard) {
        if (showGoalKeyboard) keyboardController?.hide()
    }

    fun closeGoalKeyboard() {
        showGoalKeyboard = false
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 64.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "PhoneAssist",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (accessibilityEnabled) {
                    "Screen access is on"
                } else {
                    "Turn on screen access"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = if (accessibilityEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (accessibilityEnabled) {
                    "Describe your goal. PhoneAssist will highlight one visible control at a time."
                } else {
                    "Screen access lets PhoneAssist identify visible buttons and guide your next step."
                },
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 26.sp,
            )
            Spacer(modifier = Modifier.height(28.dp))
            OutlinedButton(
                onClick = { showGoalKeyboard = true },
                enabled = accessibilityEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = "Your goal",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = target.ifEmpty { "Tap to enter a goal" },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    showGoalKeyboard = false
                    GuidanceTargetStore.set(context, target)
                    guidanceStarted = true
                    context.sendBroadcast(
                        Intent(GuidanceTargetStore.ACTION_GUIDANCE_STARTED)
                            .setPackage(context.packageName),
                    )
                },
                enabled = accessibilityEnabled && target.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = "Start guidance",
                    fontSize = 17.sp,
                )
            }
            if (guidanceStarted) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Guidance started. Open the screen where you want help.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = if (accessibilityEnabled) "Review access" else "Open accessibility settings",
                    fontSize = 17.sp,
                )
            }
        }
    }

    if (showGoalKeyboard) {
        GoalKeyboardDialog(
            value = target,
            onValueChange = {
                target = it
                guidanceStarted = false
            },
            onDismiss = ::closeGoalKeyboard,
        )
    }
}

@Composable
private fun GoalKeyboardDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = value.ifEmpty { "Type your goal" },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                )
                listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM").forEach { keys ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        keys.forEach { key ->
                            OutlinedButton(
                                onClick = { onValueChange(value + key.lowercase()) },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                            ) {
                                Text(key.toString())
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedButton(
                        onClick = { onValueChange(value + " ") },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                    ) {
                        Text("Space")
                    }
                    OutlinedButton(
                        onClick = { onValueChange(value.dropLast(1)) },
                        modifier = Modifier.height(44.dp),
                    ) {
                        Text("Backspace")
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.height(44.dp),
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

private fun isAccessibilityServiceEnabled(
    context: Context,
    serviceClass: Class<out AccessibilityService>,
): Boolean {
    val expectedComponent = ComponentName(context, serviceClass)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()

    return enabledServices
        .split(':')
        .mapNotNull(ComponentName::unflattenFromString)
        .any { component -> component == expectedComponent }
}