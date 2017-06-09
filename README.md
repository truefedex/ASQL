# ASQL
Simple wrapper for SQLite API on Android.

#### Features
 - CRUD operations
 - Not hiding low level APIs

#### Usage

##### Step 1. Add the JitPack repository to your build file

Add it in your root build.gradle at the end of repositories:
```gradle
allprojects {
  repositories {
    ...
    maven { url 'https://jitpack.io' }
  }
}
```

##### Step 2. Add the dependency

```groovy
dependencies {
	compile 'com.github.truefedex:ASQL:19b6ecefbd'
}
```

##### Initialize (recommended in your application class onCreate):
```java
ASQL.initDefaultInstance("main.db", 1, new ASQL.BaseCallback() {
            @Override
            public void onCreate(ASQL asql, SQLiteDatabase db) {
	            //currently automatic table generation not supported
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
```

##### Design your models:
```java
@DBTable(name = "note", markMode = MarkMode.ALL_EXCEPT_IGNORED)
public class Note {
    int id;
    String title;
    String body;
    long creationTime;
    long modificationTime;
    @DBIgnore
    SomeotherData ignoredField;
}
```
Currently only primitive datatypes supported as fields.

##### ...And use simple api:

```java
db = ASQL.getDefault(this);
List<Note> allNotes = db.loadAll(Note.class);
Note note = new Note();
db.save(note);
note = db.find(Note.class, "title == ?", new String[]{query});
db.delete(note);

//but you still able to do something like...
db.getDB().execSQL("SELECT count(*) FROM note");
//and other low-level stuff
```

