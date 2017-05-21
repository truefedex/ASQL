package com.phlox.asql;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by PDT on 09.09.2016.
 */
public class ASQL {
    private static final String TAG = ASQL.class.getSimpleName();
    private static WeakReference<ASQL> defaultInstance = null;
    private static InitParams defaultInitParams = null;

    private Context context;
    private String databaseName;
    private int databaseVersion;
    private DatabaseHelper openHelper;
    private Callback callback;
    private ModelsInfoProcessor models = new ModelsInfoProcessor();
    private SQLiteDatabase _cachedDatabase;
    private ExecutorService executor;
    private Handler mainThreadHandler;

    private static class InitParams {
        String databaseName; int databaseVersion; Callback callback;
        public InitParams(String databaseName, int databaseVersion, Callback callback) {
            this.databaseName = databaseName;
            this.databaseVersion = databaseVersion;
            this.callback = callback;
        }
    }

    public interface Callback {
        void onCreate(ASQL asql);
        void onUpgrade(ASQL asql, int oldVersion, int newVersion);
        void onCorruption(ASQL asql);
        ExecutorService getExecutorService();
    }

    public interface ExecCallback {
        void onDone(SQLException exception);
    }

    public interface ResultCallback<T> {
        void onDone(T result, Exception exception);
    }

    public interface AffectedRowsResultCallback {
        void onDone(int affectedRowsCount, SQLException exception);
    }

    public interface InsertResultCallback {
        void onDone(long lastInsertRowId, SQLException exception);
    }

    public static abstract class BaseCallback implements Callback {
        @Override
        public void onCreate(ASQL asql) {}
        @Override
        public void onUpgrade(ASQL asql, int oldVersion, int newVersion) {}
        @Override
        public void onCorruption(ASQL asql) {}
        @Override
        public ExecutorService getExecutorService() {
            return Executors.newCachedThreadPool();
        }
    }

    private ASQL() {

    }

    private void init(Context context, String databaseName, int databaseVersion, Callback callback) {
        this.context = context;
        this.databaseName = databaseName;
        this.databaseVersion = databaseVersion;
        this.callback = callback;
        openHelper = new DatabaseHelper(context, databaseName, null, databaseVersion);
        executor = callback.getExecutorService();
        mainThreadHandler = new Handler(Looper.getMainLooper());
    }

    public Callback getCallback() {
        return callback;
    }

    public static void initDefaultInstance(String databaseName, int databaseVersion, Callback callback) {
        defaultInitParams = new InitParams(databaseName, databaseVersion, callback);
    }

    public static ASQL getDefault(Context applicationContext) {
        ASQL asql;
        if (defaultInstance == null || defaultInstance.get() == null) {
            if (defaultInitParams == null) {
                throw new IllegalStateException("Ypu must first call ASQL.initDefaultInstance method");
            }
            asql = new ASQL();
            defaultInstance = new WeakReference<>(asql);
            asql.init(applicationContext, defaultInitParams.databaseName, defaultInitParams.databaseVersion, defaultInitParams.callback);
        } else {
            asql = defaultInstance.get();
        }
        return asql;
    }

    public DatabaseHelper getOpenHelper() {
        return openHelper;
    }

    public SQLiteDatabase getDB() {
        if (_cachedDatabase != null) {
            return _cachedDatabase;
        }
        return openHelper.getWritableDatabase();
    }

    public long count(Class type) {
        Cursor cursor;
        try {
            String query = "SELECT count(*) FROM " + models.getClassInfo(type).tableName;
            cursor = openHelper.getReadableDatabase().rawQuery(query, null);
        } catch (SQLException e) {
            Log.e(TAG, "SQL Error:", e);
            throw e;
        }

        try {
            cursor.moveToNext();
            return cursor.getLong(0);
        } finally {
            cursor.close();
        }
    }

