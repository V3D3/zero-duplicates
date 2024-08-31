package vedaant.zeroduplicates;

import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private DataLoader loader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ActivityResultLauncher<IntentSenderRequest> launcher = registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(),
                o -> Log.d("VDNT", ((o.getResultCode() == RESULT_OK) ? "successful" : "failed") + " intent"));
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        loader = new DataLoader(getApplicationContext());
        loader.load();

        TextView head = findViewById(R.id.image_count_label);
        head.setText(getString(R.string.image_count, loader.imageList.size(), loader.videoList.size()));

        findViewById(R.id.run_image_button).setOnClickListener((v) -> {
            buildTable(loader.imageList);
        });
        findViewById(R.id.run_video_button).setOnClickListener((v) -> {
            buildTable(loader.videoList);
        });

        findViewById(R.id.delete_button).setOnClickListener((v) -> {
            PendingIntent intent = loader.deleteDuplicates();
            try {
                launcher.launch((new IntentSenderRequest.Builder(intent.getIntentSender())).build());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    void buildTable(List<DataLoader.Media> collection) {
        findViewById(R.id.run_image_button).setEnabled(false);
        findViewById(R.id.run_video_button).setEnabled(false);

        TextView progressLabel = findViewById(R.id.progress_label);
        progressLabel.setText(getString(R.string.progress, 0, collection.size(), 0, 0, 0, 0));
        loader.buildTable(collection, (i, n, elapsed, estimated) -> {
            runOnUiThread(() -> {
                if (i > 0) {
                    progressLabel.setText(getString(R.string.progress, i, n, elapsed / 60, elapsed % 60, estimated / 60, estimated % 60));
                } else {
                    progressLabel.setText(R.string.done_progress);
                    showMediaMap(collection);
                }
            });
        });
    }

    void showMediaMap(List<DataLoader.Media> collection) {
        int unique = loader.mediaMap.size();
        TextView deleteLabel = findViewById(R.id.delete_label);
        if (collection.size() - unique != 0) {
            deleteLabel.setText(getString(R.string.stat_delete, unique, ((double) collection.size()) / unique, loader.sizeEstimate));
            findViewById(R.id.delete_button).setEnabled(true);
        } else {
            deleteLabel.setText(R.string.empty_delete);
        }
    }
}