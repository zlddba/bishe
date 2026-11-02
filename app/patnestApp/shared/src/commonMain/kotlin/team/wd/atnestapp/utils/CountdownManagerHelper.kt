package team.wd.atnestapp.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class CountdownManagerHelper(private val scope: CoroutineScope) {
    private var job: Job?=null

    var isRunning: Boolean=false
        private set

    fun startCountdown(
        totalMillis: Long,
        tickIntervalMillis: Long = 1000,
        onTick: ((remainingMillis: Long) -> Unit)? = null,
        onFinish: () -> Unit
    ) {
        require(totalMillis > 0) {"总倒计时时间必须大于0"}
        require(tickIntervalMillis>0) {"更新时间间隔必须大于0"}

        cancelCountdown()

        isRunning=true

        job=scope.launch {
            val mark = TimeSource.Monotonic.markNow()

            onTick?.invoke(totalMillis)

            var nextTickAt=tickIntervalMillis
            while (isActive) {
                val elapsed=mark.elapsedNow().inWholeMilliseconds
                val remaining=totalMillis-elapsed
                if(remaining<=0L)break
                if(elapsed>=nextTickAt) {
                    onTick?.invoke(remaining)
                    nextTickAt+=tickIntervalMillis
                    if(nextTickAt<=elapsed) nextTickAt=elapsed+tickIntervalMillis
                    continue
                }
                delay(minOf(nextTickAt-elapsed,remaining).milliseconds)
            }

            if(isActive) {
                isRunning=false
                onFinish()
            }
        }
    }

    fun cancelCountdown() {
        job?.cancel()
        job=null
        isRunning=false
    }

    fun dispose() {
        cancelCountdown()
    }
}