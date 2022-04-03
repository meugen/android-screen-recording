package meugeninua.screenrecording.utils;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class StoreToGalleryUtil {

    public static final StoreToGalleryUtil INSTANCE = new StoreToGalleryUtil();
    private final Executor executor = Executors.newSingleThreadExecutor();

    public void storeToGallery(Context context, String path) {
        executor.execute(
            () -> storeToGalleryAsync(context.getApplicationContext(), path)
        );
    }

    private void storeToGalleryAsync(Context context, String path) {
        String fileName = new File(path).getName();

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.TITLE, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        Uri uri = context.getContentResolver().insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
        );

        try (
            OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
            InputStream inputStream = new FileInputStream(path)
        ) {
            byte[] buf = new byte[1024];
            while (true) {
                int count = inputStream.read(buf);
                if (count < 0) {
                    break;
                }
                outputStream.write(buf, 0, count);
            }
        } catch (IOException e) {
            Log.d(getClass().getSimpleName(), e.getMessage(), e);
        }
    }
}
