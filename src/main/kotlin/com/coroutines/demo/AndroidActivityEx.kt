package com.coroutines.demo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class AndroidActivityEx: ActivitY(), CoroutineScope {
    private val view = VieW()
    private val job = Job() // Bind this scope to a Job instance
    override val coroutineContext: CoroutineContext
        get() = job

    suspend fun getExternalResponse(): Any {
        return Object()
    }

    fun onCreate(savedInstanceState: BundlE?) {
        launch { // couroutine is launched directly from Activity
            view.loadResult(getExternalResponse())
        }
    }

    fun onDestroy() {
        job.cancel() // We cancel any pending work
    }
}

open class ActivitY
open class BundlE
open class VieW{
    fun loadResult(externalResponse: Any) {}
}
