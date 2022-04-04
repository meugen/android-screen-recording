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

    public void storeToGallery(Context context, String path, String mimeType) {
        executor.execute(
            () -> storeToGalleryAsync(context.getApplicationContext(), path, mimeType)
        );
    }

    private void storeToGalleryAsync(Context context, String path, String mimeType) {
        String fileName = new File(path).getName();

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.TITLE, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        Uri storeUri;
        if (mimeType.startsWith("audio/")) {
            storeUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        } else {
            storeUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        }
        Uri uri = context.getContentResolver().insert(storeUri, values);

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
