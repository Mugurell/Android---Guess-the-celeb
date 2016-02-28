package site.petrumugurel.guessthecelebrity;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.service.notification.StatusBarNotification;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
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
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dmax.dialog.SpotsDialog;

public class MainActivity extends AppCompatActivity {

    private final int DOWNLOAD_NOTIF_ID    = 0x0101;
    private final int SAVE_IMAGES_NOTIF_ID = 0x0111;

    private final String CELEB_IMAGES_FOLDER = "Celeb Images";

    private final Handler mHandler = new Handler();
    private final Random  mRandom  = new Random();

    private NotificationManager        mNotificationManager;
    private NotificationCompat.Builder mNotifBuilder;
    private AlertDialog                mSpotsDialog;

    private Button mStartDWBTN;
    private String mHtmlContent;

    private ArrayList<Bitmap> mBitmaps    = new ArrayList<>();
    private ArrayList<String> mCelebURLs  = new ArrayList<>();
    private ArrayList<String> mCelebNames = new ArrayList<>();

    private Bitmap    currentCeleb;
    private ImageView mCelebIV;
    private String[] mCelebsOnDisk = null;

//    private HashMap<String, Bitmap> mCelebs = new HashMap<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.mainToolbar);
        setSupportActionBar(toolbar);

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotifBuilder = new NotificationCompat.Builder(getApplicationContext());
        mNotifBuilder.setContentTitle(getString(R.string.app_name));

