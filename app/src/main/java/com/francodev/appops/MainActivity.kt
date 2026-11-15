package com.francodev.appops

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.francodev.appops.shell.ShizukuShell
import com.francodev.appops.ui.AppRoot
import com.francodev.appops.ui.theme.AppOpsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShizukuShell.setContext(this)
        ShizukuShell.init(this)
        setContent {
            AppOpsTheme {
                AppRoot()
            }
        }
    }
}
