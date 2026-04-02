package com.synapse.app.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class HistoryDao_Impl implements HistoryDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<HistoryEntity> __insertionAdapterOfHistoryEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteHistoryItem;

  public HistoryDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfHistoryEntity = new EntityInsertionAdapter<HistoryEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `history_items` (`id`,`workflowType`,`inputPreview`,`transformType`,`outputText`,`providerType`,`createdAtMillis`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final HistoryEntity entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getWorkflowType() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getWorkflowType());
        }
        if (entity.getInputPreview() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getInputPreview());
        }
        if (entity.getTransformType() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getTransformType());
        }
        if (entity.getOutputText() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getOutputText());
        }
        if (entity.getProviderType() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getProviderType());
        }
        statement.bindLong(7, entity.getCreatedAtMillis());
      }
    };
    this.__preparedStmtOfDeleteHistoryItem = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM history_items WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertHistoryItem(final HistoryEntity item,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfHistoryEntity.insertAndReturnId(item);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteHistoryItem(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteHistoryItem.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteHistoryItem.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<HistoryEntity>> getAllHistory() {
    final String _sql = "SELECT * FROM history_items ORDER BY createdAtMillis DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"history_items"}, new Callable<List<HistoryEntity>>() {
      @Override
      @NonNull
      public List<HistoryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfWorkflowType = CursorUtil.getColumnIndexOrThrow(_cursor, "workflowType");
          final int _cursorIndexOfInputPreview = CursorUtil.getColumnIndexOrThrow(_cursor, "inputPreview");
          final int _cursorIndexOfTransformType = CursorUtil.getColumnIndexOrThrow(_cursor, "transformType");
          final int _cursorIndexOfOutputText = CursorUtil.getColumnIndexOrThrow(_cursor, "outputText");
          final int _cursorIndexOfProviderType = CursorUtil.getColumnIndexOrThrow(_cursor, "providerType");
          final int _cursorIndexOfCreatedAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtMillis");
          final List<HistoryEntity> _result = new ArrayList<HistoryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final HistoryEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpWorkflowType;
            if (_cursor.isNull(_cursorIndexOfWorkflowType)) {
              _tmpWorkflowType = null;
            } else {
              _tmpWorkflowType = _cursor.getString(_cursorIndexOfWorkflowType);
            }
            final String _tmpInputPreview;
            if (_cursor.isNull(_cursorIndexOfInputPreview)) {
              _tmpInputPreview = null;
            } else {
              _tmpInputPreview = _cursor.getString(_cursorIndexOfInputPreview);
            }
            final String _tmpTransformType;
            if (_cursor.isNull(_cursorIndexOfTransformType)) {
              _tmpTransformType = null;
            } else {
              _tmpTransformType = _cursor.getString(_cursorIndexOfTransformType);
            }
            final String _tmpOutputText;
            if (_cursor.isNull(_cursorIndexOfOutputText)) {
              _tmpOutputText = null;
            } else {
              _tmpOutputText = _cursor.getString(_cursorIndexOfOutputText);
            }
            final String _tmpProviderType;
            if (_cursor.isNull(_cursorIndexOfProviderType)) {
              _tmpProviderType = null;
            } else {
              _tmpProviderType = _cursor.getString(_cursorIndexOfProviderType);
            }
            final long _tmpCreatedAtMillis;
            _tmpCreatedAtMillis = _cursor.getLong(_cursorIndexOfCreatedAtMillis);
            _item = new HistoryEntity(_tmpId,_tmpWorkflowType,_tmpInputPreview,_tmpTransformType,_tmpOutputText,_tmpProviderType,_tmpCreatedAtMillis);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getHistoryById(final long id,
      final Continuation<? super HistoryEntity> $completion) {
    final String _sql = "SELECT * FROM history_items WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<HistoryEntity>() {
      @Override
      @Nullable
      public HistoryEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfWorkflowType = CursorUtil.getColumnIndexOrThrow(_cursor, "workflowType");
          final int _cursorIndexOfInputPreview = CursorUtil.getColumnIndexOrThrow(_cursor, "inputPreview");
          final int _cursorIndexOfTransformType = CursorUtil.getColumnIndexOrThrow(_cursor, "transformType");
          final int _cursorIndexOfOutputText = CursorUtil.getColumnIndexOrThrow(_cursor, "outputText");
          final int _cursorIndexOfProviderType = CursorUtil.getColumnIndexOrThrow(_cursor, "providerType");
          final int _cursorIndexOfCreatedAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtMillis");
          final HistoryEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpWorkflowType;
            if (_cursor.isNull(_cursorIndexOfWorkflowType)) {
              _tmpWorkflowType = null;
            } else {
              _tmpWorkflowType = _cursor.getString(_cursorIndexOfWorkflowType);
            }
            final String _tmpInputPreview;
            if (_cursor.isNull(_cursorIndexOfInputPreview)) {
              _tmpInputPreview = null;
            } else {
              _tmpInputPreview = _cursor.getString(_cursorIndexOfInputPreview);
            }
            final String _tmpTransformType;
            if (_cursor.isNull(_cursorIndexOfTransformType)) {
              _tmpTransformType = null;
            } else {
              _tmpTransformType = _cursor.getString(_cursorIndexOfTransformType);
            }
            final String _tmpOutputText;
            if (_cursor.isNull(_cursorIndexOfOutputText)) {
              _tmpOutputText = null;
            } else {
              _tmpOutputText = _cursor.getString(_cursorIndexOfOutputText);
            }
            final String _tmpProviderType;
            if (_cursor.isNull(_cursorIndexOfProviderType)) {
              _tmpProviderType = null;
            } else {
              _tmpProviderType = _cursor.getString(_cursorIndexOfProviderType);
            }
            final long _tmpCreatedAtMillis;
            _tmpCreatedAtMillis = _cursor.getLong(_cursorIndexOfCreatedAtMillis);
            _result = new HistoryEntity(_tmpId,_tmpWorkflowType,_tmpInputPreview,_tmpTransformType,_tmpOutputText,_tmpProviderType,_tmpCreatedAtMillis);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
