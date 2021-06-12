package com.seanghay.caresens

import android.app.Application
import com.seanghay.caresensble.CareSensBLE

class App: Application() {
    override fun onCreate() {
        super.onCreate()
        CareSensBLE.init(this)
    }
}