package com.mohammadkk.mymusicplayer.providers;

import android.database.AbstractCursor;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;

public class SortedCursor extends AbstractCursor {
    private final Cursor mCursor;
    private ArrayList<Integer> mOrderedPositions;
    protected final ArrayList<String> mMissingValues;
    protected HashMap<String, Integer> mMapCursorPositions;

    public SortedCursor(@NonNull final Cursor cursor, @Nullable final String[] order, final String columnName) {
        mCursor = cursor;
        mMissingValues = buildCursorPositionMapping(order, columnName);
    }

    @NonNull
    private ArrayList<String> buildCursorPositionMapping(@Nullable final String[] order, final String columnName) {
        ArrayList<String> missingValues = new ArrayList<>();

        mOrderedPositions = new ArrayList<>(mCursor.getCount());

        mMapCursorPositions = new HashMap<>(mCursor.getCount());
        final int valueColumnIndex = mCursor.getColumnIndex(columnName);

        if (mCursor.moveToFirst()) {
            do {
                mMapCursorPositions.put(mCursor.getString(valueColumnIndex), mCursor.getPosition());
            } while (mCursor.moveToNext());

            if (order != null) {
                for (final String value : order) {
                    if (mMapCursorPositions.containsKey(value)) {
                        mOrderedPositions.add(mMapCursorPositions.get(value));
                        mMapCursorPositions.remove(value);
                    } else {
                        missingValues.add(value);
                    }
                }
            }
            mCursor.moveToFirst();
        }

        return missingValues;
    }


    @Override
    public void close() {
        mCursor.close();
        super.close();
    }

    @Override
    public int getCount() {
        return mOrderedPositions.size();
    }

    @Override
    public String[] getColumnNames() {
        return mCursor.getColumnNames();
    }

    @Override
    public String getString(int column) {
        return mCursor.getString(column);
    }

    @Override
    public short getShort(int column) {
        return mCursor.getShort(column);
    }

    @Override
    public int getInt(int column) {
        return mCursor.getInt(column);
    }

    @Override
    public long getLong(int column) {
        return mCursor.getLong(column);
    }

    @Override
    public float getFloat(int column) {
        return mCursor.getFloat(column);
    }

    @Override
    public double getDouble(int column) {
        return mCursor.getDouble(column);
    }

    @Override
    public boolean isNull(int column) {
        return mCursor.isNull(column);
    }

    @Override
    public boolean onMove(int oldPosition, int newPosition) {
        if (newPosition >= 0 && newPosition < getCount()) {
            mCursor.moveToPosition(mOrderedPositions.get(newPosition));
            return true;
        }

        return false;
    }
}

