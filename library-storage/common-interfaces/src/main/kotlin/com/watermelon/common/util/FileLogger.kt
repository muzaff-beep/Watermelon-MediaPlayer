package com.watermelon.common.util

/**
 * Standard-level file logger. Writes to Documents/watermelon.log so issues can be
 * diagnosed on-device without a PC.
 *
 * Standard level = lifecycle events, navigation, repository calls, query result COUNTS
 * (not full data), user actions, state changes, and errors. No per-value or per-loop spam.
 *
 * The actual file-writing implementation is injected once at app startup via [install],
 * so this object can live in common-interfaces while the Android file I/O stays in app.
 */
object FileLogger {

    @Volatile private var sink: ((String) -> Unit)? = null

    /** Called once from MainActivity with a function that appends a line to the log file. */
    fun install(writer: (String) -> Unit) { sink = writer }

    private fun ts(): String {
        val now = System.currentTimeMillis()
        return java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
            .format(java.util.Date(now))
    }

    private fun write(level: String, tag: String, msg: String) {
        sink?.invoke("${ts()} [$level] $tag: $msg")
    }

    fun i(tag: String, msg: String) = write("INFO", tag, msg)
    fun w(tag: String, msg: String) = write("WARN", tag, msg)
    fun e(tag: String, msg: String, t: Throwable? = null) {
        write("ERROR", tag, msg + (t?.let { " | ${it.javaClass.simpleName}: ${it.message}" } ?: ""))
    }
}
