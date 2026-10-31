package com.linxyi.lsmusic

import android.app.Application
import com.linxyi.lsmusic.backup.AppBackupStorage

class LsMusicApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppBackupStorage.recoverInterruptedImport(this)
    }
}
