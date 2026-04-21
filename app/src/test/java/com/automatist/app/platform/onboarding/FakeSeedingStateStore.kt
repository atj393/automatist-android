package com.automatist.app.platform.onboarding

import com.automatist.app.data.local.SeedingStateStore

class FakeSeedingStateStore : SeedingStateStore {
    var version: Int = 0
    var fakeProfileId: String? = null
    var providerSetupDone: Boolean = false
    var profileSetupDone: Boolean = false
    var defaultSetupDone: Boolean = false
    var workflowSetupDone: Boolean = false

    override suspend fun getSeededDefaultsVersion(): Int = version
    override suspend fun setSeededDefaultsVersion(version: Int) { this.version = version }
    override suspend fun getSeededFakeProfileId(): String? = fakeProfileId
    override suspend fun setSeededFakeProfileId(id: String) { fakeProfileId = id }
    override suspend fun clearSeededFakeProfileId() { fakeProfileId = null }
    override suspend fun markProviderSetupDone() { providerSetupDone = true }
    override suspend fun markProfileSetupDone() { profileSetupDone = true }
    override suspend fun markDefaultSetupDone() { defaultSetupDone = true }
    override suspend fun markWorkflowSetupDone() { workflowSetupDone = true }
}
