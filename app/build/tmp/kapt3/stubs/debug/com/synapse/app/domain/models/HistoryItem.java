package com.synapse.app.domain.models;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000<\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0017\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B?\u0012\b\b\u0002\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u0012\u0006\u0010\n\u001a\u00020\u0007\u0012\u0006\u0010\u000b\u001a\u00020\f\u0012\u0006\u0010\r\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u000eJ\t\u0010\u001b\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u001c\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u001d\u001a\u00020\u0007H\u00c6\u0003J\t\u0010\u001e\u001a\u00020\tH\u00c6\u0003J\t\u0010\u001f\u001a\u00020\u0007H\u00c6\u0003J\t\u0010 \u001a\u00020\fH\u00c6\u0003J\t\u0010!\u001a\u00020\u0003H\u00c6\u0003JO\u0010\"\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00072\b\b\u0002\u0010\b\u001a\u00020\t2\b\b\u0002\u0010\n\u001a\u00020\u00072\b\b\u0002\u0010\u000b\u001a\u00020\f2\b\b\u0002\u0010\r\u001a\u00020\u0003H\u00c6\u0001J\u0013\u0010#\u001a\u00020$2\b\u0010%\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010&\u001a\u00020\'H\u00d6\u0001J\t\u0010(\u001a\u00020\u0007H\u00d6\u0001R\u0011\u0010\r\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0010R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013R\u0011\u0010\n\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0013R\u0011\u0010\u000b\u001a\u00020\f\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016R\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u001a\u00a8\u0006)"}, d2 = {"Lcom/synapse/app/domain/models/HistoryItem;", "", "id", "", "workflowType", "Lcom/synapse/app/domain/models/WorkflowType;", "inputPreview", "", "transformType", "Lcom/synapse/app/domain/models/TransformType;", "outputText", "providerType", "Lcom/synapse/app/domain/models/ProviderType;", "createdAtMillis", "(JLcom/synapse/app/domain/models/WorkflowType;Ljava/lang/String;Lcom/synapse/app/domain/models/TransformType;Ljava/lang/String;Lcom/synapse/app/domain/models/ProviderType;J)V", "getCreatedAtMillis", "()J", "getId", "getInputPreview", "()Ljava/lang/String;", "getOutputText", "getProviderType", "()Lcom/synapse/app/domain/models/ProviderType;", "getTransformType", "()Lcom/synapse/app/domain/models/TransformType;", "getWorkflowType", "()Lcom/synapse/app/domain/models/WorkflowType;", "component1", "component2", "component3", "component4", "component5", "component6", "component7", "copy", "equals", "", "other", "hashCode", "", "toString", "app_debug"})
public final class HistoryItem {
    private final long id = 0L;
    @org.jetbrains.annotations.NotNull()
    private final com.synapse.app.domain.models.WorkflowType workflowType = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String inputPreview = null;
    @org.jetbrains.annotations.NotNull()
    private final com.synapse.app.domain.models.TransformType transformType = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String outputText = null;
    @org.jetbrains.annotations.NotNull()
    private final com.synapse.app.domain.models.ProviderType providerType = null;
    private final long createdAtMillis = 0L;
    
    public HistoryItem(long id, @org.jetbrains.annotations.NotNull()
    com.synapse.app.domain.models.WorkflowType workflowType, @org.jetbrains.annotations.NotNull()
    java.lang.String inputPreview, @org.jetbrains.annotations.NotNull()
    com.synapse.app.domain.models.TransformType transformType, @org.jetbrains.annotations.NotNull()
    java.lang.String outputText, @org.jetbrains.annotations.NotNull()
    com.synapse.app.domain.models.ProviderType providerType, long createdAtMillis) {
        super();
    }
    
    public final long getId() {
        return 0L;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.synapse.app.domain.models.WorkflowType getWorkflowType() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getInputPreview() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.synapse.app.domain.models.TransformType getTransformType() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getOutputText() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.synapse.app.domain.models.ProviderType getProviderType() {
        return null;
    }
    
    public final long getCreatedAtMillis() {
        return 0L;
    }
    
    public final long component1() {
        return 0L;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.synapse.app.domain.models.WorkflowType component2() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component3() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.synapse.app.domain.models.TransformType component4() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component5() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.synapse.app.domain.models.ProviderType component6() {
        return null;
    }
    
    public final long component7() {
        return 0L;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.synapse.app.domain.models.HistoryItem copy(long id, @org.jetbrains.annotations.NotNull()
    com.synapse.app.domain.models.WorkflowType workflowType, @org.jetbrains.annotations.NotNull()
    java.lang.String inputPreview, @org.jetbrains.annotations.NotNull()
    com.synapse.app.domain.models.TransformType transformType, @org.jetbrains.annotations.NotNull()
    java.lang.String outputText, @org.jetbrains.annotations.NotNull()
    com.synapse.app.domain.models.ProviderType providerType, long createdAtMillis) {
        return null;
    }
    
    @java.lang.Override()
    public boolean equals(@org.jetbrains.annotations.Nullable()
    java.lang.Object other) {
        return false;
    }
    
    @java.lang.Override()
    public int hashCode() {
        return 0;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public java.lang.String toString() {
        return null;
    }
}