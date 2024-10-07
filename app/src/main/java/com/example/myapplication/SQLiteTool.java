package com.example.myapplication;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLiteTool类用于简化SQLite数据库的操作。
 */
public class SQLiteTool extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "floatYarnDetect_database.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TAG = "SQLiteTool";

    public SQLiteTool(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "数据库已创建");
    }


    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "数据库升级：从版本 " + oldVersion + " 升级到 " + newVersion);
    }

    public void sqlUpdateCameraParameter(String tableName, CameraParameters cameraParameters, int[] arrRoi1, int[] arrRoi2){
        sqlUpdateParameter(tableName, "FDist",cameraParameters.getFocusDistance());
        sqlUpdateParameter(tableName, "Iso", cameraParameters.getIso());
        sqlUpdateParameter(tableName, "ETime", cameraParameters.getExposureTime());
        sqlUpdateParameter(tableName, "ZRat", cameraParameters.getZoomRatio());
        sqlUpdateParameter(tableName, "Roi1",arrayToSting(arrRoi1));
        sqlUpdateParameter(tableName, "Roi2",arrayToSting(arrRoi2));
    }

    public List<YarnDetectData> sqlGetTableData(String tableName) {
        List<YarnDetectData> dataList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            // 检查表是否存在
            cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", new String[]{tableName});
            if (cursor.getCount() == 0) {
                Log.e(TAG, "Table " + tableName + " does not exist.");
                return dataList; // 或者抛出异常
            }
            Log.d(TAG, "Table " + tableName + " exists.");
            cursor.close(); // 关闭检查表存在的Cursor
            // 查询表数据
            cursor = db.rawQuery("SELECT * FROM " + tableName, null);
            if (cursor.moveToFirst()) {
                do {
                    Map<String, String> dataMap = new HashMap<>();
                    for (int i = 0; i < cursor.getColumnCount(); i++) {
                        dataMap.put(cursor.getColumnName(i), cursor.getString(i));
                    }
                    YarnDetectData data = YarnDetectData.fromMap(dataMap);
                    dataList.add(data);
                } while (cursor.moveToNext());
            }
            Log.d(TAG, "DataList Size: " + dataList.size());
//            Log.d(TAG, "DataList 0: " + dataList.get(0).toString());

        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }
        return dataList;
    }




    public void sqlInsertCameraParameter(String tableName, CameraParameters cameraParameters, int[] arrRoi1, int[] arrRoi2){
        List<ContentValues> valuesList = new ArrayList<>();
        valuesList.add(createContentValues("FDist", String.valueOf(cameraParameters.getFocusDistance()),0.0f, 0, 0));
        valuesList.add(createContentValues("Iso", String.valueOf(cameraParameters.getIso()), 0.0f, 0, 0));
        valuesList.add(createContentValues("ETime", String.valueOf(cameraParameters.getExposureTime()),0.0f, 0, 0));
        valuesList.add(createContentValues("ZRat", String.valueOf(cameraParameters.getZoomRatio()),0.0f, 0, 0));

        valuesList.add(createContentValues("Roi1", String.valueOf(arrayToSting(arrRoi1)),0.0f, 0, 0));
        valuesList.add(createContentValues("Roi2", String.valueOf(arrayToSting(arrRoi2)),0.0f, 0, 0));
        // 批量插入数据
        batchInsertData(tableName, valuesList);
        Log.d(TAG, "数据库插入相机参数");
    }

    public String arrayToSting(int[] arr){
        StringBuilder backString = new StringBuilder();
        for (int element : arr) {
            backString.append(String.valueOf(element)).append(" ");
        }
        return backString.toString().trim();
    }

    public void sqlUpdateParameter(String tableName, String key, Object value) {
        ContentValues values = new ContentValues();
        values.put("VALUE", String.valueOf(value)); // 更新的值

        // 定义 WHERE 子句和参数
        String whereClause = "KEY = ?";
        String[] whereArgs = new String[] { key };
        // 执行更新
        int rowsAffected = updateData(tableName, values, whereClause, whereArgs);
        // 打印更新结果
        Log.d(TAG, "更新 " + key + " 时受影响的行数(数据库中的表): " + rowsAffected);
    }
    public YarnDetectData sqlGetDetectInfo(int yarnRow, String knitTableName){
        YarnDetectData get_yarn_data = fetchYarnDataById(knitTableName, "Row" + String.valueOf(yarnRow));
        return get_yarn_data;
    }

    public YarnDetectData fetchYarnDataById(String tableName, String key) {
        // 定义查询参数
        String[] columns = {"KEY", "VALUE", "VELOCITY", "LUM", "REGION"}; // 要查询的列
        String selection = "KEY = ?"; // WHERE子句
        String[] selectionArgs = {String.valueOf(key)}; // WHERE子句中的参数
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;

        try {
            // 执行查询
            cursor = db.query(tableName, columns, selection, selectionArgs, null, null, null);

            // 处理查询结果
            if (cursor != null && cursor.moveToFirst()) {
                // 获取列索引
                int keyIndex = cursor.getColumnIndex("KEY");
                int valueIndex = cursor.getColumnIndex("VALUE");
                int velocityIndex = cursor.getColumnIndex("VELOCITY");
                int lumIndex = cursor.getColumnIndex("LUM");
                int regionIndex = cursor.getColumnIndex("REGION");

                // 获取列值
                String rowKey = cursor.getString(keyIndex);
                String value = cursor.getString(valueIndex);
                float velocity = cursor.getFloat(velocityIndex);
                int lum = cursor.getInt(lumIndex);
                int region = cursor.getInt(regionIndex);

                YarnDetectData yarnDetectData = new YarnDetectData(rowKey, value, velocity, lum, region);

                // 处理数据（例如，打印到日志）
                Log.d(TAG, "Key: " + rowKey + ", Value: " + value + ", Lum: " + lum + ", Region: " + region);
                return yarnDetectData;
            } else {
                Log.d(TAG, "No data found for KEY: " + key);
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "查询数据时出错：" + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close(); // 关闭Cursor
            }
        }
        return null;
    }

