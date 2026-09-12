/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class AppSection { BLOCKER, SCREEN_TIME }

@Composable
fun AppSectionSwitcher(
    selected: AppSection,
    onSelected: (AppSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 20.dp),
    ) {
        SectionButton(
            text = "Blocker",
            selected = selected == AppSection.BLOCKER,
            onClick = { onSelected(AppSection.BLOCKER) },
            modifier = Modifier.weight(1f),
        )
        SectionButton(
            text = "Screen time",
            selected = selected == AppSection.SCREEN_TIME,
            onClick = { onSelected(AppSection.SCREEN_TIME) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.padding(horizontal = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}
