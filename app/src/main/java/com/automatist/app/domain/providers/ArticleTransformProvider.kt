package com.automatist.app.domain.providers

import com.automatist.app.domain.models.ArticleInput
import com.automatist.app.domain.models.TransformResult
import com.automatist.app.domain.models.TransformType

interface ArticleTransformProvider {
    suspend fun transform(
        input: ArticleInput,
        type: TransformType
    ): Result<TransformResult>
}
