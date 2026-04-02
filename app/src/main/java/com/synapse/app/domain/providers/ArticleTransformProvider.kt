package com.synapse.app.domain.providers

import com.synapse.app.domain.models.ArticleInput
import com.synapse.app.domain.models.TransformResult
import com.synapse.app.domain.models.TransformType

interface ArticleTransformProvider {
    suspend fun transform(
        input: ArticleInput,
        type: TransformType
    ): Result<TransformResult>
}
