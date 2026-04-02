package com.synapse.app.feature.article;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u00004\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\f\n\u0002\u0018\u0002\n\u0002\b\u0002\u001aN\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u00032\u0012\u0010\u0004\u001a\u000e\u0012\u0004\u0012\u00020\u0003\u0012\u0004\u0012\u00020\u00010\u00052\u0006\u0010\u0006\u001a\u00020\u00072\u0012\u0010\b\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u00010\u00052\f\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u00010\nH\u0003\u001a(\u0010\u000b\u001a\u00020\u00012\u0006\u0010\f\u001a\u00020\u00032\f\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u00010\n2\b\b\u0002\u0010\u000e\u001a\u00020\u000fH\u0007\u001a\u001e\u0010\u0010\u001a\u00020\u00012\u0006\u0010\u0011\u001a\u00020\u00032\f\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00010\nH\u0003\u001a\b\u0010\u0013\u001a\u00020\u0001H\u0003\u001aF\u0010\u0014\u001a\u00020\u00012\u0006\u0010\u0015\u001a\u00020\u00032\u0012\u0010\u0016\u001a\u000e\u0012\u0004\u0012\u00020\u0003\u0012\u0004\u0012\u00020\u00010\u00052\u0012\u0010\u0017\u001a\u000e\u0012\u0004\u0012\u00020\u0003\u0012\u0004\u0012\u00020\u00010\u00052\f\u0010\u0018\u001a\b\u0012\u0004\u0012\u00020\u00010\nH\u0003\u001a\u0018\u0010\u0019\u001a\u00020\u00012\u0006\u0010\u001a\u001a\u00020\u00032\u0006\u0010\u001b\u001a\u00020\u001cH\u0002\u001a\u0018\u0010\u001d\u001a\u00020\u00012\u0006\u0010\u001a\u001a\u00020\u00032\u0006\u0010\u001b\u001a\u00020\u001cH\u0002\u00a8\u0006\u001e"}, d2 = {"ArticleInputContent", "", "inputText", "", "onInputChanged", "Lkotlin/Function1;", "selectedTransform", "Lcom/synapse/app/domain/models/TransformType;", "onTransformSelected", "onTransformClick", "Lkotlin/Function0;", "ArticleScreen", "sharedText", "onBack", "viewModel", "Lcom/synapse/app/feature/article/ArticleViewModel;", "ErrorContent", "message", "onRetry", "LoadingContent", "ResultContent", "resultText", "onCopy", "onShare", "onSave", "copyToClipboard", "text", "context", "Landroid/content/Context;", "shareText", "app_debug"})
public final class ArticleScreenKt {
    
    @kotlin.OptIn(markerClass = {androidx.compose.material3.ExperimentalMaterial3Api.class})
    @androidx.compose.runtime.Composable()
    public static final void ArticleScreen(@org.jetbrains.annotations.NotNull()
    java.lang.String sharedText, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onBack, @org.jetbrains.annotations.NotNull()
    com.synapse.app.feature.article.ArticleViewModel viewModel) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void ArticleInputContent(java.lang.String inputText, kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> onInputChanged, com.synapse.app.domain.models.TransformType selectedTransform, kotlin.jvm.functions.Function1<? super com.synapse.app.domain.models.TransformType, kotlin.Unit> onTransformSelected, kotlin.jvm.functions.Function0<kotlin.Unit> onTransformClick) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void LoadingContent() {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void ErrorContent(java.lang.String message, kotlin.jvm.functions.Function0<kotlin.Unit> onRetry) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void ResultContent(java.lang.String resultText, kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> onCopy, kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> onShare, kotlin.jvm.functions.Function0<kotlin.Unit> onSave) {
    }
    
    private static final void copyToClipboard(java.lang.String text, android.content.Context context) {
    }
    
    private static final void shareText(java.lang.String text, android.content.Context context) {
    }
}