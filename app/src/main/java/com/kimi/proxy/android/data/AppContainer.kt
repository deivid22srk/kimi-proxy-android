package com.kimi.proxy.android.data

import android.content.Context

/**
 * Manual DI container — keeps a single AccountRepository and ProxyApi
 * alive for the lifetime of the Application. Compose screens obtain these
 * via [com.kimi.proxy.android.ui.LocalAppContainer].
 */
class AppContainer(context: Context) {
    val repository = AccountRepository(context.applicationContext)
    val proxyApi = ProxyApi(context.applicationContext)
}
