package com.rbs.android.example

import android.app.Application
import com.readystatesoftware.chuck.ChuckInterceptor
import com.rettermobile.rio.Rio
import com.rettermobile.rio.service.RioNetworkConfig
import com.rettermobile.rio.util.RioRegion
import okhttp3.Interceptor

/**
 * Created by semihozkoroglu on 7.08.2021.
 */
class App : Application() {

    lateinit var rio: Rio

    override fun onCreate() {
        super.onCreate()

        rio = Rio(
            applicationContext = applicationContext,
//            projectId = "6qub7mnar",
            projectId = "1ktra3skh",
            culture= "en",
            config = RioNetworkConfig.build {
                customDomain = "api.kocailem.retter.io"
                sslPinningEnabled = true
            }
        )
    }
}