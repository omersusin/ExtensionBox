package com.extensionbox.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class HistoryHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "ebox_history.db";
    private static final int DB_VERSION = 1;
    public static final String TABLE = "stats";

    public static final String COL_TIME = "ts";
    public static final String COL_KEY = "module_key";
    public static final String COL_FIELD = "field";
    public static final String COL_VALUE = "value";

    private static HistoryHelper instance;

    public static synchronized HistoryHelper get(Context ctx) {
        if (instance == null) {
            instance = new HistoryHelper(ctx.getApplicationContext());
        }
        return instance;
    }

    private HistoryHelper(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                COL_TIME + " INTEGER, " +
                COL_KEY + " TEXT, " +
                COL_FIELD + " TEXT, " +
                COL_VALUE + " TEXT)");
        db.execSQL("CREATE INDEX idx_key_time ON " + TABLE + " (" + COL_KEY + ", " + COL_TIME + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int old, int nv) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    public void insert(String moduleKey, String field, String value) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TIME, System.currentTimeMillis());
        cv.put(COL_KEY, moduleKey);
        cv.put(COL_FIELD, field);
        cv.put(COL_VALUE, value);
        db.insert(TABLE, null, cv);

        db.delete(TABLE, COL_TIME + " < ?",
                new String[]{String.valueOf(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L)});
    }

    public List<long[]> query(String moduleKey, String field, int lastHours) {
        List<long[]> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        long since = System.currentTimeMillis() - (long) lastHours * 3600 * 1000;
        Cursor c = db.query(TABLE,
                new String[]{COL_TIME, COL_VALUE},
                COL_KEY + "=? AND " + COL_FIELD + "=? AND " + COL_TIME + ">?",
                new String[]{moduleKey, field, String.valueOf(since)},
                null, null, COL_TIME + " ASC");
        while (c.moveToNext()) {
            long ts = c.getLong(0);
            String val = c.getString(1);

            try {

                String numeric = val.replaceAll("[^0-9.\\-]", "");
                if (!numeric.isEmpty()) {
                    result.add(new long[]{ts, (long) Double.parseDouble(numeric)});
                }
            } catch (NumberFormatException ignored) {}
        }
        c.close();
        return result;
    }
}
