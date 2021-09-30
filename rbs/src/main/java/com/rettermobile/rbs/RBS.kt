package com.rettermobile.rbs

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import com.auth0.android.jwt.JWT
import com.google.gson.Gson
import com.rettermobile.rbs.model.RBSClientAuthStatus
import com.rettermobile.rbs.model.RBSCulture
import com.rettermobile.rbs.model.RBSUser
import com.rettermobile.rbs.service.RBSServiceImp
import com.rettermobile.rbs.service.RequestType
import com.rettermobile.rbs.service.model.RBSTokenResponse
import com.rettermobile.rbs.util.Logger
import com.rettermobile.rbs.util.RBSRegion
import com.rettermobile.rbs.util.getBase64EncodeString
import com.rettermobile.rbs.util.isForegrounded
import kotlinx.coroutines.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.Semaphore


/**
 * Created by semihozkoroglu on 22.11.2020.
 */
class RBS(
    val applicationContext: Context,
    val projectId: String,
    val region: RBSRegion = RBSRegion.EU_WEST_1,
    sslPinningEnabled: Boolean = true,
    socketEnable: Boolean = false
) {

    private var webSocketListener: WebSocketListener? = null
    private var logListener: Logger? = null

    private val availableRest = Semaphore(1, true)

    private val logger = object : Logger {
        override fun log(message: String) {
            Log.e("RBSService", message)
            logListener?.log(message)
        }
    }

    private val preferences = Preferences(applicationContext)
    private val service = RBSServiceImp(projectId, region, sslPinningEnabled, logger)
    private val gson = Gson()

    private var webSocket: RBSWebSocket? = null

    private var listener: ((RBSClientAuthStatus, RBSUser?) -> Unit)? = null

    private val actionConnectTag = "CONNECT_SOCKET"

    private var tokenInfo: RBSTokenResponse? = null
        set(value) {
            field = value

            if (value != null) {
                // Save to device
                preferences.setString(Preferences.Keys.TOKEN_INFO, gson.toJson(value))
            } else {
                // Logout
                preferences.deleteKey(Preferences.Keys.TOKEN_INFO)
            }

            sendAction(action = actionConnectTag)
            sendAuthStatus()
        }

    init {
        if (socketEnable) {
            registerActivityLifecycles()

            webSocket = RBSWebSocket(region, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocketListener?.onOpen(webSocket, response)

                    logger.log("RBSWebSocket socket semaphore released - onOpen")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    logger.log("RBSWebSocket reconnect to socket")

                    logger.log("RBSWebSocket socket semaphore released - onFailure")

                    webSocketListener?.onFailure(webSocket, t, response)

                    // send dummy event for auth token
                    sendAction(action = actionConnectTag)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    super.onClosed(webSocket, code, reason)

                    logger.log("RBSWebSocket socket semaphore released - onClosed")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    webSocketListener?.onMessage(webSocket, text)
                }
            }, logger)
        }

        val infoJson = preferences.getString(Preferences.Keys.TOKEN_INFO)

        if (!TextUtils.isEmpty(infoJson)) {
            tokenInfo = gson.fromJson(infoJson, RBSTokenResponse::class.java)
        }
    }

    fun setOnClientAuthStatusChangeListener(l: (RBSClientAuthStatus, RBSUser?) -> Unit) {
        listener = l

        sendAuthStatus()
    }

    fun authenticateWithCustomToken(customToken: String, error: ((Throwable?) -> Unit)? = null) {
        GlobalScope.launch {
            async(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    listener?.invoke(RBSClientAuthStatus.AUTHENTICATING, null)
                }

                if (!TextUtils.isEmpty(customToken)) {
                    val res =
                        kotlin.runCatching {
                            executeRunBlock(
                                customToken = customToken,
                                requestType = RequestType.REQUEST
                            )
                        }

                    if (res.isSuccess) {
                        withContext(Dispatchers.Main) {
                            res
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            error?.invoke(res.exceptionOrNull())
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        error?.invoke(IllegalArgumentException("customToken must not be null or empty"))
                    }
                }
            }
        }
    }

    fun sendAction(
        action: String,
        data: Map<String, Any> = mapOf(),
        headers: Map<String, String> = mapOf(),
        culture: RBSCulture? = null,
        success: ((String?) -> Unit)? = null,
        error: ((Throwable?) -> Unit)? = null
    ) {
        GlobalScope.launch {
            async(Dispatchers.IO) {
                if (!TextUtils.isEmpty(action)) {
                    val res =
                        kotlin.runCatching {
                            executeRunBlock(
                                action = action,
                                requestJsonString = Gson().toJson(data),
                                headers = headers,
                                culture = culture,
                                requestType = RequestType.REQUEST
                            )
                        }

                    if (res.isSuccess) {
                        withContext(Dispatchers.Main) {
                            success?.invoke(res.getOrNull())
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            error?.invoke(res.exceptionOrNull())
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        error?.invoke(IllegalArgumentException("action must not be null or empty"))
                    }
                }
            }
        }
    }

    fun generateGetActionUrl(
        action: String,
        data: Map<String, Any> = mapOf(),
        success: ((String?) -> Unit)? = null,
        error: ((Throwable?) -> Unit)? = null
    ) {
        GlobalScope.launch {
            async(Dispatchers.IO) {
                if (!TextUtils.isEmpty(action)) {
                    val res =
                        kotlin.runCatching {
                            executeRunBlock(
                                action = action,
                                requestJsonString = Gson().toJson(data),
                                requestType = RequestType.GENERATE_AUTH
                            )
                        }

                    if (res.isSuccess) {
                        withContext(Dispatchers.Main) {
                            success?.invoke(res.getOrNull())
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            error?.invoke(res.exceptionOrNull())
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        error?.invoke(IllegalArgumentException("action must not be null or empty"))
                    }
                }
            }
        }
    }

    fun generatePublicGetActionUrl(
        action: String,
        data: Map<String, Any> = mapOf()
    ): String {
        val toJson = Gson().toJson(data)
        val requestJsonEncodedString = toJson.getBase64EncodeString()

        logger.log("generateUrl public projectId: $projectId")
        logger.log("generateUrl public action: $action")
        logger.log("generateUrl public body: $toJson")
        logger.log("generateUrl public bodyEncodeString: $requestJsonEncodedString")

        return region.getUrl + "user/action/$projectId/$action?data=$requestJsonEncodedString"
    }

    private suspend fun executeRunBlock(
        customToken: String? = null,
        action: String? = null,
        requestJsonString: String? = null,
        headers: Map<String, String>? = null,
        culture: RBSCulture? = null,
        requestType: RequestType
    ): String {
        return exec(customToken, action, requestJsonString, headers, culture, requestType)
    }

    private suspend fun exec(
        customToken: String? = null,
        action: String? = null,
        requestJsonString: String? = null,
        headers: Map<String, String>? = null,
        culture: RBSCulture? = null,
        requestType: RequestType
    ): String {
        // Token info control
        if (!TextUtils.isEmpty(customToken)) {
            val res = service.authWithCustomToken(customToken!!)

            return if (res.isSuccess) {
                logger.log("authWithCustomToken success")

                tokenInfo = res.getOrNull()

                "TOKEN OK"
            } else {
                logger.log("authWithCustomToken fail")

                throw res.exceptionOrNull() ?: IllegalAccessError("AuthWithCustomToken fail")
            }
        } else {
            if (TextUtils.isEmpty(tokenInfo?.accessToken)) {
                val res = service.getAnonymousToken(projectId)

                if (res.isSuccess) {
                    logger.log("getAnonymousToken success")

                    tokenInfo = res.getOrNull()
                } else {
                    logger.log("getAnonymousToken fail")

                    throw res.exceptionOrNull() ?: IllegalAccessError("GetAnonymousToken fail")
                }
            } else {
                availableRest.acquire()

                if (isTokenRefreshRequired()) {
                    val res = service.refreshToken(tokenInfo!!.refreshToken)

                    if (res.isSuccess) {
                        logger.log("refreshToken success")

                        tokenInfo = res.getOrNull()
                    } else {
                        logger.log("refreshToken fail signOut called")
                        signOut()

                        logger.log("refreshToken fail")
                        throw IllegalAccessException("Refresh token expired")
                    }
                }

                availableRest.release()
            }
        }

        if (TextUtils.equals(action, actionConnectTag)) {
            logger.log("RBSManager connect called")
            webSocket?.connect(tokenInfo?.accessToken)

            return ""
        } else {
            val res = service.executeAction(
                tokenInfo!!.accessToken,
                action!!,
                requestJsonString ?: Gson().toJson(null),
                headers ?: mapOf(),
                culture,
                requestType
            )

            return if (res.isSuccess) {
                logger.log("executeAction success")

                res.getOrNull()?.string() ?: ""
            } else {
                logger.log("executeAction fail")

                throw res.exceptionOrNull() ?: IllegalAccessError("ExecuteAction fail")
            }
        }
    }

    private fun isTokenRefreshRequired(): Boolean {
        val jwtAccess = JWT(tokenInfo!!.accessToken)
        val accessTokenExpiresAt = jwtAccess.getClaim("exp").asLong()!!

        val jwtRefresh = JWT(tokenInfo!!.refreshToken)
        val refreshTokenExpiresAt = jwtRefresh.getClaim("exp").asLong()!!

        val now = (System.currentTimeMillis() / 1000) + 30

        return now in accessTokenExpiresAt until refreshTokenExpiresAt // now + 280 -> only wait 20 seconds for debugging
    }

    private fun sendAuthStatus() {
        GlobalScope.launch {
            async {
                if (tokenInfo != null) {
                    val jwtAccess = JWT(tokenInfo!!.accessToken)

                    val userId = jwtAccess.getClaim("userId").asString()
                    val anonymous = jwtAccess.getClaim("anonymous").asBoolean()

                    if (anonymous!!) {
                        withContext(Dispatchers.Main) {
                            listener?.invoke(
                                RBSClientAuthStatus.SIGNED_IN_ANONYMOUSLY,
                                RBSUser(userId, anonymous)
                            )
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            listener?.invoke(
                                RBSClientAuthStatus.SIGNED_IN,
                                RBSUser(userId, anonymous)
                            )
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        listener?.invoke(RBSClientAuthStatus.SIGNED_OUT, null)
                    }
                }
            }
        }
    }

    fun signOut() {
        webSocket?.disconnect()
        tokenInfo = null
    }

    private fun registerActivityLifecycles() {
        (applicationContext as Application).registerActivityLifecycleCallbacks(object :
            Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                Log.e(
                    "RBSService",
                    "registerActivityLifecycles onActivityCreated isForegrounded: ${isForegrounded()}"
                )
            }

            override fun onActivityStarted(activity: Activity) {
                Log.e(
                    "RBSService",
                    "registerActivityLifecycles ${activity.javaClass.simpleName} onActivityStarted isForegrounded: ${isForegrounded()}"
                )
            }

            override fun onActivityResumed(activity: Activity) {
                Log.e(
                    "RBSService",
                    "registerActivityLifecycles ${activity.javaClass.simpleName} onActivityResumed isForegrounded: ${isForegrounded()}"
                )

                webSocket?.isConnectionPaused = false
                sendAction(action = actionConnectTag)
            }

            override fun onActivityPaused(activity: Activity) {
                Log.e(
                    "RBSService",
                    "registerActivityLifecycles ${activity.javaClass.simpleName} onActivityPaused isForegrounded: ${isForegrounded()}"
                )
            }

            override fun onActivityStopped(activity: Activity) {
                Log.e(
                    "RBSService",
                    "registerActivityLifecycles ${activity.javaClass.simpleName} onActivityStopped isForegrounded: ${isForegrounded()}"
                )

                if (!isForegrounded()) {
                    Log.e(
                        "RBSService",
                        "registerActivityLifecycles ${activity.javaClass.simpleName} onActivityStopped webSocket disconnect called!"
                    )

                    webSocket?.disconnect(paused = true)
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                Log.e(
                    "RBSService",
                    "registerActivityLifecycles ${activity.javaClass.simpleName} onActivitySaveInstanceState isForegrounded: ${isForegrounded()}"
                )

                if (!isForegrounded()) {
                    Log.e(
                        "RBSService",
                        "registerActivityLifecycles ${activity.javaClass.simpleName} onActivitySaveInstanceState webSocket disconnect called!"
                    )

                    webSocket?.disconnect(paused = true)
                }
            }

            override fun onActivityDestroyed(activity: Activity) {
                Log.e(
                    "RBSService",
                    "registerActivityLifecycles ${activity.javaClass.simpleName} onActivityDestroyed isForegrounded: ${isForegrounded()}"
                )

                if (!isForegrounded()) {
                    Log.e(
                        "RBSService",
                        "registerActivityLifecycles ${activity.javaClass.simpleName} onActivityDestroyed webSocket disconnect called!"
                    )

                    webSocket?.disconnect(paused = true)
                }
            }
        })
    }

    fun setWebSocketListener(listener: WebSocketListener) {
        webSocketListener = listener
    }

    fun setLoggerListener(listener: Logger) {
        logListener = listener
    }
}