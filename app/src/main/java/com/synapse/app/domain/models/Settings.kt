package com.synapse.app.domain.models

data class AppSettings(
    val activeProvider: ProviderType = ProviderType.FAKE
)
