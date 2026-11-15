package com.xingjian.assistant

import android.app.Application
import com.xingjian.assistant.di.AppContainer

class XjApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
