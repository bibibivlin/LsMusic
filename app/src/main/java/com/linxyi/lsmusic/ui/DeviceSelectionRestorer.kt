package com.linxyi.lsmusic.ui

internal fun restoreDeviceSelection(
    currentId: String?,
    rememberedId: String?,
    fallbackId: String?,
    isSearching: Boolean,
    initialSelectionResolved: Boolean,
    userSelectedDevice: Boolean,
): String? {
    if (userSelectedDevice || initialSelectionResolved) return currentId ?: fallbackId
    return rememberedId ?: currentId ?: fallbackId.takeUnless { isSearching }
}