        mSpotsDialog = new SpotsDialog(this, R.style.Custom_Spots_Dialog);
        mSpotsDialog.setCancelable(false);
        mSpotsDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    Toast.makeText(MainActivity.this,
                                   "Just a little more..", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return true;
            }
        });


        Button option1BTN = (Button) findViewById(R.id.mainA_RL_BTN_0);
        mCelebIV = (ImageView) findViewById(R.id.mainA_RL_IV_celebImage);

        if (!readCelebImagesFromDisk()) {
            AlertDialog.Builder builder =
                    new AlertDialog.Builder(this)
                            .setTitle("Missing celebrity data")
                            .setMessage("Do you want to download required data?")
                            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    getWindow().addFlags(
                                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                                    refreshCelebData();
                                }
                            })
                            .setNegativeButton("No", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    ToastNoImages(4000);
                                }
                            });
            AlertDialog alertDialog = builder.show();
        }

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
            mNotifBuilder.setContentText("Downloading celebrity data ...")
                         .setSmallIcon(R.drawable.ic_file_download_white_24dp)
                         .setProgress(100, 0, true)
                         .setOngoing(true);
            mNotificationManager.notify(DOWNLOAD_NOTIF_ID, mNotifBuilder.build());

            mSpotsDialog.show();
            mSpotsDialog.setMessage("Downloading celebrity data ...");
        }
        @Override
        protected void onPostExecute(String result) {
            mNotifBuilder.setContentText("Celebrity data downloaded")
                         .setProgress(0, 0, false)
                         .setOngoing(false);
            mNotificationManager.notify(DOWNLOAD_NOTIF_ID, mNotifBuilder.build());
            if (mSpotsDialog.isShowing()) {
                mSpotsDialog.dismiss();
            }

            cancelNotifications(5000l, DOWNLOAD_NOTIF_ID);

            extractCelebData(result);

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
                                                    CELEB_IMAGES_FOLDER);
                        // Refresh data
                        if (celebFolder.exists()) {
                            celebFolder.delete();
                        }
                        celebFolder.mkdirs();

                        File celeb = new File(celebFolder, imageName);

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
                         .setContentText("Getting celebrity images ...")
                         .setSmallIcon(R.drawable.ic_file_download_white_24dp)
                         .setOngoing(true);
            mNotificationManager.notify(SAVE_IMAGES_NOTIF_ID, mNotifBuilder.build());
            mSpotsDialog.show();
            mSpotsDialog.setMessage("Getting celebrity images ...");
        }
        @Override
        protected void onPostExecute(Void result) {
            mNotifBuilder.setProgress(0, 0, false)
                         .setOngoing(false)
                         .setContentText("Celebrity images are prepared");
            mNotificationManager.notify(SAVE_IMAGES_NOTIF_ID, mNotifBuilder.build());
            if (mSpotsDialog.isShowing()) {
                mSpotsDialog.dismiss();
            }

            cancelNotifications(5000l, SAVE_IMAGES_NOTIF_ID);

            if (!readCelebImagesFromDisk()) {
                ToastNoImages(4000);
            }
        }


    }

    private void ToastNoImages(long millisDelay) {
        Toast.makeText(MainActivity.this, "Cannot read images\nApp will be closing.",
                       Toast.LENGTH_LONG).show();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, millisDelay);
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

        saveCelebsToDisk();

    }


    /**
     * Will check if previous celebrities photos already exists in apps file dir and try to save all
     * existing images filenames into &nbsp;{@link #mCelebsOnDisk}&nbsp; to be worked with further
     * in the app.
     * @return if workable with images found on disk or not.
     */
    private boolean readCelebImagesFromDisk() {
        mSpotsDialog.show();
        mSpotsDialog.setMessage("Reading?");

        boolean ifFoundImages = false;

        final File path = new File(getApplicationContext().getFilesDir()
                                   + "/" + CELEB_IMAGES_FOLDER);
        if (path.exists()) {
            mCelebsOnDisk = path.list();
            if (mCelebsOnDisk.length == 100) {  // I happen to know exactly how many should be
                ifFoundImages = true;
                Log.i(getApplication().getPackageName(), "Found 100 celeb images");
            }
        }
        else {
            Log.i(getApplication().getPackageName(), "No images workable with found on disk");
        }

//        // For debugging
//        if (mCelebsOnDisk != null) {
//            for (String image : mCelebsOnDisk) {
//                System.out.println(image);
//            }
//        }

        if (mSpotsDialog.isShowing()) {
            mSpotsDialog.dismiss();
        }

        return ifFoundImages;
    }


    private void initCelebIVAndButtons() {
        File path = new File(getApplicationContext().getFilesDir() + "/" + CELEB_IMAGES_FOLDER);
        mCelebIV.setImageBitmap(BitmapFactory.decodeFile(path.getPath() + "/"
                                                         + mCelebsOnDisk[1]));
    }


    private void selectRandomCeleb() {
        // todo    De ce sa nu salvez doar un string - pathul pozei in metoda de mai sus, ramanand sa incarc poza cautata la runtime., nu sa am o lista intreaga de poze - size mare
        int chosenCeleb = mRandom.nextInt(mBitmaps.size());
        currentCeleb = mBitmaps.get(chosenCeleb);
        String currCelebName;
    }


    /**
     * Helper function to cancel all requested notifications if active.
     * @param delayMillis milliseconds after which to dismiss the notification(s) if active.
     * @param notificationIDs notifications we want to dismiss.
     */
    private void cancelNotifications(long delayMillis, Integer... notificationIDs) {
        final ArrayList<Integer> appsActiveNotif = new ArrayList<>();

        if (mNotificationManager != null) {
            StatusBarNotification[] notifications = mNotificationManager.getActiveNotifications();
            for (StatusBarNotification activeNotif : notifications) {
                for (int notifID : notificationIDs) {
                    if (activeNotif.getId() == notifID) {
                        if (delayMillis == 0) {
                            mNotificationManager.cancel(notifID);
                        }
                        else {
                            appsActiveNotif.add(notifID);
                        }
                    }
                }
            }
        }

        if (!appsActiveNotif.isEmpty()) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    for (int activeNotif : appsActiveNotif) {
                        mNotificationManager.cancel(activeNotif);
                    }
                }
            }, delayMillis);
        }
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
        cancelNotifications(0l, DOWNLOAD_NOTIF_ID, SAVE_IMAGES_NOTIF_ID);

        super.onDestroy();
    }

}
