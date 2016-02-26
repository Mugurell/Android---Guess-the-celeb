package site.petrumugurel.guessthecelebrity;

import android.app.NotificationManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private int mDownloadNotificationID   = 0x0101;
    private int mSaveImagesNotificationID = 0x0111;

    private NotificationManager        mNotificationManager;
    private NotificationCompat.Builder mNotifBuilder;
    private Button                     mStartDWBTN;
    private String                     mHtmlContent;

    private ArrayList<Bitmap> mBitmaps = new ArrayList<>();

    private ArrayList<String> mCelebURLs  = new ArrayList<>();
    private ArrayList<String> mCelebNames = new ArrayList<>();

//    private HashMap<String, Bitmap> mCelebs = new HashMap<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.mainToolbar);
        setSupportActionBar(toolbar);


//        readCelebImagesFromDisk();
        refreshCelebData();

    }


    /**
     * Of of the most important methods for this app.
     * <br>With subsequent calls to
     * {@link site.petrumugurel.guessthecelebrity.MainActivity.DownloadHTMLTask},
     * {@link #extractCelebData(String)} and {@link #saveCelebsToDisk()}
     * it will try to read the entire html content of a site, extract from it celebrities names
     * and their photos and save them to a folder inside apps file dir.
     */
    private void refreshCelebData() {
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotifBuilder = new NotificationCompat.Builder(getApplicationContext());

        mNotifBuilder.setContentTitle("Guess The Celebrity")
                     .setContentText("Downloading celebrity data in progress")
                     .setSmallIcon(R.drawable.ic_file_download_white_24dp);

        new DownloadHTMLTask().execute("http://www.posh24.com/celebrities");
    }


    /**
     * Helper function to be called after a refresh of celeb data.
     * <br>Will use an AsyncTask to download the image thumbnails for every celebrity
     * and saves that image on app's files dir with the celebs name as filename.
     */
    private void saveCelebsToDisk() {
        if (mCelebNames.size() == mCelebURLs.size()) {
            new DownloadSaveCelebImagesTask().execute();
        }
    }


    /**
     * Will try to read the entire html content of a site, then in the
     * {@link site.petrumugurel.guessthecelebrity.MainActivity.DownloadHTMLTask
     * onPostExecute(String)} method will use two helper methods &mdash;
     * {@link #extractCelebData(String)} and {@link #saveCelebsToDisk()} to extract the name and
     * photo of each celebrity and save them to a folder inside apps file dir.
     */
    public class DownloadHTMLTask extends AsyncTask<String, Void, String> {

        // Download the whole html text from the site.
        @Override
        protected String doInBackground(String... urls) {
            String result = "";
            URL    url;

            HttpURLConnection urlConnection;

            try {
                url = new URL(urls[0]);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.connect();

                InputStream inputStream = urlConnection.getInputStream();
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);

                int data = inputStreamReader.read();
                while (data != -1) {
                    result += (char) data;
                    data = inputStreamReader.read();
                }

                urlConnection.disconnect();
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
            mNotifBuilder.setContentText("Celebrity data downloaded")
                         .setProgress(0, 0, false)
                         .setOngoing(false);
            mNotificationManager.notify(mDownloadNotificationID, mNotifBuilder.build());


            extractCelebData(result);


            saveCelebsToDisk();


//            // Write the whole hashmap to disk.
//            // Not possible this way because Bitmap isn't serializable
//            String filename = "celebs.dat";
//            File celebData = new File(getApplicationContext().getFilesDir(), filename);
//            try {
//                FileOutputStream f = new FileOutputStream(celebData);
//                ObjectOutputStream s = new ObjectOutputStream(f);
//                s.writeObject(mCelebs);
//                s.close();
//            } catch (FileNotFoundException e) {
//                e.printStackTrace();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }

            readCelebImagesFromDisk();

            super.onPostExecute(result);
        }


    }
    /**
     * This will be used to download images from the internet and save them with the filename
     * matching the celebrity's name into app's files dir on the Android device.
     * <br>For it to work global &nbsp;{@code ArrayList<String> mCelebURLs}&nbsp; and
     * &nbsp;{@code ArrayList<String> mCelebNames} &nbsp; already containing appropriate data
     * are needed.
     */
    private class DownloadSaveCelebImagesTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            int i = 0;
            for (String imageURL : mCelebURLs) {
                try {
                    URL url = new URL(imageURL);
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.connect();

                    InputStream inputStream = urlConnection.getInputStream();
                    Bitmap celebImage = BitmapFactory.decodeStream(inputStream);

                    String imageName = mCelebNames.get(i++);
//                    mCelebs.put(imageName, celebImage);

                    try {
                        File celebFolder = new File(getApplicationContext().getFilesDir(),
                                                    "Celeb Images");
                        // Refresh data
                        if (celebFolder.exists()) {
                            celebFolder.delete();
                        }
                        celebFolder.mkdirs();

                        File celeb = new File(celebFolder, imageName + ".jpeg");

                        FileOutputStream out = new FileOutputStream(celeb);
                        celebImage.compress(Bitmap.CompressFormat.JPEG, 100, out);
                        out.close();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return null;
        }

        @Override
        protected void onPreExecute() {
            mNotifBuilder.setProgress(100, 0, true)
                         .setContentText("Getting celebrity images")
                         .setOngoing(true);
            mNotificationManager.notify(mSaveImagesNotificationID, mNotifBuilder.build());

            super.onPreExecute();
        }
        @Override
        protected void onPostExecute(Void result) {
            mNotifBuilder.setProgress(0, 0, false)
                         .setOngoing(false)
                         .setContentText("Celebrity images are prepared");
            mNotificationManager.notify(mSaveImagesNotificationID, mNotifBuilder.build());

            readCelebImagesFromDisk();

            super.onPostExecute(result);
        }
    }


    /**
     * Helper function to extract the relevant celebrity data from the html read.
     * Will extract celebrity's name which will be added in &nbsp;{@code mCelebNames} &nbsp;
     * and the URL for that celeb thumbnail which will be added to &nbsp;{@code mCelebURLs}.
     *
     * @param result HTML code from which to extract relevant data.
     */
    private void extractCelebData(String result) {
        // Need to first do a split because on the bottom of the page there are other
        // unneeded images. We only want the ones above.
        String[] readHTMLData  = result.split("<div class=\"sidebarContainer\">");
        String   htmlCelebData = readHTMLData[0];

        // Get all celeb images
        Pattern imagesPattern = Pattern.compile("<img src=\"(.+?)\"");
        Matcher imagesMatcher = imagesPattern.matcher(htmlCelebData.trim());
        while (imagesMatcher.find()) {
            mCelebURLs.add(imagesMatcher.group(1));
//                System.out.println(imagesMatcher.group(1));
        }

        // Get all celeb names. We are relying on the appropriate alt tag fot previous images.
        Pattern namesPattern = Pattern.compile("alt=\"(.+?)\"");
        Matcher namesMatcher = namesPattern.matcher(htmlCelebData);
        while (namesMatcher.find()) {
            mCelebNames.add(namesMatcher.group(1));
//                System.out.println(namesMatcher.group(1));
        }
    }


    /**
     * Will check if previous celebrities photos already exists in apps file dir and try to save all
     * existing images into an {@code ArrayList<Bitmap>} to be worked with further in the app.
     */
    private void readCelebImagesFromDisk() {
        File     path       = new File(getApplicationContext().getFilesDir() + "/Celeb Images");
        String[] imageFiles = null;
        if (path.exists()) {
            imageFiles = path.list();
        }
        else {
            Toast.makeText(MainActivity.this, "Invalid folder path", Toast.LENGTH_LONG).show();
        }
        if (imageFiles != null) {
            for (String image : imageFiles) {
                mBitmaps.add(BitmapFactory.decodeFile(path.getPath() + "/" + image));
            }
        }
//          For debugging
//        if (mBitmaps != null) {
//            System.out.println(mBitmaps.toString());
//        }

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
    public void onDestroy() {
        mNotificationManager.cancel(mDownloadNotificationID);
        mNotificationManager.cancel(mSaveImagesNotificationID);
        super.onDestroy();
    }

}
