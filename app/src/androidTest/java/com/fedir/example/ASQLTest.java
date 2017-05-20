package com.fedir.example;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import com.phlox.asql.ASQL;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by PDT on 20.05.2017.
 */
@RunWith(AndroidJUnit4.class)
public class ASQLTest {
    public static final String TEST_DB = "test.db";
    private static ASQL asql;

    public static class Entity {
        public int id;
        public String title;

        public Entity() {
        }

        public Entity(String title) {
            this.title = title;
        }
    }

    @BeforeClass
    public static void init() {
        Context appContext = InstrumentationRegistry.getTargetContext();
        appContext.deleteDatabase(TEST_DB);
        ASQL.initDefaultInstance(TEST_DB, 1, new ASQL.BaseCallback() {
            @Override
            public void onCreate(ASQL asql) {
                SQLiteDatabase db = asql.getDB();
                db.execSQL("CREATE TABLE entity ("
                        + "ID INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                        + "TITLE TEXT"
                        + ");");
            }
        });
        asql = ASQL.getDefault(appContext);
    }

    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {
        asql.exec("DELETE FROM entity");
    }

    @Test
    public void getDB() throws Exception {
        assertNotNull(asql.getDB());
    }

    @Test
    public void exec() throws Exception {
        asql.exec("DELETE FROM entity");
    }

    @Test
    public void save() throws Exception {
        ASQLTest.Entity e = new ASQLTest.Entity("test");
        long id = asql.save(e);
        assertTrue(id > 0);
        assertEquals(id, e.id);
        ASQLTest.Entity e2 = asql.find(ASQLTest.Entity.class, "id = ?", new String[]{Long.toString(id)});
        assertNotNull(e2);
        assertEquals(e.title, e2.title);
    }
}