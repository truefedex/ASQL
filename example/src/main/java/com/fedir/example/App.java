package com.fedir.example;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;

import com.phlox.asql.ASQL;

import java.util.concurrent.ExecutorService;

/**
 * Created by PDT on 07.06.2017.
 */

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ASQL.initDefaultInstance("main.db", 1, new ASQL.BaseCallback() {
            @Override
            public void onCreate(ASQL asql, SQLiteDatabase db) {
                db.execSQL("CREATE TABLE note ("
                        + "id INTEGER PRIMARY KEY NOT NULL,"
                        + "title TEXT,"
                        + "body TEXT,"
                        + "creationTime INTEGER,"
                        + "modificationTime INTEGER"
                        + ");");
                db.execSQL("CREATE INDEX note_title_idx ON note(title);");
            }
        });
    }
}
