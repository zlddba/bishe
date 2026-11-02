package team.wd.atnestapp.data.api

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

actual fun platformHttpClientEngine(): HttpClientEngine = CIO.create()
