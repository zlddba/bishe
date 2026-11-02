package team.wd.atnestapp.data.api

import io.ktor.client.engine.HttpClientEngine

/** 各平台提供自己的 HTTP 引擎实现。 */
expect fun platformHttpClientEngine(): HttpClientEngine
