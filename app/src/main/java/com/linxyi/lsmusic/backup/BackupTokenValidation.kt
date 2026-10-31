package com.linxyi.lsmusic.backup

import com.linxyi.lsmusic.listenbrainz.ListenBrainzHttpException
import kotlinx.coroutines.CancellationException

internal enum class BackupTokenValidation { VALID, INVALID, CONNECTION_FAILURE }

/** A null token means the user declined credential import; never contact a service in that case. */
internal suspend fun validateBackupToken(
    token: String?,
    validate: suspend (String) -> Boolean,
): BackupTokenValidation {
    if (token.isNullOrBlank()) return BackupTokenValidation.VALID
    return try {
        if (validate(token)) BackupTokenValidation.VALID else BackupTokenValidation.INVALID
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        if (error is ListenBrainzHttpException && error.statusCode in setOf(401, 403)) {
            BackupTokenValidation.INVALID
        } else {
            BackupTokenValidation.CONNECTION_FAILURE
        }
    }
}
