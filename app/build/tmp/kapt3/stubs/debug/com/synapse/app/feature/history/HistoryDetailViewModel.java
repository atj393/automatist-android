package com.synapse.app.feature.history;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\t\n\u0000\b\u0007\u0018\u00002\u00020\u0001B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u000e\u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000fR\u0016\u0010\u0005\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0019\u0010\b\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00070\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0010"}, d2 = {"Lcom/synapse/app/feature/history/HistoryDetailViewModel;", "Landroidx/lifecycle/ViewModel;", "repository", "Lcom/synapse/app/domain/repositories/HistoryRepository;", "(Lcom/synapse/app/domain/repositories/HistoryRepository;)V", "_item", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/synapse/app/domain/models/HistoryItem;", "item", "Lkotlinx/coroutines/flow/StateFlow;", "getItem", "()Lkotlinx/coroutines/flow/StateFlow;", "loadItem", "", "id", "", "app_debug"})
@dagger.hilt.android.lifecycle.HiltViewModel()
public final class HistoryDetailViewModel extends androidx.lifecycle.ViewModel {
    @org.jetbrains.annotations.NotNull()
    private final com.synapse.app.domain.repositories.HistoryRepository repository = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.synapse.app.domain.models.HistoryItem> _item = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.synapse.app.domain.models.HistoryItem> item = null;
    
    @javax.inject.Inject()
    public HistoryDetailViewModel(@org.jetbrains.annotations.NotNull()
    com.synapse.app.domain.repositories.HistoryRepository repository) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.synapse.app.domain.models.HistoryItem> getItem() {
        return null;
    }
    
    public final void loadItem(long id) {
    }
}