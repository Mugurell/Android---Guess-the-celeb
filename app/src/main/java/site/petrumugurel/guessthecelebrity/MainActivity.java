package site.petrumugurel.guessthecelebrity;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
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

    private static final String TAG = MainActivity.class.getSimpleName();

    private final String CELEB_IMAGES_FOLDER = "Celeb Images";

    private NotificationManager        mNotificationManager;
    private NotificationCompat.Builder mNotifBuilder;
    private AlertDialog                mSpotsDialog;

    private final Handler mHandler = new Handler();
    private final Random  mRandom  = new Random();

    /** Array of filenames for each celeb image on disk. The app will work based on this. */
    private String[]          mCelebsOnDisk = null;

    private Bitmap    currentCeleb;
    private ImageView mCelebIV;


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
                                   "A little patience I beg of you..", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return true;
            }
        });

        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy
                    = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }


        Button option1BTN = (Button) findViewById(R.id.mainA_RL_BTN_0);
        mCelebIV = (ImageView) findViewById(R.id.mainA_RL_IV_celebImage);

        if (!loadCelebImagesFromDisk()) {
            showMissingDataDialog();
        }
    }


    /**
     * Shows a dialog informing the user that missing data is required for the app to work and
     * let's him choose if to try and acquire said data from internet or leave the app.
     */
    private void showMissingDataDialog() {
        Toast.makeText(MainActivity.this, "Sunt in dialog", Toast.LENGTH_SHORT).show();
        final AlertDialog.Builder builder =
                new AlertDialog.Builder(this)
                        .setTitle("Missing celebrity data")
                        .setMessage("Do you want to download required data?")
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
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


    /**
     * This will try to download the html content of the site containing celeb info, extract
     * celeb image and name, and save that data on device's internal storage.
     */
    private void refreshCelebData() {
        String siteHTML = getCelebHTML("http://www.posh24.com/celebrities");
        ArrayList<String> celebURLs = extractCelebData(siteHTML, "<img src=\"(.+?)\"");
        ArrayList<String> celebNames = extractCelebData(siteHTML, "alt=\"(.+?)\"");

        if (celebURLs.size() > 0 && celebURLs.size() == celebNames.size()) {
            saveCelebImagesOnDisk(celebURLs, celebNames);
        }
    }


    /**
     * Will try to read the entire html content of a site which should contain some celebrity
     * info &mdash; name and thumbnail.
     *
     * @param siteAddress valid address of a site which contains info about celebrities.
     * @return String containing all html code of the site.
     */
    public String getCelebHTML(String siteAddress) {
        mNotifBuilder.setContentText("Downloading celebrity data ...")
                     .setSmallIcon(R.drawable.ic_file_download_white_24dp)
                     .setProgress(100, 0, true)
                     .setOngoing(true);
        mNotificationManager.notify(DOWNLOAD_NOTIF_ID, mNotifBuilder.build());

        mSpotsDialog.show();
        mSpotsDialog.setMessage("Downloading celebrity data ...");

        String result = "";     // Will hold entire html site source.
        URL    url;
        HttpURLConnection urlConnection;

        try {
            url = new URL(siteAddress);
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

        } catch (Exception e) {
            e.printStackTrace();
        }

        // Need to first do a split because on the bottom of the page there are other
        // unneeded images. We only want the ones above.
        String[] readHTMLData  = result.split("<div class=\"sidebarContainer\">");
        String   htmlCelebData = readHTMLData[0];


        mNotifBuilder.setContentText("Celebrity data downloaded")
                     .setProgress(0, 0, false)
                     .setOngoing(false);
        mNotificationManager.notify(DOWNLOAD_NOTIF_ID, mNotifBuilder.build());
        if (mSpotsDialog.isShowing()) {
            mSpotsDialog.dismiss();
        }

        cancelNotifications(5000l, DOWNLOAD_NOTIF_ID);

        return htmlCelebData;
    }


    /**
     * Helper function to extract the relevant celebrity data from the html read.
     * Will extract celebrity's name which will be added in &nbsp;{@code mCelebNames} &nbsp;
     * and the URL for that celeb thumbnail which will be added to &nbsp;{@code mCelebURLs}.
     *
     * @param sourceText HTML code from which to extract relevant data.
     * @param regex to be used for extracting the needed part of {@code sourceText}.
     * @return array of elements which comply with {@code regex} extracted from {@code sourceText}.
     */
    private ArrayList<String> extractCelebData(String sourceText, String regex) {
        ArrayList<String> result = new ArrayList<>();

        Pattern imagesPattern = Pattern.compile(regex);
        Matcher imagesMatcher = imagesPattern.matcher(sourceText);
        while (imagesMatcher.find()) {
            result.add(imagesMatcher.group(1));
//                System.out.println(imagesMatcher.group(1));
        }

        return result;
    }


    /**
     * Will download celebrity's images from the internet and save them with the filename
     * matching the celebrity's name into app's files dir on the Android device.
     *
     * @param celebURLs from which to download images
     * @param celebNames names for the downloaded images.
     */
    private void saveCelebImagesOnDisk(ArrayList<String> celebURLs, ArrayList<String> celebNames) {
        mNotifBuilder.setProgress(100, 0, true)
                     .setContentText("Getting celebrity images ...")
                     .setSmallIcon(R.drawable.ic_file_download_white_24dp)
                     .setOngoing(true);
        mNotificationManager.notify(SAVE_IMAGES_NOTIF_ID, mNotifBuilder.build());
        mSpotsDialog.show();
        mSpotsDialog.setMessage("Getting celebrity images ...");

        // Create a new folder in which to store the new images.
        File celebFolder = new File(getApplicationContext().getFilesDir(), CELEB_IMAGES_FOLDER);
        if (celebFolder.exists()) {
            celebFolder.delete();
        }
        celebFolder.mkdirs();


        int i = 0;
        for (String imageURL : celebURLs) {
            try {
                URL url = new URL(imageURL);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.connect();

                InputStream inputStream = urlConnection.getInputStream();
                Bitmap celebImage = BitmapFactory.decodeStream(inputStream);

                String imageName = celebNames.get(i++);

                File celeb = new File(celebFolder, imageName);

                FileOutputStream fout = new FileOutputStream(celeb);
                celebImage.compress(Bitmap.CompressFormat.JPEG, 100, fout);
                fout.close();


            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mNotifBuilder.setProgress(0, 0, false)
                         .setOngoing(false)
                         .setContentText("Celebrity images are prepared");
            mNotificationManager.notify(SAVE_IMAGES_NOTIF_ID, mNotifBuilder.build());
            if (mSpotsDialog.isShowing()) {
                mSpotsDialog.dismiss();
            }

            cancelNotifications(5000l, SAVE_IMAGES_NOTIF_ID);
        }
    }


    /**
     * Will check if previous celebrities photos already exists in apps file dir and try to save all
     * existing images filenames into &nbsp;{@link #mCelebsOnDisk}&nbsp; to be worked with further
     * in the app.
     * @return if &nbsp;{@link #mCelebsOnDisk}&nbsp; was successfully initialized with valid data.
     */
    private boolean loadCelebImagesFromDisk() {
        boolean ifImagesLoaded = false;

        final File path = new File(getApplicationContext().getFilesDir()
                                   + "/" + CELEB_IMAGES_FOLDER);
        if (path.exists()) {
            mCelebsOnDisk = path.list();
            if (mCelebsOnDisk.length == 100) {  // I happen to know exactly how many should be
                ifImagesLoaded = true;
                Log.i(TAG, "Found 100 celeb images");
            }
            Log.i(TAG, "Invalid number of celebrities on disk");
        }
        else {
            Log.i(TAG, "Invalid path for celeb images");
        }

        return ifImagesLoaded;
    }


//


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