    public int delete(Object entity) {
        ClassInfo classInfo = models.getClassInfo(entity.getClass());
        String value = models.getFieldValueAsString(classInfo.primaryKey.field, entity);
        String query = String.format("DELETE FROM %s WHERE %s=%s", classInfo.tableName,
                classInfo.primaryKey.name, value);
        SQLiteStatement statement = null;
        try {
            statement = openHelper.getWritableDatabase().compileStatement(query);
        } catch (SQLException e) {
            Log.e(TAG, "SQL Error:", e);
            throw e;
        }
        try {
            return statement.executeUpdateDelete();
        } finally {
            statement.close();
        }
    }

    public void delete(final Object entity, final ResultCallback<Integer> callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                Integer result = null;
                Exception exception = null;
                try {
                    result = delete(entity);
                } catch (Exception e) {
                    exception = e;
                }
                final Integer _result = result;
                final Exception _exception = exception;
                mainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onDone(_result, _exception);
                    }
                });
            }
        });
    }

    public <T> T find(Class<T> type, String whereClause, String[] selectionArgs) throws IllegalAccessException, InstantiationException {
        ClassInfo classInfo = models.getClassInfo(type);
        Cursor cursor;
        try {
            String query = String.format("SELECT * FROM %s WHERE %s", classInfo.tableName, whereClause);
            cursor = openHelper.getReadableDatabase().rawQuery(query, selectionArgs);
        } catch (SQLException e) {
            Log.e(TAG, "SQL Error:", e);
            throw e;
        }

        T result = null;
        try {
            if (cursor.moveToNext()) {
                result = (T) models.instantiateObjectFromCursor(type, cursor);
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    public <T> void find(final Class<T> type, final String whereClause, final String[] selectionArgs, final ResultCallback<T> callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                T result = null;
                Exception exception = null;
                try {
                    result = find(type, whereClause, selectionArgs);
                } catch (Exception e) {
                    exception = e;
                }
                final T _result = result;
                final Exception _exception = exception;
                mainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onDone(_result, _exception);
                    }
                });
            }
        });
    }

    public long save(Object entity) throws IllegalAccessException {
        ClassInfo classInfo = models.getClassInfo(entity.getClass());
        Field keyField = classInfo.primaryKey.field;
        if ((keyField.getType().equals(long.class) || keyField.getType().equals(int.class) || keyField.getType().equals(short.class)) &&
                keyField.getLong(entity) == 0) {
            //look like we attempt to save row with autoincrement key
            SQLiteStatement statement = classInfo.fillInsertAutoincrementPreparedStatement(this, entity);
            long result = statement.executeInsert();
            if (result != -1) {
                if (keyField.getType().equals(long.class))
                    keyField.setLong(entity, result);
                else if (keyField.getType().equals(int.class))
                    keyField.setInt(entity, (int)result);
                else if (keyField.getType().equals(short.class))
                    keyField.setShort(entity, (short) result);
            }
            return result;
        } else {
            SQLiteStatement statement = classInfo.fillSavePreparedStatement(this, entity);
            return statement.executeInsert();
        }
    }

    public void save(final Object entity, final ResultCallback<Long> callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                Long result = null;
                Exception exception = null;
                try {
                    result = save(entity);
                } catch (Exception e) {
                    exception = e;
                }
                final Long _result = result;
                final Exception _exception = exception;
                mainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onDone(_result, _exception);
                    }
                });
            }
        });
    }

    public void clear(Class type) throws SQLException{
        ClassInfo classInfo = models.getClassInfo(type);
        String query = "DELETE FROM " + classInfo.tableName;
        exec(query);
    }

    public void clear(Class type, final ExecCallback callback) {
        ClassInfo classInfo = models.getClassInfo(type);
        String query = "DELETE FROM " + classInfo.tableName;
        exec(query, callback);
    }

    public <T> List<T> loadAll(Class<T> type) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        ClassInfo classInfo = models.getClassInfo(type);
        String query = "SELECT * FROM " + classInfo.tableName;
        return queryAll(type, query, null);
    }

    public <T> void loadAll(final Class<T> type, final ResultCallback<List<T>> callback) {
        ClassInfo classInfo = models.getClassInfo(type);
        String query = "SELECT * FROM " + classInfo.tableName;
        queryAll(type, "", null, callback);
    }

    public <T> List<T> queryAll(Class<T> type, String query, String[] selectionArgs) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        Cursor cursor;
        try {
            cursor = openHelper.getReadableDatabase().rawQuery(query, selectionArgs);
        } catch (SQLException e) {
            Log.e(TAG, "SQL Error:", e);
            throw e;
        }
        List<T> result = null;
        try {
            result = (List<T>) models.instantiateObjectsFromCursor(type, cursor);
        } finally {
            cursor.close();
        }
        return result;
    }

    public <T> void queryAll(final Class<T> type, final String query, final String[] selectionArgs, final ResultCallback<List<T>> callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                List<T> result = null;
                Exception exception = null;
                try {
                    result = queryAll(type, query, selectionArgs);
                } catch (Exception e) {
                    exception = e;
                }
                final List<T> _result = result;
                final Exception _exception = exception;
                mainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onDone(_result, _exception);
                    }
                });
            }
        });
    }

    public int execUpdateDelete(String sql, Object values) throws SQLException {
        SQLiteStatement statement = null;
        try {
            statement = openHelper.getWritableDatabase().compileStatement(models.formatSQL(sql, values));
        } catch (SQLException e) {
            Log.e(TAG, "SQL Error:", e);
            throw e;
        }
        try {
            return statement.executeUpdateDelete();
        } finally {
            statement.close();
        }
    }

    public void execUpdateDelete(final String sql, final Object values, final AffectedRowsResultCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                int affectedRows = 0;
                SQLException exception = null;
                try {
                    affectedRows = execUpdateDelete(sql, values);
                } catch (SQLException e) {
                    exception = e;
                }
                final int _affectedRows = affectedRows;
                final SQLException _exception = exception;
                mainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onDone(_affectedRows, _exception);
                    }
                });
            }
        });
    }

    public long execInsert(String sql, Object values) throws SQLException {
        SQLiteStatement statement = null;
        try {
            statement = openHelper.getWritableDatabase().compileStatement(models.formatSQL(sql, values));
        } catch (SQLException e) {
            Log.e(TAG, "SQL Error:", e);
            throw e;
        }
        try {
            return statement.executeInsert();
        } finally {
            statement.close();
        }
    }

    public void execInsert(final String sql, final Object values, final InsertResultCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                long lastInsertRowId = 0;
                SQLException exception = null;
                try {
                    lastInsertRowId = execInsert(sql, values);
                } catch (SQLException e) {
                    exception = e;
                }
                final long _lastInsertRowId = lastInsertRowId;
                final SQLException _exception = exception;
                mainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onDone(_lastInsertRowId, _exception);
                    }
                });
            }
        });
    }

    public void exec(String sql) throws SQLException {
        exec(sql, null);
    }

    public void execAsync(final String sql, @NonNull final ExecCallback callback) {
        execAsync(sql, null, callback);
    }

    public void exec(String sql, Object values) throws SQLException {
        if (values == null) {
            openHelper.getWritableDatabase().execSQL(sql);
            return;
        }
        SQLiteStatement statement = null;
        try {
            statement = openHelper.getWritableDatabase().compileStatement(models.formatSQL(sql, values));
        } catch (SQLException e) {
            Log.e(TAG, "SQL Error:", e);
            throw e;
        }
        try {
            statement.execute();
        } finally {
            statement.close();                                                   
        }
    }

    public void execAsync(final String sql, final Object values, @NonNull final ExecCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                SQLException exception = null;
                try {
                    exec(sql, values);
                } catch (SQLException e) {
                    exception = e;
                }
                final SQLException _exception = exception;
                mainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onDone(_exception);
                    }
                });
            }
        });
    }

    private class DatabaseHelper extends SQLiteOpenHelper {

        public DatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
            super(context, name, factory, version, new DatabaseErrorHandler() {
                @Override
                public void onCorruption(SQLiteDatabase db) {
                    if (callback != null) {
                        callback.onCorruption(ASQL.this);
                    }
                }
            });
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            if (callback != null) {
                _cachedDatabase = db;
                callback.onCreate(ASQL.this);
                _cachedDatabase = null;
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (callback != null) {
                _cachedDatabase = db;
                callback.onUpgrade(ASQL.this, oldVersion, newVersion);
                _cachedDatabase = null;
            }
        }

    }
}
