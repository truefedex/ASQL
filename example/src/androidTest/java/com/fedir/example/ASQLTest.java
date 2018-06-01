package com.fedir.example;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import com.phlox.asql.ASQL;
import com.phlox.asql.annotations.DBTable;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.After;
import org.junit.Before;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Created by PDT on 20.05.2017.
 */
@RunWith(AndroidJUnit4.class)
public class ASQLTest {
    public static final String TEST_DB = "test.db";
    static ASQL asql;

    public static class Entity {
        int id;
        private String title;

        public Entity() {
        }

        public Entity(String title) {
            this.title = title;
        }
    }

    @DBTable(name = "table_without_primary_key")
    public static class IdLessEntity {
        String field1;
        String field2;
        public IdLessEntity() {
        }
        public IdLessEntity(String field1, String field2) {
            this.field1 = field1;
            this.field2 = field2;
        }
    }

    @BeforeClass
    public static void init() {
        Context appContext = InstrumentationRegistry.getTargetContext();
        appContext.deleteDatabase(TEST_DB);
        ASQL.initDefaultInstance(TEST_DB, 1, new ASQL.BaseCallback() {
            @Override
            public void onCreate(ASQL asql, SQLiteDatabase db) {
                db.execSQL("CREATE TABLE entity ("
                        + "ID INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                        + "TITLE TEXT"
                        + ");");
                db.execSQL("CREATE TABLE table_without_primary_key ("
                        + "field1 TEXT,"
                        + "field2 TEXT"
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
        asql.clear(Entity.class);
        asql.clear(IdLessEntity.class);
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
        Entity e = new Entity("test");
        long id = asql.save(e);
        assertTrue(id > 0);
        assertEquals(id, e.id);
        Entity e2 = asql.find(Entity.class, "id = ?", Long.toString(id));
        assertNotNull(e2);
        assertEquals(e.title, e2.title);
    }

    @Test
    public void loadAll() throws Exception {
        asql.save(new Entity("test"));
        asql.save(new Entity("test2"));
        List<Entity> result = asql.loadAll(Entity.class);
        assertNotNull(result);
        assertEquals(result.size(), 2);
    }

    @Test
    public void clear() throws Exception {
        asql.save(new Entity("test"));
        asql.save(new Entity("test2"));
        asql.clear(Entity.class);
        assertEquals(asql.count(Entity.class), 0);
    }

    @Test
    public void delete() throws Exception {
        asql.clear(Entity.class);
        List<Entity> items = new ArrayList<>();
        items.add(new Entity("test"));
        items.add(new Entity("test2"));
        asql.save(items.get(0));
        asql.save(items.get(1));
        Entity e3 = new Entity("test3");
        asql.save(e3);
        assertEquals(asql.count(Entity.class), 3);
        assertEquals(asql.delete(e3), 1);
        assertEquals(asql.count(Entity.class), 2);
        Entity shouldBeNull = asql.find(Entity.class, "id = ?", Integer.toString(e3.id));
        assertNull(shouldBeNull);
        assertEquals(asql.delete(items), 2);
        assertEquals(asql.count(Entity.class), 0);
    }

    @Test
    public void saveAndLoadIdLessEntity() throws Exception {
        asql.save(new IdLessEntity("test", "test2"));
        asql.save(new IdLessEntity("test3", "test4"));
        assertEquals(asql.count(IdLessEntity.class), 2);
        List<IdLessEntity> entities = asql.loadAll(IdLessEntity.class);
        assertTrue(!entities.get(0).field2.equals(entities.get(1).field2));
        asql.clear(IdLessEntity.class);
        assertEquals(asql.count(IdLessEntity.class), 0);
    }
}