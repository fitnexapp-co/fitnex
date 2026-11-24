package com.fitnex.core.designsystem.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.fitnex.core.designsystem.R
import java.util.Locale

/**
 * Locale used to format dates and numbers. It comes from the app's own string resources, not
 * the device setting, so dates always match the language the UI text is shown in (an English
 * device must not show "November" next to Spanish labels). Adding a translation = adding
 * `values-xx/strings.xml` with its own `ds_locale_tag`.
 */
@Composable
fun rememberAppLocale(): Locale {
    val tag = stringResource(R.string.ds_locale_tag)
    return remember(tag) { Locale.forLanguageTag(tag) }
}
