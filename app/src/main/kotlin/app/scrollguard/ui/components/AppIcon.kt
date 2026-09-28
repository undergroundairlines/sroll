/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.ui.components

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

@Composable
fun AppIcon(packageName: String, size: Int = 42) {
    val context = LocalContext.current
    val icon = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName)
                .toBitmap(width = 64, height = 64)
                .asImageBitmap()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(size.dp))
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(packageName.substringAfterLast('.').take(1).uppercase())
        }
    }
}
