package com.linxyi.lsmusic.ui

enum class DataBackupPhase { IDLE, EXPORTING, READING, PREVIEW, IMPORTING, RECOVERY_REQUIRED }

data class DataBackupUiState(
    val phase: DataBackupPhase = DataBackupPhase.IDLE,
    val preferenceCount: Int = 0,
    val queueCount: Int = 0,
    val pendingListenCount: Int = 0,
    val containsSecrets: Boolean = false,
    val message: UiText? = null,
) {
    val isBusy: Boolean get() = phase in setOf(DataBackupPhase.EXPORTING, DataBackupPhase.READING, DataBackupPhase.IMPORTING)
}
