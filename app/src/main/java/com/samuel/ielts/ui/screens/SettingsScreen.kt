package com.samuel.ielts.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.samuel.ielts.ui.components.IeltsIcons
import com.samuel.ielts.ui.theme.IeltsSpacing
import com.samuel.ielts.ui.theme.ThemeIeltsApp
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale

const val DEFAULT_API_BASE_URL = "https://api.meta.ai/v1"
const val DEFAULT_API_MODEL = "muse-spark-1.3-contributor"
const val AIRPLANE_FRIENDLY_ERROR = "Check connection or airplane mode and try again."

sealed interface SettingsTestState {
    data object Idle : SettingsTestState
    data object Loading : SettingsTestState
    data class Success(val latencyMs: Long, val detail: String = "") : SettingsTestState
    data class Error(val message: String) : SettingsTestState
}

@Composable
fun SettingsScreen(
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    model: String,
    onModelChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    testState: SettingsTestState,
    modifier: Modifier = Modifier
) {
    var apiKeyVisible by rememberSaveable { mutableStateOf(false) }
    val isLoading = testState is SettingsTestState.Loading

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(IeltsSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Md)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)) {
                Text(
                    text = "AI Marker Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Configure your Meta-compatible chat completions endpoint and model.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, NeutralBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(IeltsSpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Md)
                ) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = onBaseUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Base URL") },
                        placeholder = { Text(DEFAULT_API_BASE_URL) },
                        supportingText = {
                            Text("Default: $DEFAULT_API_BASE_URL (OpenAI-compatible v1 endpoint)")
                        }
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = onApiKeyChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("API Key") },
                        visualTransformation = if (apiKeyVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { apiKeyVisible = !apiKeyVisible },
                                modifier = Modifier.size(IeltsSpacing.TouchTarget)
                            ) {
                                Icon(
                                    imageVector = if (apiKeyVisible) {
                                        IeltsIcons.VisibilityOff
                                    } else {
                                        IeltsIcons.Visibility
                                    },
                                    contentDescription = if (apiKeyVisible) "Hide API key" else "Show API key",
                                    modifier = Modifier.size(IeltsSpacing.ActionIcon)
                                )
                            }
                        },
                        supportingText = {
                            Text("Stored in app state only during this session unless persisted by caller.")
                        }
                    )

                    OutlinedTextField(
                        value = model,
                        onValueChange = onModelChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Model") },
                        placeholder = { Text(DEFAULT_API_MODEL) },
                        supportingText = {
                            Text("Default: $DEFAULT_API_MODEL")
                        }
                    )

                    Button(
                        onClick = onTestConnection,
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IeltsSpacing.TouchTarget)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(IeltsSpacing.Sm))
                            Text("Testing Connection...")
                        } else {
                            Text("Test Connection")
                        }
                    }
                }
            }
        }

        item {
            SettingsConnectionResultCard(testState = testState)
        }
    }
}

@Composable
private fun SettingsConnectionResultCard(
    testState: SettingsTestState
) {
    when (testState) {
        SettingsTestState.Idle -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, NeutralBorder)
            ) {
                Text(
                    text = "Status: Idle. Tap Test Connection to verify reachability and latency.",
                    modifier = Modifier.padding(IeltsSpacing.Md),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SettingsTestState.Loading -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = BrandBlueSoft.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, BrandBlue.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(IeltsSpacing.Md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = BrandBlue
                    )
                    Text(
                        text = "Checking endpoint connection...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = BrandBlue
                    )
                }
            }
        }

        is SettingsTestState.Success -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = SuccessGreenBg,
                border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(IeltsSpacing.Md),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Connection succeeded",
                        tint = SuccessGreen,
                        modifier = Modifier.size(IeltsSpacing.ActionIcon)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)) {
                        Text(
                            text = "Connected (${testState.latencyMs} ms)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                        if (testState.detail.isNotBlank()) {
                            Text(
                                text = testState.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = SuccessGreen
                            )
                        }
                    }
                }
            }
        }

        is SettingsTestState.Error -> {
            val safeMessage = sanitizeUserErrorMessage(testState.message)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = ErrorRedBg,
                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(IeltsSpacing.Md),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Connection failed warning",
                        tint = ErrorRed,
                        modifier = Modifier.size(IeltsSpacing.ActionIcon)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)) {
                        Text(
                            text = "Connection failed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = ErrorRed
                        )
                        Text(
                            text = safeMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed
                        )
                    }
                }
            }
        }
    }
}

fun toFriendlyNetworkError(error: Throwable): String {
    val causes = generateSequence(error) { it.cause }
    val isOfflineOrUnreachable = causes.any { cause ->
        cause is UnknownHostException ||
            cause is NoRouteToHostException ||
            cause is ConnectException ||
            cause is SocketTimeoutException
    }
    if (isOfflineOrUnreachable) {
        return AIRPLANE_FRIENDLY_ERROR
    }
    return sanitizeUserErrorMessage(error.message.orEmpty())
}

fun sanitizeUserErrorMessage(rawMessage: String): String {
    val firstLine = rawMessage.lineSequence().firstOrNull()?.trim().orEmpty()
    if (firstLine.isEmpty()) {
        return AIRPLANE_FRIENDLY_ERROR
    }
    val lower = firstLine.lowercase(Locale.US)
    val networkMarkers = listOf(
        "unknownhostexception",
        "unable to resolve host",
        "network is unreachable",
        "enetunreach",
        "ehostunreach",
        "no address associated with hostname",
        "failed to connect",
        "connection refused",
        "timed out",
        "timeout"
    )
    if (networkMarkers.any { lower.contains(it) }) {
        return AIRPLANE_FRIENDLY_ERROR
    }
    val looksLikeStackTrace = lower.contains("exception:") ||
        lower.startsWith("at ") ||
        lower.contains("java.net.") ||
        lower.contains("javax.net.")
    if (looksLikeStackTrace) {
        return AIRPLANE_FRIENDLY_ERROR
    }
    return firstLine
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenIdlePreview() {
    ThemeIeltsApp {
        SettingsScreen(
            baseUrl = DEFAULT_API_BASE_URL,
            onBaseUrlChange = {},
            apiKey = "sk-demo-key",
            onApiKeyChange = {},
            model = DEFAULT_API_MODEL,
            onModelChange = {},
            onTestConnection = {},
            testState = SettingsTestState.Idle
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenSuccessPreview() {
    ThemeIeltsApp {
        SettingsScreen(
            baseUrl = DEFAULT_API_BASE_URL,
            onBaseUrlChange = {},
            apiKey = "sk-demo-key",
            onApiKeyChange = {},
            model = DEFAULT_API_MODEL,
            onModelChange = {},
            onTestConnection = {},
            testState = SettingsTestState.Success(latencyMs = 148L, detail = "Endpoint responded OK.")
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenErrorPreview() {
    ThemeIeltsApp {
        SettingsScreen(
            baseUrl = DEFAULT_API_BASE_URL,
            onBaseUrlChange = {},
            apiKey = "",
            onApiKeyChange = {},
            model = DEFAULT_API_MODEL,
            onModelChange = {},
            onTestConnection = {},
            testState = SettingsTestState.Error(AIRPLANE_FRIENDLY_ERROR)
        )
    }
}
