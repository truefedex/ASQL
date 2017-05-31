package com.phlox.asql;

import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteStatement;

import com.phlox.asql.annotations.DBColumn;
import com.phlox.asql.annotations.DBIgnore;
import com.phlox.asql.annotations.DBTable;
import com.phlox.asql.annotations.MarkMode;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by PDT on 12.09.2016.
 */
public class ModelsInfoProcessor extends Object{
    public static final String NULL_SQL_VALUE_AS_STRING = "NULL";
    private Map<String, ClassInfo> classInfoCache = new TreeMap<>();

    public ClassInfo getClassInfo(Class type) {
        return parseClassFields(type);
    }

    public List instantiateObjectsFromCursor(Class type, Cursor cursor) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        ArrayList results = new ArrayList();
        ClassInfo classInfo = parseClassFields(type);
        String[] columns = cursor.getColumnNames();
        for (int i = 0; i < columns.length; i++) {
            columns[i] = columns[i].toLowerCase();
        }

        while (cursor.moveToNext()) {
            Object newInstance = instantiateObjectFromCursor(type, cursor, classInfo, columns);
            results.add(newInstance);
        }
        return results;
    }

    public Object instantiateObjectFromCursor(Class type, Cursor cursor) throws InstantiationException, IllegalAccessException {
        ClassInfo classInfo = parseClassFields(type);
        String[] columns = cursor.getColumnNames();
        for (int i = 0; i < columns.length; i++) {
            columns[i] = columns[i].toLowerCase();
        }
        return instantiateObjectFromCursor(type, cursor, classInfo, columns);
    }

    private Object instantiateObjectFromCursor(Class type, Cursor cursor, ClassInfo classInfo, String[] columns) throws InstantiationException, IllegalAccessException {
        Object newInstance = type.newInstance();
        for (int i = 0; i < columns.length; i++) {
            if (cursor.isNull(i)) continue;
            ColumnInfo column = classInfo.fields.get(columns[i]);
            if (column == null) continue;
            Field field = column.field;
            if (field == null) continue;
            if (!field.isAccessible()) {
                field.setAccessible(true);
            }
            if (field.getType().equals(String.class))
                field.set(newInstance, cursor.getString(i));
            else if (field.getType().equals(int.class))
                field.setInt(newInstance, cursor.getInt(i));
            else if (field.getType().equals(short.class))
                field.setShort(newInstance, cursor.getShort(i));
            else if (field.getType().equals(byte.class))
                field.setByte(newInstance, (byte) cursor.getShort(i));
            else if (field.getType().equals(char.class))
                field.setChar(newInstance, (char) cursor.getInt(i));
            else if (field.getType().equals(long.class))
                field.setLong(newInstance, cursor.getLong(i));
            else if (field.getType().equals(float.class))
                field.setFloat(newInstance, cursor.getFloat(i));
            else if (field.getType().equals(double.class))
                field.setDouble(newInstance, cursor.getDouble(i));
            else if (field.getType().equals(boolean.class))
                field.setBoolean(newInstance, cursor.getInt(i) != 0);
        }
        return newInstance;
    }

    private ClassInfo parseClassFields(Class type) {
        ClassInfo classInfo = classInfoCache.get(type.getName());
        if (classInfo == null) {
            classInfo = new ClassInfo();
            MarkMode mode = MarkMode.ALL_EXCEPT_IGNORED;
            DBTable tableAnnotation = (DBTable) type.getAnnotation(DBTable.class);
            if (tableAnnotation != null) {
                mode = tableAnnotation.markMode();
                classInfo.tableName = tableAnnotation.name();
            }
            if ("".equals(classInfo.tableName)) {
                classInfo.tableName = camelCaseToDBCase(type.getSimpleName());
            }

            Field[] fields = type.getDeclaredFields();
            for (Field field : fields) {
                int mods = field.getModifiers();
                if (field.isSynthetic() || Modifier.isStatic(mods) ||
                        Modifier.isFinal(mods) ||
                        "serialVersionUID".equals(field.getName()) ||
                        field.isAnnotationPresent(DBIgnore.class)) continue;
                ColumnInfo columnInfo = new ColumnInfo(field);
                String name;
                if (field.isAnnotationPresent(DBColumn.class)) {
                    DBColumn annotation = field.getAnnotation(DBColumn.class);
                    name = "".equals(annotation.name()) ? camelCaseToDBCase(field.getName()) : annotation.name();
                    if (annotation.primaryKey()) {
                        columnInfo.primaryKey = true;
                        classInfo.primaryKey = columnInfo;
                    }
                } else {
                    if (MarkMode.ONLY_COLUMN_MARKED.equals(mode)) continue;
                    name = camelCaseToDBCase(field.getName());
                }
                columnInfo.name = name;
                classInfo.fields.put(name, columnInfo);
            }
            classInfoCache.put(type.getName(), classInfo);
        }
        if (classInfo.primaryKey == null) {
            ColumnInfo ci = classInfo.fields.get("id");
            if (ci != null) {
                ci.primaryKey = true;
                classInfo.primaryKey = ci;
            }
        }
        return classInfo;
    }

    private String camelCaseToDBCase(String name) {
        StringBuffer sb = new StringBuffer(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i != 0) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private Pattern sqlValuePattern = Pattern.compile(":([a-zA-Z_$][a-zA-Z_$0-9]*)");
    public String formatSQL(String sql, Object values) {
        ClassInfo classInfo = parseClassFields(values.getClass());
        Matcher matcher = sqlValuePattern.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String foundName = matcher.group(1).toLowerCase();
            ColumnInfo column = classInfo.fields.get(foundName);
            if (column == null) continue;
            Field field = column.field;
            if (field == null) continue;
            String sqlValue = getFieldValueAsString(field, values);
            matcher.appendReplacement(sb, sqlValue);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static String getFieldValueAsString(Field field, Object obj) {
        try {
            if (!field.isAccessible()) {
                field.setAccessible(true);
            }
            if (field.getType().equals(String.class)) {
                Object val = field.get(obj);
                if (val == null) {
                    return NULL_SQL_VALUE_AS_STRING;
                }
                return DatabaseUtils.sqlEscapeString((String) val);
            } else if (field.getType().equals(long.class) || field.getType().equals(int.class) ||
                    field.getType().equals(short.class) || field.getType().equals(byte.class))
                return Long.toString(field.getLong(obj));
            else if (field.getType().equals(char.class))
                return Integer.toString(field.getChar(obj));
            else if (field.getType().equals(double.class) || field.getType().equals(float.class))
                return Double.toString(field.getDouble(obj));
            else if (field.getType().equals(boolean.class))
                return field.getBoolean(obj) ? "1" : "0";
        } catch (Exception e) {
        }
        return "";
    }

    public static void bindFieldValueToPreparedStatement(Field field, Object obj, int index, SQLiteStatement statement) {
        try {
            if (!field.isAccessible()) {
                field.setAccessible(true);
            }
            if (field.getType().equals(String.class)) {
                Object val = field.get(obj);
                if (val == null) {
                    statement.bindNull(index);
                } else {
                    statement.bindString(index, (String) val);
                }
                return;
            } else if (field.getType().equals(long.class) || field.getType().equals(int.class) || field.getType().equals(short.class) || field.getType().equals(byte.class)) {
                statement.bindLong(index, field.getLong(obj));
                return;
            } else if (field.getType().equals(char.class)) {
                statement.bindLong(index, field.getChar(obj));
                return;
            } else if (field.getType().equals(double.class) || field.getType().equals(float.class)) {
                statement.bindDouble(index, field.getDouble(obj));
                return;
            } else if (field.getType().equals(boolean.class))
                statement.bindLong(index, field.getBoolean(obj) ? 1 : 0);
                return;
        } catch (Exception e) {
        }
        statement.bindNull(index);
        return;
    }

    public String getValuesCommaSeparated(Object entity) {
        ClassInfo classInfo = getClassInfo(entity.getClass());
        StringBuffer sb = new StringBuffer();
        if (!classInfo.fields.isEmpty()) {
            for (Map.Entry<String, ColumnInfo> e : classInfo.fields.entrySet()) {
                sb.append(getFieldValueAsString(e.getValue().field, entity));
                sb.append(',');
            }
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }
}
