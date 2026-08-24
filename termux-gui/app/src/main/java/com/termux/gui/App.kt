package com.termux.gui

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentName

/**
 * Makes the Application object globally available enable the handler Threads to register Activity Lifecycle listeners.
 * Also cleans up any leftover Tasks stacks from e.g. a crash and sets the default Exception handler to just print
 * (so exceptions in handler threads don't bring down the whole app).
 */
class App : Application() {
    companion object {
        /** The rest of this codebase only ever calls base [Application]/[Context] methods on
         * this, so it's typed as [Application] rather than [App] -- that's what lets
         * [initForEmbeddedHost] hand it a *different* Application subclass when termux-gui is
         * merged into a host app (e.g. Termux) instead of running standalone as [App]. */
        @JvmStatic
        var APP: Application? = null

        /**
         * Entry point for a host app (e.g. TermuxApplication) that merges termux-gui in rather
         * than running it as its own separately-installed [App]. Does everything [onCreate]
         * below does except install a default uncaught-exception handler -- the host already
         * has its own crash handler, and calling this after it must not clobber that.
         */
        @JvmStatic
        fun initForEmbeddedHost(host: Application) {
            APP = host
            Settings.instance.load(host)
            cleanUpStaleTaskStacks(host)
        }

        private fun cleanUpStaleTaskStacks(context: Application) {
            context.getSystemService(ActivityManager::class.java)?.let {
                for (t in it.appTasks) {
                    if (t.taskInfo.baseIntent.component == ComponentName(context, GUIActivity::class.java) ||
                            t.taskInfo.baseIntent.component == ComponentName(context, GUIActivityDialog::class.java) ||
                            t.taskInfo.baseIntent.component == ComponentName(context, GUIActivityLockscreen::class.java))
                        t.finishAndRemoveTask()
                }
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        APP = this
        Settings.instance.load(this)
        cleanUpStaleTaskStacks(this)
        Thread.setDefaultUncaughtExceptionHandler { _, e -> e.printStackTrace() }
    }


}