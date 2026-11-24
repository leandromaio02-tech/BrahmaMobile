package com.brahma.mobile.util

import android.content.Context
import com.brahma.mobile.BrahmaApp

val Context.brahmaApp: BrahmaApp
    get() = applicationContext as BrahmaApp
