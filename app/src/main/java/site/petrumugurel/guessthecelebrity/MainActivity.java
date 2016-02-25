package site.petrumugurel.guessthecelebrity;

import android.app.NotificationManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private int mDownloadNotificationID = 0x0101;

    private NotificationManager        mNotificationManager;
    private NotificationCompat.Builder mNotifBuilder;
    private Button                     mStartDWBTN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.mainToolbar);
        setSupportActionBar(toolbar);

        downloadAndNotify();
    }

    public class DownloadTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... urls) {
            String result = "";
            URL url;
            HttpURLConnection urlConnection;

            try {
                url = new URL(urls[0]);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.connect();

                InputStream inputStream = urlConnection.getInputStream();
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);

                int data = inputStreamReader.read();
                while (data != -1) {
                    result += (char)data;
                    data = inputStreamReader.read();
                }

                return result;

            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPreExecute() {
            mNotifBuilder.setProgress(100, 0, true)
                         .setOngoing(true);
            mNotificationManager.notify(mDownloadNotificationID, mNotifBuilder.build());
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(String result) {
            mNotifBuilder.setContentText("Download finished")
                         .setProgress(100, 100, false)
                         .setOngoing(false);
            mNotificationManager.notify(mDownloadNotificationID, mNotifBuilder.build());


            // Save the read html content to a local file on the device. For debugging purposes.
//            String filename = "myfile";
//            FileOutputStream outputStream;
//            try {
//                outputStream = openFileOutput(filename, Context.MODE_WORLD_READABLE);
//                outputStream.write(result.getBytes());
//                outputStream.close();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//            Toast.makeText(MainActivity.this, "Am salvat in " + getFilesDir(),
//                           Toast.LENGTH_SHORT).show();
//
//

            super.onPostExecute(result);
        }
    }

    private void downloadAndNotify() {
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotifBuilder = new NotificationCompat.Builder(getApplicationContext());

        mNotifBuilder.setContentTitle("Guess The Celebrity")
                     .setContentText("Download in progress")
                     .setSmallIcon(R.drawable.ic_file_download_white_24dp);

        new DownloadTask().execute("http://www.posh24.com/celebrities");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        return true;    // we've built the menu and want it displayed
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        int id = menuItem.getItemId();

        if (id == R.id.mainM_I_settyings) {
            Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content),
                                              "No available settyings atm",
                                              Snackbar.LENGTH_LONG);
            View snackbarView = snackbar.setActionTextColor(Color.WHITE).getView();
            snackbarView.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
            snackbarView.animate().rotationX(-360).setDuration(700);
            snackbar.setAction("Ok", new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                }
            }).show();
        }

        return true;    // we've handled the press, don't want other listeners check for it
    }

    @Override
    public void onDestroy () {
        mNotificationManager.cancel(mDownloadNotificationID);
        super.onDestroy();
    }

}
