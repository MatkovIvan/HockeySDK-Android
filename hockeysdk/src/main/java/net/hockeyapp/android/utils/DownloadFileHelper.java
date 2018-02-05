package net.hockeyapp.android.utils;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;

import java.util.NoSuchElementException;

public class DownloadFileHelper {
    private static final int CHECK_DELAY = 500;

    private Context mContext;
    private long mDownloadId;
    private Listener mListener;

    private Handler mMainHandler;

    private DownloadFileHelper(final Context context, final Listener listener) {
        mContext = context;
        mListener = listener;

        mMainHandler = new Handler(context.getMainLooper());
    }

    @SuppressLint("StaticFieldLeak")
    private void start(final String url) {
        AsyncTaskUtils.execute(new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... voids) {
                DownloadManager downloadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                mDownloadId = downloadManager.enqueue(request);
                mListener.onStarted(mDownloadId);
                delayedCheckDownload();
                return null;
            }
        });
    }


    private void delayedCheckDownload() {
        mMainHandler.postDelayed(new Runnable() {

            @SuppressLint("StaticFieldLeak")
            @Override
            public void run() {
                AsyncTaskUtils.execute(new AsyncTask<Void, Void, Void>() {

                    @Override
                    protected Void doInBackground(Void... voids) {
                        boolean completed;
                        try {
                            completed = checkDownload();
                        } catch (final RuntimeException e) {
                            mMainHandler.post(new Runnable() {

                                @Override
                                public void run() {
                                    mListener.onFail(e.getMessage());
                                }
                            });
                            return null;
                        }
                        if (!completed) {
                            delayedCheckDownload();
                        }
                        return null;
                    }
                });
            }
        }, CHECK_DELAY);
    }

    private boolean checkDownload() {
        DownloadManager downloadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
        Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterById(mDownloadId));
        if (cursor == null) {
            throw new NoSuchElementException();
        }
        try {
            if (!cursor.moveToFirst()) {
                throw new NoSuchElementException();
            }
            final int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status == DownloadManager.STATUS_FAILED) {
                final int reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON));
                mMainHandler.post(new Runnable() {

                    @Override
                    public void run() {
                        mListener.onFail("Failed with reason: " + reason);
                    }
                });
                return true;
            }
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                final long totalSize = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                final long currentSize = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                mMainHandler.post(new Runnable() {

                    @Override
                    public void run() {
                        mListener.onProgress(currentSize, totalSize);
                    }
                });
                return false;
            }
            final String localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
            mMainHandler.post(new Runnable() {

                @Override
                public void run() {
                    mListener.onSuccess(Uri.parse(localUri));
                }
            });
            return true;
        } finally {
            cursor.close();
        }
    }

    /**
     *
     * @param context
     * @param url
     * @param listener
     */
    public static void download(final Context context, final String url, final Listener listener) {
        DownloadFileHelper downloadFileHelper = new DownloadFileHelper(context, listener);
        downloadFileHelper.start(url);
    }

    /**
     *
     * @param context
     * @param downloadId
     */
    public static void remove(final Context context, final long downloadId) {
        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        downloadManager.remove(downloadId);
    }

    /**
     *
     */
    public interface Listener {
        void onStarted(long downloadId);
        void onProgress(long current, long total);
        void onSuccess(Uri localUri);
        void onFail(String error);
    }
}
