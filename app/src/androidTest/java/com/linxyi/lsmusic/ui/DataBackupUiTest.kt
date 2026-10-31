package com.linxyi.lsmusic.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.linxyi.lsmusic.R
import com.linxyi.lsmusic.ui.theme.LsMusicTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DataBackupUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun exportDefaultsToNoSecretsAndRequiresExplicitChoice() {
        val showing = mutableStateOf(true)
        var exported: Boolean? = null
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                DataBackupDialogs(DataBackupUiState(), showing.value,
                    onExport = { exported = it; showing.value = false }, onCloseExport = { showing.value = false },
                    onImport = {}, onDismiss = {},
                )
            }
        }
        compose.onNodeWithTag("backup-include-secrets").performScrollTo().assertIsOff()
        compose.onNodeWithText(context.getString(R.string.backup_secret_warning)).assertExists()
        compose.onNodeWithText(context.getString(R.string.backup_choose_location)).performClick()
        compose.runOnIdle { assertEquals(false, exported); showing.value = true }
        compose.onNodeWithTag("backup-include-secrets").performScrollTo().assertIsOff().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("backup-include-secrets").assertIsOn()
        compose.onNodeWithText(context.getString(R.string.backup_choose_location)).performClick()
        compose.runOnIdle { assertEquals(true, exported) }
    }

    @Test fun importDefaultsToKeepingLocalTokenAndCancelDoesNotApply() {
        val state = mutableStateOf(DataBackupUiState(phase = DataBackupPhase.PREVIEW, containsSecrets = true, queueCount = 2))
        var imported: Boolean? = null
        var dismissed = false
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                DataBackupDialogs(state.value, false, {}, {}, onImport = { imported = it }, onDismiss = { dismissed = true })
            }
        }
        compose.onNodeWithTag("backup-include-secrets").performScrollTo().assertIsOff()
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        compose.runOnIdle { assertTrue(dismissed); assertNull(imported) }
        compose.onNodeWithText(context.getString(R.string.backup_replace)).performClick()
        compose.runOnIdle { assertEquals(false, imported) }
        compose.onNodeWithTag("backup-include-secrets").performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.backup_replace)).performClick()
        compose.runOnIdle { assertEquals(true, imported) }
    }

    @Test fun backupWithoutSecretsHasNoTokenImportOption() {
        var imported: Boolean? = null
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                DataBackupDialogs(DataBackupUiState(phase = DataBackupPhase.PREVIEW), false, {}, {}, { imported = it }, {})
            }
        }
        compose.onNodeWithTag("backup-include-secrets").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.backup_keep_token)).assertExists()
        compose.onNodeWithText(context.getString(R.string.backup_replace)).performClick()
        compose.runOnIdle { assertEquals(false, imported) }
    }
}
