package com.linxyi.lsmusic.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.linxyi.lsmusic.R
import com.linxyi.lsmusic.ui.theme.LsMusicTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercises the production SAF launchers with isolated activity results; no external files or accounts. */
class DataBackupPickerTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var resultUri: Uri? = null
    private var launched: Intent? = null
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            launched = contract.createIntent(context, input)
            dispatchResult(requestCode, if (resultUri == null) Activity.RESULT_CANCELED else Activity.RESULT_OK, resultUri?.let { Intent().setData(it) })
        }
    }
    private val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
    private var exports = 0
    private var imports = 0
    private var exportedSecrets = false

    private fun render() {
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                LsMusicTheme(dynamicColor = false) {
                    val actions = rememberDataBackupActions(
                        DataBackupUiState(),
                        onExport = { _, secrets -> exports++; exportedSecrets = secrets },
                        onInspectImport = { imports++ }, onImport = {}, onDismiss = {},
                    )
                    DataBackupButtons(actions.export, actions.import, true)
                }
            }
        }
    }

    @Test fun cancelledSystemPickersNeverExportOrImportData() {
        render()
        compose.onNodeWithTag("import-data").performClick()
        compose.runOnIdle { assertEquals(Intent.ACTION_OPEN_DOCUMENT, launched?.action); assertEquals(0, imports) }
        compose.onNodeWithTag("export-data").performClick()
        compose.onNodeWithText(context.getString(R.string.backup_choose_location)).performClick()
        compose.runOnIdle { assertEquals(Intent.ACTION_CREATE_DOCUMENT, launched?.action); assertEquals(0, exports) }
    }

    @Test fun createDocumentResultCarriesOnlyTheExplicitCredentialChoice() {
        resultUri = Uri.parse("content://backup-test/document.json")
        render()
        compose.onNodeWithTag("export-data").performClick()
        compose.onNodeWithTag("backup-include-secrets").performScrollTo().assertIsOff().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("backup-include-secrets").assertIsOn()
        compose.onNodeWithText(context.getString(R.string.backup_choose_location)).performClick()
        compose.runOnIdle {
            assertEquals(1, exports)
            assertTrue(exportedSecrets)
            assertEquals("application/json", launched?.type)
            assertTrue(launched?.getStringExtra(Intent.EXTRA_TITLE)?.endsWith(".lsmusic.json") == true)
        }
    }
}
