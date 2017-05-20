package com.phlox.asql;

import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by fedex on 16.01.17.
 */

public class ClassInfo {
    public Map<String, ColumnInfo> fields = new TreeMap<>();
    public String tableName = "";
    public ColumnInfo primaryKey;
    
    private SQLiteStatement saveStatement;
    private SQLiteStatement insertStatement;

    public SQLiteStatement fillSavePreparedStatement(ASQL asql, Object entity) {
        if (saveStatement == null) {
            Collection<ColumnInfo> fieldValues = fields.values();
            String columnsCommaSeparated = TextUtils.join(",", fieldValues);
            StringBuffer sb = new StringBuffer(fieldValues.size()*2);
            for (int i = 0; i < fieldValues.size(); i++) {
                sb.append('?');
                if (i != fieldValues.size() - 1) {
                    sb.append(',');
                }
            }
            String valuesPlaceholdersCommaSeparated = sb.toString();
            
            String query = String.format("REPLACE INTO %s (%s) VALUES (%s)",
                    tableName, columnsCommaSeparated, valuesPlaceholdersCommaSeparated);
            saveStatement = asql.getDB().compileStatement(query);
        }
        int i = 1;
        saveStatement.clearBindings();
        for (Map.Entry<String, ColumnInfo> e: fields.entrySet()) {
            ModelsInfoProcessor.bindFieldValueToPreparedStatement(e.getValue().field, entity, i, saveStatement);
            i++;
        }
        return saveStatement;
    }

    public SQLiteStatement fillInsertAutoincrementPreparedStatement(ASQL asql, Object entity) {
        List<ColumnInfo> fieldValues = new ArrayList<>(fields.values());
        //insert used there only for insert rows with autoincrement/autogenerate keys, so
        //we should remove that column from insert clause
        for (int i = 0; i < fieldValues.size(); i++) {
            if (fieldValues.get(i) == primaryKey) {
                fieldValues.remove(i);
                break;
            }
        }
        if (insertStatement == null) {
            String columnsCommaSeparated = TextUtils.join(",", fieldValues);
            StringBuffer sb = new StringBuffer(fieldValues.size() *2);
            for (int i = 0; i < fieldValues.size(); i++) {
                sb.append('?');
                if (i != fieldValues.size() - 1) {
                    sb.append(',');
                }
            }
            String valuesPlaceholdersCommaSeparated = sb.toString();

            String query = String.format("INSERT INTO %s (%s) VALUES (%s)",
                    tableName, columnsCommaSeparated, valuesPlaceholdersCommaSeparated);
            insertStatement = asql.getDB().compileStatement(query);
        }
        int i = 1;
        insertStatement.clearBindings();
        for (ColumnInfo ci: fieldValues) {
            ModelsInfoProcessor.bindFieldValueToPreparedStatement(ci.field, entity, i, insertStatement);
            i++;
        }
        return insertStatement;
    }

    @Override
    protected void finalize() throws Throwable {
        if (saveStatement != null) {
            saveStatement.close();
            saveStatement = null;
        }
        if (insertStatement != null) {
            insertStatement.close();
            insertStatement = null;
        }
        super.finalize();
    }
}
