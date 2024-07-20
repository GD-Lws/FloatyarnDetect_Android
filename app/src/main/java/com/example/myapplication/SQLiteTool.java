package com.example.myapplication;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SQLiteTool extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "floatYarnDetect_database.db";
    private static final int DATABASE_VERSION = 1;


    public SQLiteTool(@Nullable Context context) {
        super(context, DATABASE_NAME, null,DATABASE_VERSION);
    }


    @Override
    public void onCreate(SQLiteDatabase db) {

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    public void createTable(String tableName, String columns) {
        SQLiteDatabase db = this.getWritableDatabase();
        String createTableSQL = "CREATE TABLE IF NOT EXISTS " + tableName + " (" + columns + ");";
        try {
            db.execSQL(createTableSQL);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public long insertData(String tableName, ContentValues values) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.insert(tableName, null, values);
    }

    /**
     * 查询数据
     *
     * @param tableName     表名
     * @param columns       要查询的列
     * @param selection     WHERE子句
     * @param selectionArgs WHERE子句的参数
     * @param groupBy       GROUP BY子句
     * @param having        HAVING子句
     * @param orderBy       ORDER BY子句
     * @return Cursor对象，包含查询结果
     */
    public Cursor queryData(String tableName, String[] columns, String selection,
                            String[] selectionArgs, String groupBy, String having, String orderBy) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(tableName, columns, selection, selectionArgs, groupBy, having, orderBy);
    }

    /**
     * 添加新列
     *
     * @param tableName  表名
     * @param columnName 列名
     * @param columnType 列类型（例如："TEXT"）
     */
    public void addColumn(String tableName, String columnName, String columnType) {
        SQLiteDatabase db = this.getWritableDatabase();
        String alterTableSQL = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType + ";";
        try {
            db.execSQL(alterTableSQL);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
