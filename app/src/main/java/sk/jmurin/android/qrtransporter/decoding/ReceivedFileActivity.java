package sk.jmurin.android.qrtransporter.decoding;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.File;
import java.io.Serializable;

import sk.jmurin.android.qrtransporter.R;

public class ReceivedFileActivity extends Activity {

    private static final String TAG = "ReceivedFileActivity";
    public static final String STATS = "stats";
    private TextView fileDetailTextView;
    public static final String FILE_BUNDLE_KEY = "file";
    private File subor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_received_file);
        fileDetailTextView = (TextView) findViewById(R.id.fileDetailTextView);

        String text = "";
        if (savedInstanceState != null) {
            Log.i(TAG, "savedInstanceState != null");
            subor = (File) savedInstanceState.get(FILE_BUNDLE_KEY);
            text = (String) savedInstanceState.get(STATS);
        } else {
            Log.i(TAG, "savedInstanceState == null");
            subor = (File) getIntent().getSerializableExtra(FILE_BUNDLE_KEY);
            text = "subor ulozeny do: " + subor.getAbsolutePath() + "\n velkost: " + subor.length() + "\n" + (String) getIntent().getSerializableExtra(STATS);
        }
        Log.i(TAG, "text: " + text);
        fileDetailTextView.setText(text);
    }

    public void openFileButtonClicked(View view) {
        Log.i(TAG, "openFileButtonClicked");
        Intent myIntent = new Intent(android.content.Intent.ACTION_VIEW);
        File file = new File(subor.getAbsolutePath());
        String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(file).toString());
        String mimetype = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        myIntent.setDataAndType(Uri.fromFile(file), mimetype);
        startActivity(myIntent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.i(TAG, "onSaveInstanceState");
        outState.putSerializable(FILE_BUNDLE_KEY, subor);
        outState.putSerializable(STATS, (Serializable) fileDetailTextView.getText());
        Log.i(TAG, "saving text as serializable: " + fileDetailTextView.getText());
        super.onSaveInstanceState(outState);
    }

    public void closeButtonClicked(View view) {
        Log.i(TAG, "closeButtonClicked");
        finish();
    }
}
