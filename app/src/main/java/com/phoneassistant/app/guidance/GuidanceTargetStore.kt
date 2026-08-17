package com.phoneassistant.app.guidance

import android.content.Context

object GuidanceTargetStore {
    const val ACTION_GUIDANCE_STARTED = "com.phoneassistant.app.action.GUIDANCE_STARTED"

    private const val PREFERENCES_NAME = "guidance"
    private const val TARGET_KEY = "target"
    private const val REVISION_KEY = "revision"
    private const val STARTED_REVISION_KEY = "started_revision"
    private const val COMPLETED_REVISION_KEY = "completed_revision"
    private const val STOPPED_REVISION_KEY = "stopped_revision"

    fun get(context: Context): String = context
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getString(TARGET_KEY, "")
        .orEmpty()

    fun set(context: Context, target: String) {
        val revision = System.currentTimeMillis()
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(TARGET_KEY, target.trim())
            .putLong(REVISION_KEY, revision)
            .putLong(STARTED_REVISION_KEY, revision)
            .commit()
    }

    fun isStarted(context: Context, revision: Long): Boolean {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return revision != 0L &&
            preferences.getLong(STARTED_REVISION_KEY, Long.MIN_VALUE) == revision &&
            !isComplete(context, revision) &&
            !isStopped(context, revision)
    }

    fun getRevision(context: Context): Long = context
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getLong(REVISION_KEY, 0L)

    fun markComplete(context: Context, revision: Long) {
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(COMPLETED_REVISION_KEY, revision)
            .apply()
    }

    fun isComplete(context: Context, revision: Long): Boolean = revision != 0L && context
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getLong(COMPLETED_REVISION_KEY, Long.MIN_VALUE) == revision

    fun markStopped(context: Context, revision: Long) {
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(STOPPED_REVISION_KEY, revision)
            .apply()
    }

    fun isStopped(context: Context, revision: Long): Boolean = revision != 0L && context
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getLong(STOPPED_REVISION_KEY, Long.MIN_VALUE) == revision
}