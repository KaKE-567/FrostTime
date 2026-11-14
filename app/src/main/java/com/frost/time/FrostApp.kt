package com.frost.time

import android.app.Application
import com.topjohnwu.superuser.Shell

class FrostApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Configure libsu root shell builder
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
    }
}
