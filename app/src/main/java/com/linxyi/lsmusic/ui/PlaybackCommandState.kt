package com.linxyi.lsmusic.ui

internal data class PlaybackCommandIdentity(
    val rendererId: String?,
    val queueId: String?,
    val playbackGeneration: Long,
    val commandGeneration: Long,
)
