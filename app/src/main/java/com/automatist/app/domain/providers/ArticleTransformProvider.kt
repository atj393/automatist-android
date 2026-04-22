package com.automatist.app.domain.providers

import com.automatist.app.domain.models.ArticleInput
import com.automatist.app.domain.models.TransformResult
import com.automatist.app.domain.models.TransformType

interface ArticleTransformProvider {
    suspend fun transform(
        input: ArticleInput,
        type: TransformType
    ): Result<TransformResult>

    /**
     * Clears any transient runtime state a provider may hold between stages
     * (e.g. cached session metadata, last-phase diagnostic timings). The
     * default implementation is a no-op — stateless cloud providers never
     * need to override it. Providers that hold on-device runtime state
     * (local/offline) override this to release stage-boundary state.
     *
     * The engine invokes this at the following boundaries so a single run
     * doesn't leak state across stages and one run doesn't leak into the next:
     *  - before a new workflow run begins
     *  - between a per-action preprocessing pass and the next action
     *  - before the final generation stage
     *  - after the run completes OR fails
     *
     * Must be cheap: called multiple times per run. Must not throw.
     */
    suspend fun resetForNewStage(reason: String) { /* no-op for stateless providers */ }
}
