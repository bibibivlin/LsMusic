package com.linxyi.lsmusic.ui

import android.content.Context
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/** A locale-aware message that is resolved only when it is displayed. */
sealed interface UiText {
    data class Resource(
        @StringRes val id: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    data class Plural(
        @PluralsRes val id: Int,
        val quantity: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    data class Raw(val value: String) : UiText
}

fun UiText.resolve(context: Context): String = resolve(context.resources)

fun UiText.resolve(resources: Resources): String = when (this) {
    is UiText.Resource -> resources.getString(id, *args.map { it.resolveArgument(resources) }.toTypedArray())
    is UiText.Plural -> resources.getQuantityString(
        id,
        quantity,
        *args.map { it.resolveArgument(resources) }.toTypedArray(),
    )
    is UiText.Raw -> value
}

private fun Any.resolveArgument(resources: Resources): Any =
    if (this is UiText) resolve(resources) else this
