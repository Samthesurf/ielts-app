package com.samuel.ielts.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.samuel.ielts.ui.theme.IeltsSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface AssetImageState {
    data object Loading : AssetImageState
    data object Missing : AssetImageState
    data class Ready(val imageBitmap: ImageBitmap) : AssetImageState
}

suspend fun decodeAssetImageState(
    context: Context,
    assetPath: String?
): AssetImageState {
    val normalizedPath = assetPath?.trim()?.takeIf { it.isNotEmpty() }
        ?: return AssetImageState.Missing
    val decoded = withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open(normalizedPath).use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }.getOrNull()
    }
    return if (decoded != null) {
        AssetImageState.Ready(decoded)
    } else {
        AssetImageState.Missing
    }
}

@Composable
fun rememberAssetImageState(assetPath: String?): AssetImageState {
    val context = LocalContext.current
    val normalizedPath = assetPath?.trim()?.takeIf { it.isNotEmpty() }
    var state by remember(normalizedPath) {
        mutableStateOf<AssetImageState>(
            if (normalizedPath == null) AssetImageState.Missing else AssetImageState.Loading
        )
    }
    LaunchedEffect(context, normalizedPath) {
        if (normalizedPath == null) {
            state = AssetImageState.Missing
            return@LaunchedEffect
        }
        state = AssetImageState.Loading
        state = decodeAssetImageState(context, normalizedPath)
    }
    return state
}

@Composable
fun AssetImageThumbnailCard(
    imageBitmap: ImageBitmap,
    contentDescription: String,
    onClick: () -> Unit,
    caption: String? = null,
    modifier: Modifier = Modifier,
    thumbnailHeight: Dp = 148.dp
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(IeltsSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(thumbnailHeight)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color.White)
            ) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(IeltsSpacing.Xs)
                )
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(IeltsSpacing.Xs)
                ) {
                    Icon(
                        imageVector = IeltsIcons.ZoomIn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(IeltsSpacing.Xs)
                            .size(IeltsSpacing.SmallIcon)
                    )
                }
            }

            if (!caption.isNullOrBlank()) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = IeltsSpacing.Xs)
                )
            }
        }
    }
}

@Composable
fun MissingAssetImagePlaceholder(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                horizontal = IeltsSpacing.Md,
                vertical = IeltsSpacing.Sm
            )
        )
    }
}

@Composable
fun AssetImageZoomDialog(
    imageBitmap: ImageBitmap,
    title: String,
    imageContentDescription: String,
    onDismiss: () -> Unit,
    closeContentDescription: String = "Close image preview"
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val minScale = 1f
    val maxScale = 4f
    val zoomStep = 0.5f

    var scale by remember { mutableFloatStateOf(minScale) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    fun clampOffset(candidate: Offset, targetScale: Float): Offset {
        if (targetScale <= minScale || containerSize == IntSize.Zero) return Offset.Zero
        val maxX = ((containerSize.width * (targetScale - 1f)) / 2f).coerceAtLeast(0f)
        val maxY = ((containerSize.height * (targetScale - 1f)) / 2f).coerceAtLeast(0f)
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY)
        )
    }

    fun updateScale(targetScale: Float) {
        val clamped = targetScale.coerceIn(minScale, maxScale)
        scale = clamped
        offset = clampOffset(offset, clamped)
    }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(minScale, maxScale)
        scale = nextScale
        offset = clampOffset(offset + panChange, nextScale)
    }

    val zoomPercent = (scale * 100).toInt()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(IeltsSpacing.Md),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = IeltsSpacing.Md,
                            end = IeltsSpacing.Xs,
                            top = IeltsSpacing.Xs,
                            bottom = IeltsSpacing.Xs
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(IeltsSpacing.TouchTarget)
                    ) {
                        Icon(
                            imageVector = IeltsIcons.Close,
                            contentDescription = closeContentDescription,
                            modifier = Modifier.size(IeltsSpacing.ActionIcon)
                        )
                    }
                }

                HorizontalDivider(color = borderColor)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.White)
                        .clipToBounds()
                        .onSizeChanged { containerSize = it }
                        .transformable(state = transformableState),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = imageContentDescription,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(IeltsSpacing.Sm)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                }

                HorizontalDivider(color = borderColor)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = IeltsSpacing.Md, vertical = IeltsSpacing.Xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pinch or tap +/- to zoom",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(IeltsSpacing.Xs)
                    ) {
                        IconButton(
                            onClick = { updateScale(scale - zoomStep) },
                            enabled = scale > minScale,
                            modifier = Modifier.size(IeltsSpacing.TouchTarget)
                        ) {
                            Icon(
                                imageVector = IeltsIcons.ZoomOut,
                                contentDescription = "Zoom out",
                                modifier = Modifier.size(IeltsSpacing.ActionIcon)
                            )
                        }
                        TextButton(
                            onClick = {
                                scale = minScale
                                offset = Offset.Zero
                            },
                            enabled = scale > minScale,
                            modifier = Modifier.semantics {
                                contentDescription = "Reset zoom, currently $zoomPercent percent"
                            }
                        ) {
                            Text(
                                text = "$zoomPercent%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(
                            onClick = { updateScale(scale + zoomStep) },
                            enabled = scale < maxScale,
                            modifier = Modifier.size(IeltsSpacing.TouchTarget)
                        ) {
                            Icon(
                                imageVector = IeltsIcons.ZoomIn,
                                contentDescription = "Zoom in",
                                modifier = Modifier.size(IeltsSpacing.ActionIcon)
                            )
                        }
                    }
                }
            }
        }
    }
}
