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
import android.support.annotation.NonNull;
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
import java.util.HashSet;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dmax.dialog.SpotsDialog;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private final int DOWNLOAD_NOTIF_ID    = 0x0101;
    private final int SAVE_IMAGES_NOTIF_ID = 0x0111;

    private NotificationManager        mNotificationManager;
    private NotificationCompat.Builder mNotifBuilder;
    private AlertDialog                mSpotsDialog;

    private static final String TAG                 = MainActivity.class.getSimpleName();
    private final        String CELEB_IMAGES_FOLDER = "Celeb Images";

    private final        Handler mHandler = new Handler();
    private static final Random  mRandom  = new Random();

    /** Array of filenames for each celeb image on disk. The app will work based on this. */
    private String[] mCelebsOnDisk = null;
    private String mDisplayedCeleb;

    private int     mNoOfCelebsGuessed;
    private int     mNoOfCelebsShowed;
    private boolean mIfShowCorrectAnswer;

    private Button    mOption1BTN;
    private Button    mOption2BTN;
    private Button    mOption3BTN;
    private Button    mOption4BTN;
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
                                   "Just a little more..", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return true;
            }
        });

        mOption1BTN = (Button) findViewById(R.id.mainA_RL_BTN_1);
        mOption2BTN = (Button) findViewById(R.id.mainA_RL_BTN_2);
        mOption3BTN = (Button) findViewById(R.id.mainA_RL_BTN_3);
        mOption4BTN = (Button) findViewById(R.id.mainA_RL_BTN_4);
        mOption1BTN.setOnClickListener(this);
        mOption2BTN.setOnClickListener(this);
        mOption3BTN.setOnClickListener(this);
        mOption4BTN.setOnClickListener(this);
        mCelebIV = (ImageView) findViewById(R.id.mainA_RL_IV_celebImage);

        mNoOfCelebsShowed = mNoOfCelebsGuessed = 0;
        mIfShowCorrectAnswer = false;

        ensureAvailableCelebImages();
    }



    /**
     * This is to be called before trying any operation with celeb data.
     * Will check if celeb data is already present on device's internal memory so the app can
     * continue, if not will ask the user if he want to download required data.
     */
    private void ensureAvailableCelebImages() {
        if (!loadCelebImagesFromDisk()) {
            showMissingDataDialog();
        }
        else {
            startGame();
        }
    }


    /**
     * Shows a dialog informing the user that missing data is required for the app to work and
     * let's him choose if to try and acquire said data from internet or leave the app.
     */
    private void showMissingDataDialog() {
        final AlertDialog.Builder builder =
                new AlertDialog.Builder(this)
                        .setTitle("Missing celebrity data")
                        .setMessage("Do you want to download required data?")
                        .setCancelable(false)
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
        new DownloadHTMLSaveImagesTask().execute("http://www.posh24.com/celebrities");
    }

    
    private void startGame() {
        Integer[] randomCelebs = get4RandomCelebs();

        initButtonsAndImage(randomCelebs);
    }

    /**
     * Used to set the text for the four buttons with the names of {@link #mCelebsOnDisk} with
     * {@code randomCelebs} indexes. Will also set the image for apps ImageView to be one of the
     * 4 {@code randomCelebs} at random.
     * <br>The correct association between the displayed image and one of the buttons is to be
     * checked in the button's onClick listener.
     * @param randomCelebs array of valid indexes of {@link #mCelebsOnDisk} to display.
     */
    private void initButtonsAndImage(Integer[] randomCelebs) {
        Button[] buttons = {mOption1BTN, mOption2BTN, mOption3BTN, mOption4BTN};
        for (int i = 0; i < 4; i++) {
            buttons[i].setText(mCelebsOnDisk[randomCelebs[i]]);
        }

        // Select one celeb for which to display the image from the four random already settled.
        mDisplayedCeleb = mCelebsOnDisk[randomCelebs[mRandom.nextInt(randomCelebs.length)]];
        File imagesPath = new File(
                getApplicationContext().getFilesDir() + "/" + CELEB_IMAGES_FOLDER);
        mCelebIV.setImageBitmap(BitmapFactory.decodeFile(
                imagesPath.toString() + "/" + mDisplayedCeleb));

        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i].getText().equals(mDisplayedCeleb)) {
                Log.d(TAG, "Correct answer is " + (i + 1));
            }
        }
    }

    /**
     * Used for getting 4 random celebs to be used on each minigame.
     * @return Array of 4 random, unique, valid indexes of celebs from {@link #mCelebsOnDisk}.
     */
    @NonNull
    private Integer[] get4RandomCelebs() {
        HashSet<Integer> randCelebs = new HashSet<>();
        while (randCelebs.size() < 4) {
            randCelebs.add(mRandom.nextInt(mCelebsOnDisk.length));
        }
        return (randCelebs.toArray(new Integer[randCelebs.size()]));
    }


    /**
     * To be called only as a listener for app's buttons.
     * <br>Will check if the button pressed refers to the celeb with the image displayed and pass
     * the result of this check further to modify current score.
     * @param v {@code Button!} that was pressed.
     */
    @Override
    public void onClick(View v) {
        Button pressedBTN = ((Button) v);

        if (pressedBTN.getText().equals(mDisplayedCeleb)) {
            mNoOfCelebsGuessed++;
        }

        mNoOfCelebsShowed++;

        if (mIfShowCorrectAnswer) {
            Toast.makeText(MainActivity.this, "Was " + mDisplayedCeleb, Toast.LENGTH_SHORT).show();
        }

        assert getSupportActionBar() != null;
        getSupportActionBar().setSubtitle
                ("Score: " + mNoOfCelebsGuessed + " / " + mNoOfCelebsShowed);

        startGame();
    }


    /**
     * Will download the HTML code of a site &mdash; first String argument, then call another 
     * AsyncTask to extract celeb images and save them to device's internal memory.
     * @see site.petrumugurel.guessthecelebrity.MainActivity.DownloadSaveCelebImagesTask
     */
    private class DownloadHTMLSaveImagesTask extends AsyncTask<String, Void, String> {
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

            return result;
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
        protected void onPostExecute(String htmlRead) {
            mNotifBuilder.setContentText("Celebrity data downloaded")
                         .setProgress(0, 0, false)
                         .setOngoing(false);
            mNotificationManager.notify(DOWNLOAD_NOTIF_ID, mNotifBuilder.build());

            cancelNotifications(5000l, DOWNLOAD_NOTIF_ID);

            if (mSpotsDialog.isShowing()) {
                mSpotsDialog.dismiss();
            }

            // Get another AsyncTask to download celeb images and save them to internal storage.
            new DownloadSaveCelebImagesTask().execute(htmlRead);
        }
    }


    /**
     * Will extract from the first String received as argument celeb image URL and name, and then
     * will save that image with appropriate filename on device's internal storage.
     */
    private class DownloadSaveCelebImagesTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... HTMLCode) {

            // Need to first do a split because on the bottom of the page there are other
            // unneeded images. We only want the ones above.
            String[] readHTMLData  = HTMLCode[0].split("<div class=\"sidebarContainer\">");
            String   htmlCelebData = readHTMLData[0];

            // From all that html code get the celeb images and their names.
            // This is trivial, can be don on the main thread.
            ArrayList<String> celebURLs = extractCelebData(htmlCelebData, "<img src=\"(.+?)\"");
            ArrayList<String> celebNames = extractCelebData(htmlCelebData, "alt=\"(.+?)\"");

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
            }

            return null;
        }

        @Override
        protected void onPreExecute() {
            mNotifBuilder.setProgress(0, 0, false)
                         .setOngoing(false)
                         .setContentText("Celebrity images are prepared");
            mNotificationManager.notify(SAVE_IMAGES_NOTIF_ID, mNotifBuilder.build());

            mSpotsDialog.show();
            mSpotsDialog.setMessage("Saving celeb photos ...");
        }

        @Override
        protected void onPostExecute(Void result) {
            mNotifBuilder.setProgress(0, 0, false)
                         .setOngoing(false)
                         .setContentText("Celebrity images are prepared");
            mNotificationManager.notify(SAVE_IMAGES_NOTIF_ID, mNotifBuilder.build());

            cancelNotifications(5000l, SAVE_IMAGES_NOTIF_ID);

            if (mSpotsDialog.isShowing()) {
                mSpotsDialog.dismiss();
            }

            ensureAvailableCelebImages();
        }
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
            else {
                Log.i(TAG, "Invalid number of celebrities on disk");
            }
        }
        else {
            Log.i(TAG, "Invalid path for celeb images");
        }

        return ifImagesLoaded;
    }


    /**
     * Display a Toast message saying {@code "Cannot read images\nApp will be closing."}.
     * @param millisDelay time between showing the Toast and finishing the app.
     */
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
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.mainM_I_settyings:
                Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content),
                                                  "No more available settyings atm",
                                                  Snackbar.LENGTH_LONG);
                View snackbarView = snackbar.setActionTextColor(Color.WHITE).getView();
                snackbarView.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
                snackbarView.animate().rotationX(-360).setDuration(700);
                snackbar.setAction("Ok", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                    }
                }).show();
                break;

            case R.id.mainM_I_showAnswer:
                item.setChecked(!item.isChecked());
                mIfShowCorrectAnswer = item.isChecked();
                break;

            case R.id.mainM_I_resetScores:
                mNoOfCelebsShowed = mNoOfCelebsGuessed = 0;
                assert getSupportActionBar() != null;
                getSupportActionBar().setSubtitle("Once ate a whole bowl of soup");
                break;
        }

        return true;    // we've handled the press, don't want other listeners check for it
    }

    @Override
    public void onStart() {
        super.onStart();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onStop() {
        super.onStop();
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onDestroy() {
        cancelNotifications(0l, DOWNLOAD_NOTIF_ID, SAVE_IMAGES_NOTIF_ID);
        super.onDestroy();
    }

}
