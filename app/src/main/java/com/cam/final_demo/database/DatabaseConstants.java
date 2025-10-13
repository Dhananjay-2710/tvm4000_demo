package com.cam.final_demo.database;

public class DatabaseConstants {
    // User Table
    public static final String TABLE_USERS = "users";
    public static final String COLUMN_USER_ID = "_id";
    public static final String COLUMN_USER_NAME = "name";

    // Topup Columns (optional but clean)
    public static final String COLUMN_CARD_NUMBER = "CardNumber";
    public static final String COLUMN_CARD_TYPE = "CardType";
    public static final String COLUMN_CARD_TAP_TIME = "CardTapTime";
    public static final String COLUMN_TOPUP_AMOUNT = "TopUpAmount";
    public static final String COLUMN_FIFTY_NOTE = "FiftyNoteCount";
    public static final String COLUMN_HUNDRED_NOTE = "HunderedNoteCount";
    public static final String COLUMN_TWO_HUNDRED_NOTE = "TwoHunderedNoteCount";
    public static final String COLUMN_FIVE_HUNDRED_NOTE = "FiveHunderedNoteCount";
    public static final String COLUMN_TOPUP_STATUS = "TopUpStatus";
    public static final String COLUMN_CREATED_AT = "Created_At";
    public static final String COLUMN_UPDATED_AT = "Updated_At";


    public static final String CREATE_TABLE_USERS = "CREATE TABLE " + TABLE_USERS + " (" +
            COLUMN_USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_USER_NAME + " TEXT);";

    // Topup Details Table
    public static final String TABLE_TOPUP_DETAILS = "topup_details";
    public static final String CREATE_TABLE_TOPUP_DETAILS = "CREATE TABLE " + TABLE_TOPUP_DETAILS + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "CardNumber TEXT," +
            "CardType TEXT," +
            "CardTapTime TEXT," +
            "TopUpAmount REAL," +
            "FiftyNoteCount INTEGER," +
            "HunderedNoteCount INTEGER," +
            "TwoHunderedNoteCount INTEGER," +
            "FiveHunderedNoteCount INTEGER," +
            "TopUpStatus TEXT," +
            "Created_At TEXT," +
            "Updated_At TEXT" +
            ");";
}
