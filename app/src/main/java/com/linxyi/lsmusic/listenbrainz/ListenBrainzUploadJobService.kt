package com.linxyi.lsmusic.listenbrainz

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.linxyi.lsmusic.ui.AppPreferencesStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ListenBrainzUploadJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var uploadJob: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        uploadJob?.cancel()
        uploadJob = scope.launch {
            val shouldRetry = try {
                val preferences = AppPreferencesStore(applicationContext).load()
                val shouldUpload = preferences.listenBrainzEnabled && preferences.listenBrainzToken.isNotBlank()
                val result = if (shouldUpload) {
                    PendingListenRepository.get(applicationContext).upload(preferences.listenBrainzToken)
                } else {
                    null
                }
                result != null && result.remainingCount > 0
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.w(TAG, "ListenBrainz background upload failed", error)
                true
            }
            if (isActive) jobFinished(params, shouldRetry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        uploadJob?.cancel()
        uploadJob = null
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "ListenBrainzUpload"
    }
}

object ListenBrainzUploadScheduler {
    private const val JOB_ID = 0x4c53424a

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val job = JobInfo.Builder(
            JOB_ID,
            ComponentName(context, ListenBrainzUploadJobService::class.java),
        )
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()
        if (scheduler.schedule(job) == JobScheduler.RESULT_FAILURE) {
            Log.w(TAG, "Unable to schedule ListenBrainz background upload")
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
    }

    private const val TAG = "ListenBrainzUpload"
}
