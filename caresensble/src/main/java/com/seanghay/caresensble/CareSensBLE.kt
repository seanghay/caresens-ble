package com.seanghay.caresensble

import android.annotation.SuppressLint
import android.content.Context


class CareSensBLE(val context: Context) {

    companion object {

        @SuppressLint("StaticFieldLeak")
        private var INSTANCE: CareSensBLE? = null

        @JvmStatic
        fun init(context: Context) {
            if (INSTANCE == null) {
                synchronized(CareSensBLE::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = CareSensBLE(context)
                    }
                }
            }
        }

        @JvmStatic
        fun get(): CareSensBLE {
            return INSTANCE ?: throw IllegalStateException("init() was never called")
        }
    }
}