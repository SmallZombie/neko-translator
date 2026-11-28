package me.avgt.s9e.common.debug

import java.util.concurrent.ConcurrentHashMap

/**
 * The translation requests that are waiting or running right now.
 *
 * <p>Feeds the `translation_queue` entry of the debug screen: the coordinator registers a job when
 * it is queued and removes it when it is done, the debug entry reads [snapshot] once per frame.
 */
object TranslationDebug {
    /** One request in the queue. Times are monotonic and only meaningful inside the debug screen. */
    class Job(val text: String, val language: String, val kind: String) {
        val queuedAt: Long = System.nanoTime()

        @Volatile
        private var startedAt = 0L

        /** True while the job sits in the executor queue and has not reached the network yet. */
        val waiting: Boolean
            get() = startedAt == 0L

        /** Time spent waiting before [start], or running after it. */
        fun millis(): Long {
            val since = if (startedAt == 0L) queuedAt else startedAt
            return (System.nanoTime() - since) / 1_000_000
        }

        /** Called when the executor picks the job up. */
        fun start() {
            startedAt = System.nanoTime()
        }
    }

    private val jobs = ConcurrentHashMap<String, Job>()

    fun begin(key: String, text: String, language: String, kind: String): Job {
        val job = Job(text, language, kind)
        jobs[key] = job
        return job
    }

    fun end(key: String) {
        jobs.remove(key)
    }

    fun snapshot(): List<Job> = jobs.values.sortedBy { it.queuedAt }
}
