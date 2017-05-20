package com.phlox.asql;

import java.lang.reflect.Field;

/**
 * Created by PDT on 22.01.2017.
 */

public class ColumnInfo {
    public String name;
    public Field field;
    public boolean primaryKey = false;

    public ColumnInfo(Field field) {
        this.field = field;
    }

    @Override
    public String toString() {
        return name;
    }
}
