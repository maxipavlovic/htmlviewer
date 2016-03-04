package com.traineeship.htmlviewer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private GetHtmlTask mTask;
    private final static String PARAM_TEXT = "text";
    private final static String BROADCAST_ACTION = "com.traineeship.htmlviewer.broadcast";
    private final static String BUTTON_STATE = "button_state";
    private final static String EDITOR_TEXT = "editor_text";
    private BroadcastReceiver mReceiver;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Button button = (Button)findViewById(R.id.button_id);
        EditText editText = (EditText) findViewById(R.id.edittext_id);
        if (savedInstanceState != null) {
            button.setEnabled(savedInstanceState.getBoolean(BUTTON_STATE));
            editText.setText(savedInstanceState.getString(EDITOR_TEXT));
        }
        editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    hideKeyBoard(v);
                }
            }
        });
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText editText = (EditText) findViewById(R.id.edittext_id);
                editText.clearFocus();
                String htmlLink = editText.getText().toString();
                TextView textView = (TextView) findViewById(R.id.textview_id);
                textView.setText("");
                if (editText.getText().toString().trim().equalsIgnoreCase("")) {
                    editText.setError("This field cannot be blank");
                } else {
                    textView.setText(R.string.loading_text);
                    editText.setError(null);
                    mTask = new GetHtmlTask();
                    mTask.execute(htmlLink);
                    button.setEnabled(false);
                }
            }
        });
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String text = intent.getStringExtra(PARAM_TEXT);
                TextView textView = (TextView) findViewById(R.id.textview_id);
                textView.setText(text);
                Button button = (Button)findViewById(R.id.button_id);
                button.setEnabled(true);
            }
        };
    }

    @Override
    protected void onStart() {
        IntentFilter intentFilter = new IntentFilter(BROADCAST_ACTION);
        registerReceiver(mReceiver, intentFilter);
        super.onStart();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(mReceiver);
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        EditText editText = (EditText) findViewById(R.id.edittext_id);
        Button button = (Button)findViewById(R.id.button_id);
        outState.putBoolean(BUTTON_STATE, button.isEnabled());
        outState.putString(EDITOR_TEXT, editText.getText().toString());
        super.onSaveInstanceState(outState);
    }

    private void hideKeyBoard(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager)getSystemService(Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private class GetHtmlTask extends AsyncTask<String,Integer,String> {

        private Exception e=null;
        private Context context = getApplicationContext();
        private CharSequence error_text = "Error loading source code";
        private CharSequence ok_text = "Source code loaded";

        private final String LOG_TAG = GetHtmlTask.class.getSimpleName();

        //https://github.com/google/j2objc/blob/master/jre_emul/android/libcore/luni/src/main/java/libcore/net/url/UrlUtils.java
        private boolean isValidSchemeChar(int index, char c) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                return true;
            }
            if (index > 0 && ((c >= '0' && c <= '9') || c == '+' || c == '-' || c == '.')) {
                return true;
            }
            return false;
        }

        private String getSchemePrefix(String spec) {
            int colon = spec.indexOf(':');

            if (colon < 1) {
                return null;
            }

            for (int i = 0; i < colon; i++) {
                char c = spec.charAt(i);
                if (!isValidSchemeChar(i, c)) {
                    return null;
                }
            }

            return spec.substring(0, colon).toLowerCase(Locale.US);
        }

        private String configureProtocol(String spec) {
            spec = spec.trim();
            String protocol = getSchemePrefix(spec);
            if (protocol == null) {
                return "http://" + spec;
            }
            return spec;
        }

        @Override
        protected String doInBackground(String... strUrl) {
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            String htmlStr = null;
            String configuredUrl = configureProtocol(strUrl[0]);

            try {
                URL url = new URL(configuredUrl);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setConnectTimeout(3000);
                urlConnection.setReadTimeout(3000);
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    return null;
                }
                htmlStr = buffer.toString();
            } catch (IOException e) {
                this.e = e;
                Log.e(LOG_TAG, "Error ", e);
            }
            finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        this.e = e;
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }
            return htmlStr;
        }

        protected void onPostExecute(String result) {
            Intent intent = new Intent(BROADCAST_ACTION);
            if (e == null) {
                Toast.makeText(context, ok_text, Toast.LENGTH_SHORT).show();
                intent.putExtra(PARAM_TEXT, result);

            } else {
                Toast.makeText(context, error_text, Toast.LENGTH_SHORT).show();
                intent.putExtra(PARAM_TEXT, "");
            }
            sendBroadcast(intent);
        }

    }
}
