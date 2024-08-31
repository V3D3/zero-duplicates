package vedaant.zeroduplicates;

import android.app.AlertDialog;
import android.content.Context;

public class Common {
    public static void dialog(Context context, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(message);
        builder.setPositiveButton(R.string.okay, (a, b) -> {});
        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
