package com.fluortronix.fluortronixapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FluortronixApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Configure XML parsers for Apache POI on Android
        // These system properties are required for POI to work properly on Android
        // See: https://github.com/centic9/poi-on-android
        System.setProperty("org.apache.poi.javax.xml.stream.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl")
        System.setProperty("org.apache.poi.javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl")
        System.setProperty("org.apache.poi.javax.xml.stream.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl")
    }
} 