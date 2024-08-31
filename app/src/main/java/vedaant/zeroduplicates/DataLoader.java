package vedaant.zeroduplicates;

import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DataLoader {
    Context context;
    public class Media {
        private final Uri uri;
        private final String name;
        private final int size;
        private final long time;
        private String hash;

        public Media(Uri uri, String name, int size, long time) {
            this.uri = uri;
            this.name = name;
            this.size = size;
            this.time = time;
        }
    }

    List<Media> videoList = new ArrayList<>();
    List<Media> imageList = new ArrayList<>();

    public DataLoader(Context context) {
        this.context = context;
    }

    public void load() {
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Log.d("VDNT", collection.toString());

        String[] projection = new String[] {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_TAKEN
        };
        String sortOrder = MediaStore.Images.Media.DATE_TAKEN + " ASC";

        ContentResolver resolver = context.getContentResolver();
        try (Cursor cursor = resolver.query(collection, projection, null, null, sortOrder)) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID);
                int nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                int sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE);
                int timeCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN);
                if (idCol == -1 || nameCol == -1 || sizeCol == -1 || timeCol == -1) {
                    throw new RuntimeException("Missing columns");
                }

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    String name = cursor.getString(nameCol);
                    int size = cursor.getInt(sizeCol);
                    long time = cursor.getLong(timeCol);

                    Uri contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                    imageList.add(new Media(contentUri, name, size, time));
                }
            }
        } catch(Exception e) {
            Common.dialog(context, "Error loading images.");
            Log.e("VDNT", e.toString());
            e.printStackTrace();
        }

        collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        projection = new String[] {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_TAKEN
        };
        sortOrder = MediaStore.Video.Media.DATE_TAKEN + " ASC";

        try (Cursor cursor = resolver.query(collection, projection, null, null, sortOrder)) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID);
                int nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME);
                int sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE);
                int timeCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN);
                if (idCol == -1 || nameCol == -1 || sizeCol == -1 || timeCol == -1) {
                    throw new RuntimeException("Missing columns");
                }

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    String name = cursor.getString(nameCol);
                    int size = cursor.getInt(sizeCol);
                    long time = cursor.getLong(timeCol);

                    Uri contentUri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                    videoList.add(new Media(contentUri, name, size, time));
                }
            }
        } catch (Exception e) {
            Common.dialog(context, "Error loading videos.");
            Log.e("VDNT", e.toString());
            e.printStackTrace();
        }
    }

    Map<String, List<Media>> mediaMap = new HashMap<>();
    public String sizeEstimate = "unknown";

    public interface ProgressCallback {
        void call(int i, int total, long elapsed, long estimated);
    }

    public void buildTable(List<Media> collection, ProgressCallback callback) {
        mediaMap.clear();

        Thread handle = new Thread(() -> {
            long startTime = System.currentTimeMillis();
            long previousTime = System.currentTimeMillis();

            MessageDigest m;
            try {
                m = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("MD5 not available");
            }

            final int BUFSIZE = 512 * 1024;
            int n = collection.size();
            ContentResolver resolver = context.getContentResolver();
            for (int i = 0; i < n; i++) {
                m.reset();
                Media obj = collection.get(i);
                byte[] chunk = new byte[BUFSIZE];

                try (InputStream stream = resolver.openInputStream(obj.uri)) {
                    if (stream != null) {
                        boolean run = true;
                        while (run) {
                            if (stream.read(chunk) != BUFSIZE) {
                                run = false;
                            }
                            m.update(chunk);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(String.format("Error reading file %s at %s", obj.name, obj.uri));
                }
                obj.hash = String.format("%032X", new BigInteger(1, m.digest()));
                mediaMap.putIfAbsent(obj.hash, new ArrayList<>());
                mediaMap.get(obj.hash).add(obj);
                m.reset();

                if (System.currentTimeMillis() - previousTime > 1000L) {
                    previousTime = System.currentTimeMillis();

                    double elapsed = (previousTime - startTime) / 1000.0;
                    double estimated = (n - i) * (elapsed / (i + 1));
                    callback.call(i, n, Math.round(elapsed), Math.round(estimated));
                }
            }
            calculateSizeEstimate();
            callback.call(-1, 0, 0, 0);
        });
        handle.start();
    }

    private void calculateSizeEstimate() {
        long size = 0;

        for (List<Media> l : mediaMap.values()) {
            for (Media m : l) {
                size += m.size;
            }
            size -= l.get(0).size;
        }

        int qualifier = 0;
        double div = 0.;
        while (size > 1024) {
            div = size / 1024.;
            size /= 1024;
            qualifier++;
        }

        final String qualifiers = " KMGT";
        sizeEstimate = context.getString(R.string.size, div, qualifiers.charAt(qualifier));
    }

    public PendingIntent deleteDuplicates() {
        ContentResolver resolver = context.getContentResolver();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            List<Uri> preferredDuplicates = new ArrayList<>();

            for (List<Media> mediaList : mediaMap.values()) {
                mediaList.sort(Comparator.comparingLong(a -> a.time));
                for (int i = 1; i < mediaList.size(); i++) {
                    preferredDuplicates.add(mediaList.get(i).uri);
                }
            }

            return MediaStore.createDeleteRequest(resolver, preferredDuplicates);
        }
        return null;
    }
}
