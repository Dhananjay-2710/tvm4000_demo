package com.cam.final_demo.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class MyDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "mydata.db";
    private static final int DATABASE_VERSION = 1;


    public MyDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(DatabaseConstants.CREATE_TABLE_USERS);
        db.execSQL(DatabaseConstants.CREATE_TABLE_TOPUP_DETAILS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + DatabaseConstants.TABLE_USERS);
        db.execSQL("DROP TABLE IF EXISTS " + DatabaseConstants.TABLE_TOPUP_DETAILS);
        onCreate(db);
    }

    public void addUser(String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.COLUMN_USER_NAME, name);
        db.insert(DatabaseConstants.TABLE_USERS, null, values);
        db.close();
    }

    public List<String> getAllUsers() {
        List<String> users = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + DatabaseConstants.TABLE_USERS, null);

        if (cursor.moveToFirst()) {
            do {
                String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseConstants.COLUMN_USER_NAME));
                users.add(name);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return users;
    }

    public long insertTopUpDetails(String cardNumber, String cardType, String cardTapTime,
                                   double topUpAmount, int fifty, int hundred, int twoHundred, int fiveHundred,
                                   String status, String createdAt, String updatedAt) {

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(DatabaseConstants.COLUMN_CARD_NUMBER, cardNumber);
        values.put(DatabaseConstants.COLUMN_CARD_TYPE, cardType);
        values.put(DatabaseConstants.COLUMN_CARD_TAP_TIME, cardTapTime);
        values.put(DatabaseConstants.COLUMN_TOPUP_AMOUNT, topUpAmount);
        values.put(DatabaseConstants.COLUMN_FIFTY_NOTE, fifty);
        values.put(DatabaseConstants.COLUMN_HUNDRED_NOTE, hundred);
        values.put(DatabaseConstants.COLUMN_TWO_HUNDRED_NOTE, twoHundred);
        values.put(DatabaseConstants.COLUMN_FIVE_HUNDRED_NOTE, fiveHundred);
        values.put(DatabaseConstants.COLUMN_TOPUP_STATUS, status);
        values.put(DatabaseConstants.COLUMN_CREATED_AT, createdAt);
        values.put(DatabaseConstants.COLUMN_UPDATED_AT, updatedAt);

        long result = db.insert(DatabaseConstants.TABLE_TOPUP_DETAILS, null, values);
        db.close();
        return result;
    }

    public int updateTopUpDetails(ContentValues values, String whereClause, String[] whereArgs) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsAffected = db.update(
                DatabaseConstants.TABLE_TOPUP_DETAILS,
                values,
                whereClause,
                whereArgs
        );
        db.close();
        return rowsAffected;
    }
}
