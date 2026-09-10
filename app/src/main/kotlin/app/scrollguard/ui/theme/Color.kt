/*
 * Copyright 2025 Atick Faisal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.scrollguard.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Material 3 color palette constants for light and dark themes.
 *
 * These colors serve as fallback values for devices that don't support
 * dynamic color (Android 12+). On supported devices, the app uses colors
 * extracted from the user's wallpaper for a personalized experience.
 *
 * Colors follow the Material 3 design system specification.
 */

/** Light theme primary colors */
val Purple80 = Color(0xFFD0BCFF)

/** Light theme secondary colors */
val PurpleGrey80 = Color(0xFFCCC2DC)

/** Light theme tertiary colors */
val Pink80 = Color(0xFFEFB8C8)

/** Dark theme primary colors */
val Purple40 = Color(0xFF6650a4)

/** Dark theme secondary colors */
val PurpleGrey40 = Color(0xFF625b71)

/** Dark theme tertiary colors */
val Pink40 = Color(0xFF7D5260)
