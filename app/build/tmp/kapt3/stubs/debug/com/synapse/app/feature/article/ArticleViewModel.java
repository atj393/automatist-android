package com.synapse.app.feature.article;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000@\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0010\u0002\n\u0002\b\t\b\u0007\u0018\u00002\u00020\u0001B\u0017\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u0006\u0010\u0019\u001a\u00020\u001aJ\u0006\u0010\u001b\u001a\u00020\u001aJ\u0006\u0010\u001c\u001a\u00020\u001aJ\u000e\u0010\u001d\u001a\u00020\u001a2\u0006\u0010\u001e\u001a\u00020\fJ\u000e\u0010\u001f\u001a\u00020\u001a2\u0006\u0010 \u001a\u00020\tJ\u0006\u0010!\u001a\u00020\u001aJ\u000e\u0010\"\u001a\u00020\u001a2\u0006\u0010 \u001a\u00020\tR\u0014\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\n\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\t0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\f0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u000e0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\t0\u0010\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0019\u0010\u0013\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\t0\u0010\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0012R\u0017\u0010\u0015\u001a\b\u0012\u0004\u0012\u00020\f0\u0010\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0012R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u0017\u001a\b\u0012\u0004\u0012\u00020\u000e0\u0010\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0012\u00a8\u0006#"}, d2 = {"Lcom/synapse/app/feature/article/ArticleViewModel;", "Landroidx/lifecycle/ViewModel;", "transformProvider", "Lcom/synapse/app/domain/providers/ArticleTransformProvider;", "historyRepository", "Lcom/synapse/app/domain/repositories/HistoryRepository;", "(Lcom/synapse/app/domain/providers/ArticleTransformProvider;Lcom/synapse/app/domain/repositories/HistoryRepository;)V", "_inputText", "Lkotlinx/coroutines/flow/MutableStateFlow;", "", "_saveStatus", "_selectedTransform", "Lcom/synapse/app/domain/models/TransformType;", "_uiState", "Lcom/synapse/app/feature/article/ArticleUiState;", "inputText", "Lkotlinx/coroutines/flow/StateFlow;", "getInputText", "()Lkotlinx/coroutines/flow/StateFlow;", "saveStatus", "getSaveStatus", "selectedTransform", "getSelectedTransform", "uiState", "getUiState", "reset", "", "resetSaveStatus", "saveResult", "selectTransform", "type", "setInitialTextOnce", "text", "transform", "updateInputText", "app_debug"})
@dagger.hilt.android.lifecycle.HiltViewModel()
public final class ArticleViewModel extends androidx.lifecycle.ViewModel {
    @org.jetbrains.annotations.NotNull()
    private final com.synapse.app.domain.providers.ArticleTransformProvider transformProvider = null;
    @org.jetbrains.annotations.NotNull()
    private final com.synapse.app.domain.repositories.HistoryRepository historyRepository = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.synapse.app.feature.article.ArticleUiState> _uiState = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.synapse.app.feature.article.ArticleUiState> uiState = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> _inputText = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.lang.String> inputText = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.synapse.app.domain.models.TransformType> _selectedTransform = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.synapse.app.domain.models.TransformType> selectedTransform = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> _saveStatus = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.lang.String> saveStatus = null;
    
    @javax.inject.Inject()
    public ArticleViewModel(@org.jetbrains.annotations.NotNull()
    com.synapse.app.domain.providers.ArticleTransformProvider transformProvider, @org.jetbrains.annotations.NotNull()
    com.synapse.app.domain.repositories.HistoryRepository historyRepository) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.synapse.app.feature.article.ArticleUiState> getUiState() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.lang.String> getInputText() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.synapse.app.domain.models.TransformType> getSelectedTransform() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.lang.String> getSaveStatus() {
        return null;
    }
    
    public final void updateInputText(@org.jetbrains.annotations.NotNull()
    java.lang.String text) {
    }
    
    public final void selectTransform(@org.jetbrains.annotations.NotNull()
    com.synapse.app.domain.models.TransformType type) {
    }
    
    public final void setInitialTextOnce(@org.jetbrains.annotations.NotNull()
    java.lang.String text) {
    }
    
    public final void transform() {
    }
    
    public final void reset() {
    }
    
    public final void resetSaveStatus() {
    }
    
    public final void saveResult() {
    }
}