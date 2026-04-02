package com.synapse.app.data.repositories;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0005\u0018\u00002\u00020\u0001B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0016\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bH\u0096@\u00a2\u0006\u0002\u0010\tJ\u0014\u0010\n\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\r0\f0\u000bH\u0016J\u0018\u0010\u000e\u001a\u0004\u0018\u00010\r2\u0006\u0010\u0007\u001a\u00020\bH\u0096@\u00a2\u0006\u0002\u0010\tJ\u0016\u0010\u000f\u001a\u00020\b2\u0006\u0010\u0010\u001a\u00020\rH\u0096@\u00a2\u0006\u0002\u0010\u0011R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0012"}, d2 = {"Lcom/synapse/app/data/repositories/RoomHistoryRepository;", "Lcom/synapse/app/domain/repositories/HistoryRepository;", "dao", "Lcom/synapse/app/data/local/HistoryDao;", "(Lcom/synapse/app/data/local/HistoryDao;)V", "deleteHistoryItem", "", "id", "", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getHistory", "Lkotlinx/coroutines/flow/Flow;", "", "Lcom/synapse/app/domain/models/HistoryItem;", "getHistoryItem", "saveHistoryItem", "item", "(Lcom/synapse/app/domain/models/HistoryItem;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_debug"})
public final class RoomHistoryRepository implements com.synapse.app.domain.repositories.HistoryRepository {
    @org.jetbrains.annotations.NotNull()
    private final com.synapse.app.data.local.HistoryDao dao = null;
    
    @javax.inject.Inject()
    public RoomHistoryRepository(@org.jetbrains.annotations.NotNull()
    com.synapse.app.data.local.HistoryDao dao) {
        super();
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public kotlinx.coroutines.flow.Flow<java.util.List<com.synapse.app.domain.models.HistoryItem>> getHistory() {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getHistoryItem(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.synapse.app.domain.models.HistoryItem> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object saveHistoryItem(@org.jetbrains.annotations.NotNull()
    com.synapse.app.domain.models.HistoryItem item, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Long> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object deleteHistoryItem(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
}