//   获取当前所有表名
    public String getTableName() {
        StringBuilder allTableNames = new StringBuilder();
        SQLiteDatabase db = getReadableDatabase(); // 使用当前实例的数据库连接
        Cursor cursor = null;
        try {
            // 查询 sqlite_master 表以获取所有表名
            String query = "SELECT name FROM sqlite_master WHERE type='table'";
            cursor = db.rawQuery(query, null);
            while (cursor.moveToNext()) {
                @SuppressLint("Range") String tableName = cursor.getString(cursor.getColumnIndex("name"));
                if (allTableNames.length() > 0) {
                    allTableNames.append(", ");
                }
                allTableNames.append(tableName);
            }
        } catch (Exception e) {
            Log.e(TAG, "获取表名时出错：" + e.getMessage());
            return "";
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
            // 注意：通常不需要在这里关闭 db，因为它会被 SQLiteOpenHelper 管理
            // 但如果你确实需要在某个时候关闭它，你应该确保它不是由 SQLiteOpenHelper 当前正在使用的实例
        }
        return allTableNames.toString();
    }
    /**
     * 创建一个新表。
     *
     * @param tableName 表名
     */
    public boolean createTable(String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            Log.e(TAG, "Invalid table name");
            return false;
        }

        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();

            String createTableSQL = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "KEY TEXT PRIMARY KEY, " +
                    "VALUE TEXT, " +
                    "VELOCITY REAL, " +
                    "LUM INTEGER, " +
                    "REGION INTEGER" +
                    ");";
            db.execSQL(createTableSQL);
            Log.d(TAG, "createTableSQL:" + createTableSQL);
            return true;
        } catch (SQLException e) {
            Log.e(TAG, "创建表时出错：" + e.getMessage());
            return false;
        } finally {
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
    }

    /**
     * 向表中插入一行数据。
     *
     * @param tableName 表名
     * @param values    数据值，键为列名，值为列值
     * @return 插入的行ID，如果插入失败则返回-1
     */
    public long insertOrUpdateData(String tableName, ContentValues values ) {
        SQLiteDatabase db = null;
        String keyColumnName = "KEY";
        long result = -1;
        try {
            db = this.getWritableDatabase();
            // 检查是否已经存在相同的 Key
            String keyValue = values.getAsString(keyColumnName);
            String selection = keyColumnName + " = ?";
            String[] selectionArgs = { keyValue };

            // 查询表中是否存在该 Key
            Cursor cursor = db.query(tableName, null, selection, selectionArgs, null, null, null);
            boolean recordExists = cursor.moveToFirst();  // true if record exists
            cursor.close();

            if (recordExists) {
                // 如果存在相同的 Key，则执行更新
                result = db.update(tableName, values, selection, selectionArgs);
                Log.d(TAG, "数据更新成功，Key: " + keyValue);
            } else {
                // 如果不存在，则执行插入
                result = db.insert(tableName, null, values);
                Log.d(TAG, "数据插入成功，Key: " + keyValue);
            }
        } catch (SQLException e) {
            Log.e(TAG, "插入或更新数据时出错：" + e.getMessage());
        } finally {
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
        return result;
    }


    public boolean isTableExists(String tableName) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        boolean exists = false;
        try {
            db = this.getReadableDatabase();
            String query = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";
            cursor = db.rawQuery(query, new String[]{tableName});
            exists = (cursor.getCount() > 0);
        } catch (SQLException e) {
            Log.e(TAG, "检查表是否存在时出错：" + e.getMessage());
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
        return exists;
    }

    /**
     * 批量插入数据。
     *
     * @param tableName  表名
     * @param valuesList 数据值列表，每个ContentValues表示一行数据
     */
    public void batchInsertData(String tableName, List<ContentValues> valuesList) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            db.beginTransaction();  // 开始事务
            for (ContentValues values : valuesList) {
                db.insert(tableName, null, values);
            }
            db.setTransactionSuccessful();  // 设置事务成功
            Log.d(TAG, "批量插入数据成功");
        } catch (SQLException e) {
            Log.e(TAG, "批量插入数据时出错：" + e.getMessage());
        } finally {
            if (db != null && db.isOpen()) {
                db.endTransaction();  // 结束事务
                db.close();
            }
        }
    }

    /**
     * 更新表中的数据。
     *
     * @param tableName  表名
     * @param values     要更新的值，键为列名，值为列值
     * @param whereClause 选择条件，例如 "id = ?"
     * @param whereArgs   选择条件中的参数
     * @return 更新的行数
     */
    public int updateData(String tableName, ContentValues values, String whereClause, String[] whereArgs) {
        SQLiteDatabase db = null;
        int rowsAffected = 0;
        try {
            db = this.getWritableDatabase();
            rowsAffected = db.update(tableName, values, whereClause, whereArgs);
        } catch (SQLException e) {
            Log.e(TAG, "更新数据时出错：" + e.getMessage());
        } finally {
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
        return rowsAffected;
    }


    ContentValues createContentValues(String key, String value,float velocity, int lum, int region) {
        ContentValues values = new ContentValues();
        values.put("KEY", key);
        values.put("VALUE", value);
        values.put("VELOCITY", velocity);
        values.put("LUM", lum);
        values.put("REGION", region);
        return values;
    }

    /**
     * 删除表中的数据。
     *
     * @param tableName  表名
     * @param whereClause 选择条件，例如 "id = ?"
     * @param whereArgs   选择条件中的参数
     * @return 删除的行数
     */
    public int deleteData(String tableName, String whereClause, String[] whereArgs) {
        SQLiteDatabase db = null;
        int rowsDeleted = 0;
        try {
            db = this.getWritableDatabase();
            rowsDeleted = db.delete(tableName, whereClause, whereArgs);
        } catch (SQLException e) {
            Log.e(TAG, "删除数据时出错：" + e.getMessage());
        } finally {
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
        return rowsDeleted;
    }

    /**
     * 删除表。
     *
     * @param tableName 表名
     */
    public void dropTable(String tableName) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            String dropTableSQL = "DROP TABLE IF EXISTS " + tableName + ";";
            db.execSQL(dropTableSQL);
        } catch (SQLException e) {
            Log.e(TAG, "删除表时出错：" + e.getMessage());
        } finally {
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
    }

        public void dropAllTables() {
            SQLiteDatabase db = null;
            Cursor cursor = null;
            try {
                db = this.getWritableDatabase();

                // 查询数据库中所有表的名称
                String query = "SELECT name FROM sqlite_master WHERE type='table'";
                cursor = db.rawQuery(query, null);

                if (cursor.moveToFirst()) {
                    do {
                        String tableName = cursor.getString(0);
                        if (!tableName.equals("android_metadata") && !tableName.equals("sqlite_sequence")) {
                            String dropTableSQL = "DROP TABLE IF EXISTS " + tableName + ";";
                            db.execSQL(dropTableSQL);
                        }
                    } while (cursor.moveToNext());
                }
            } catch (SQLException e) {
                Log.e(TAG, "清空所有表时出错：" + e.getMessage());
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
                if (db != null && db.isOpen()) {
                    db.close();
                }
            }
        }


    /**
     * 查询表中的数据。
     *
     * @param tableName     表名
     * @param columns       要查询的列，传null表示查询所有列
     * @param selection     WHERE子句，例如 "id = ?"
     * @param selectionArgs WHERE子句中的参数
     * @param groupBy       GROUP BY子句
     * @param having        HAVING子句
     * @param orderBy       ORDER BY子句
     * @return Cursor对象，包含查询结果
     */
    public Cursor queryData(String tableName, String[] columns, String selection,
                            String[] selectionArgs, String groupBy, String having, String orderBy) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = this.getReadableDatabase();
            cursor = db.query(tableName, columns, selection, selectionArgs, groupBy, having, orderBy);
        } catch (SQLException e) {
            Log.e(TAG, "查询数据时出错：" + e.getMessage());
        }
        return cursor;
    }

    /**
     * 向表中添加新列。
     *
     * @param tableName  表名
     * @param columnName 列名
     * @param columnType 列类型，例如 "TEXT"
     */
    public void addColumn(String tableName, String columnName, String columnType) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            String alterTableSQL = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType + ";";
            db.execSQL(alterTableSQL);
        } catch (SQLException e) {
            Log.e(TAG, "添加列时出错：" + e.getMessage());
        } finally {
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
    }

    /**
     * 执行原始SQL查询。
     *
     * @param sql 原始SQL语句
     */
    public void executeRawQuery(String sql) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            db.execSQL(sql);
        } catch (SQLException e) {
            Log.e(TAG, "执行原始SQL查询时出错：" + e.getMessage());
        } finally {
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
    }

    /**
     * 开始一个事务。
     */
    public void beginTransaction() {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            db.beginTransaction();
        } catch (SQLException e) {
            Log.e(TAG, "开始事务时出错：" + e.getMessage());
        }
    }


    /**
     * 设置事务成功。
     */
    public void setTransactionSuccessful() {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            db.setTransactionSuccessful();
        } catch (SQLException e) {
            Log.e(TAG, "设置事务成功时出错：" + e.getMessage());
        }
    }

    /**
     * 结束事务。
     */
    public void endTransaction() {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            db.endTransaction();
        } catch (SQLException e) {
            Log.e(TAG, "结束事务时出错：" + e.getMessage());
        }
    }

    /**
     * 备份数据库到指定路径。
     *
     * @param backupPath 备份文件路径
     */
    public void backupDatabase(String backupPath) {
        SQLiteDatabase db = null;
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            db = this.getReadableDatabase();
            File dbFile = new File(db.getPath());
            File backupFile = new File(backupPath);
            fis = new FileInputStream(dbFile);
            fos = new FileOutputStream(backupFile);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
            Log.d(TAG, "数据库备份完成");
        } catch (IOException e) {
            Log.e(TAG, "备份数据库时出错：" + e.getMessage());
        } finally {
            try {
                if (fis != null) fis.close();
                if (fos != null) fos.close();
            } catch (IOException e) {
                Log.e(TAG, "关闭流时出错：" + e.getMessage());
            }
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
    }

    /**
     * 从指定路径恢复数据库。
     *
     * @param backupPath 备份文件路径
     */
    public void restoreDatabase(String backupPath) {
        SQLiteDatabase db = null;
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            db = this.getWritableDatabase();
            File dbFile = new File(db.getPath());
            File backupFile = new File(backupPath);
            fos = new FileOutputStream(dbFile);
            fis = new FileInputStream(backupFile);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
            Log.d(TAG, "数据库恢复完成");
        } catch (IOException e) {
            Log.e(TAG, "恢复数据库时出错：" + e.getMessage());
        } finally {
            try {
                if (fis != null) fis.close();
                if (fos != null) fos.close();
            } catch (IOException e) {
                Log.e(TAG, "关闭流时出错：" + e.getMessage());
            }
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
    }

    /**
     * 导出数据库模式（表结构）为SQL脚本。
     *
     * @return 数据库模式的SQL脚本
     */
    public String exportSchema() {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        StringBuilder schema = new StringBuilder();
        try {
            db = this.getReadableDatabase();
            cursor = db.rawQuery("SELECT sql FROM sqlite_master WHERE type='table'", null);
            while (cursor.moveToNext()) {
                schema.append(cursor.getString(0)).append(";\n\n");
            }
            Log.d(TAG, "数据库模式导出完成");
        } catch (SQLException e) {
            Log.e(TAG, "导出数据库模式时出错：" + e.getMessage());
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
        return schema.toString();
    }

    /**
     * 从SQL脚本导入数据库模式（表结构）。
     *
     * @param schemaSql SQL脚本，包含CREATE TABLE语句
     */
    public void importSchema(String schemaSql) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            String[] statements = schemaSql.split(";\n\n");
            for (String statement : statements) {
                if (!statement.trim().isEmpty()) {
                    db.execSQL(statement);
                }
            }
            Log.d(TAG, "数据库模式导入完成");
        } catch (SQLException e) {
            Log.e(TAG, "导入数据库模式时出错：" + e.getMessage());
        } finally {
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
    }

    public List<String> getAllTables() {
        List<String> tableList = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = this.getReadableDatabase();
            String query = "SELECT name FROM sqlite_master WHERE type='table'";
            cursor = db.rawQuery(query, null);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String tableName = cursor.getString(0); // 获取表名
                    tableList.add(tableName);
                } while (cursor.moveToNext());
            }
        } catch (SQLException e) {
            Log.e(TAG, "获取所有表时出错：" + e.getMessage());
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
        return tableList;
    }

    /**
     * 计算指定表中的行数。
     *
     * @param tableName 表名
     * @return 表中的行数
     */
    public int countRows(String tableName) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        int count = 0;
        try {
            db = this.getReadableDatabase();
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + tableName, null);
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            Log.d(TAG, "计算行数完成");
        } catch (SQLException e) {
            Log.e(TAG, "计算行数时出错：" + e.getMessage());
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
        return count;
    }
}
