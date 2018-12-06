package za.co.twinc.colorsofnoise;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.annotation.UiThread;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

import za.co.twinc.colorsofnoise.billing.BillingManager;
import za.co.twinc.colorsofnoise.billing.BillingProvider;
import za.co.twinc.colorsofnoise.skulist.AcquireFragment;

import static android.support.v4.app.NotificationCompat.CATEGORY_SERVICE;
import static za.co.twinc.colorsofnoise.billing.BillingManager.BILLING_MANAGER_NOT_INITIALIZED;

public class MainActivity extends AppCompatActivity implements BillingProvider{

    public String MAIN_PREFS = "main_app_prefs";

    private BillingManager mBillingManager;
    private AcquireFragment mAcquireFragment;
    private MainViewController mViewController;
    private View mScreenWait, mScreenMain;

    private ImageButton playButton;
    private ImageView image;
    private SeekBar seekBar;

    private Thread thread;
    private int sampleRate;

    private FloatingActionButton fab;
    private Snackbar snackbar;
    private TextView textView;

    private TextView textTimer;
    private CountDownTimer countDownTimer;
    private int secondsTimer;

    private boolean isRunning = false;
    private boolean redShift = true;
    private boolean pinkShift = false;

    private int amplitude;

    private AdView adView;
    private Button adButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start the controller and load data
        mViewController = new MainViewController(this);

        // Create and initialize BillingManager which talks to BillingLibrary
        mBillingManager = new BillingManager(this, mViewController.getUpdateListener());

        mScreenWait = findViewById(R.id.screen_wait);
        mScreenMain = findViewById(R.id.screen_main);

        // Initialise the invisible 'why ads?' button
        adButton = findViewById(R.id.why_ads);


        // Set up the rectangle (banner) adView
        adView = findViewById(R.id.adView);

        // Create main share preference log
        final SharedPreferences main_log = getSharedPreferences(MAIN_PREFS, 0);

        // Get the timer textView. Note that the timer can be initialised even if it is not visible
        textTimer = findViewById(R.id.textTimer);
        secondsTimer = main_log.getInt("secondsTimer",0);
        displayTimer();
        textTimer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setTimer();
            }
        });

        // Free ad space if premium
        if (main_log.getBoolean("premium", false)) {
            adView.setVisibility(View.GONE);
            textTimer.setVisibility(View.VISIBLE);
        }
        else {
            // Load rectangle add
            adView.setVisibility(View.VISIBLE);
            AdRequest adRequest = new AdRequest.Builder()
                    .addTestDevice("5F2995EE0A8305DEB4C48C77461A7362")
                    .build();
            adView.loadAd(adRequest);
            adView.setAdListener(new AdListener(){
                @Override
                public void onAdLoaded(){
                    adButton.setVisibility(View.VISIBLE);
                }
            });
        }

        // FAB to get extra info about the type of noise
        fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String info;
                if (redShift) info = getString(R.string.red_description);
                else if (pinkShift) info = getString(R.string.pink_description);
                else info = getString(R.string.white_description);
                snackbar = Snackbar.make(view, info, Snackbar.LENGTH_INDEFINITE)
                                            .setAction(getString(android.R.string.ok), new snackbarListener());
                TextView textView = snackbar.getView().findViewById(android.support.design.R.id.snackbar_text);
                textView.setMaxLines(10);  // show multiple line
                snackbar.show();
            }
        });

        // To get preferred buffer size and sampling rate.
        AudioManager audioManager = (AudioManager) this.getSystemService(AUDIO_SERVICE);
        if (audioManager != null)
            sampleRate = Integer.parseInt(audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE));
        else
            sampleRate = 144000;

        playButton = findViewById(R.id.play_button);
        textView = findViewById(R.id.textView);

        // Set volume bar
        seekBar = findViewById(R.id.volume_seekBar);
        amplitude = 32000*main_log.getInt("volume",80)/seekBar.getMax();
        seekBar.setProgress(main_log.getInt("volume",80));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if (b){
                    amplitude = 32000*i/seekBar.getMax();
                    SharedPreferences.Editor editor = main_log.edit();
                    editor.putInt("volume", i);
                    editor.apply();
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {            }
        });

        // Initialise top image
        image = findViewById(R.id.spectrum_imageView);

        setButtonColors();

        // Remember state
        // Todo: use only one onclick with shared logic
        if (main_log.getBoolean("pink",false))
            onButtonPinkClick(findViewById(R.id.pink_button));
        else if (main_log.getBoolean("red",false))
            onButtonRedClick(findViewById(R.id.white_button));
        else
            onButtonWhiteClick(findViewById(R.id.white_button));
    }

    @Override
    public BillingManager getBillingManager() {
        return mBillingManager;
    }

    @Override
    public boolean isPremiumPurchased() {
        return mViewController.isPremiumPurchased();
    }

    void onBillingManagerSetupFinished() {
        if (mAcquireFragment != null) {
            mAcquireFragment.onManagerReady(this);
        }
    }

    /**
     * Enables or disables the "please wait" screen.
     */
    private void setWaitScreen(@SuppressWarnings("SameParameterValue") boolean set) {
        mScreenMain.setVisibility(set ? View.GONE : View.VISIBLE);
        mScreenWait.setVisibility(set ? View.VISIBLE : View.GONE);
    }

    private boolean isAcquireFragmentShown() {
        return mAcquireFragment != null && mAcquireFragment.isVisible();
    }

    /**
     * Remove loading spinner and refresh the UI
     */
    public void showRefreshedUi() {
        setWaitScreen(false);
        updateUi();
        if (mAcquireFragment != null) {
            mAcquireFragment.refreshUI();
        }
    }

    /**
     * Update UI to reflect model
     */
    @UiThread
    private void updateUi() {
        updateUi(isPremiumPurchased());
    }

    private void updateUi(boolean premium){
        if (premium) {
            adView.setVisibility(View.GONE);
            adButton.setVisibility(View.GONE);
            textTimer.setVisibility(View.VISIBLE);
        }
    }

    @SuppressWarnings("deprecation")
    private void setButtonColors(){
        if (Build.VERSION.SDK_INT >= 23){
            playButton.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.button)));
            Button button;
            int[] buttons = {R.id.white_button, R.id.pink_button, R.id.red_button};
            for (int b: buttons){
                button = findViewById(b);
                button.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.button)));
                button.setTextColor(getColor(R.color.background));
            }
        }
        else if (Build.VERSION.SDK_INT >= 21){
            playButton.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.button)));
            Button button;
            int[] buttons = {R.id.white_button, R.id.pink_button, R.id.red_button};
            for (int b: buttons) {
                button = findViewById(b);
                button.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.button)));
                button.setTextColor(getResources().getColor(R.color.background));
            }
        }
        else{
            playButton.setBackgroundColor(getResources().getColor(R.color.button));
        }
    }

    private class snackbarListener implements View.OnClickListener{
        @Override
        public void onClick(View v) {
            // Todo: some code here
        }
    }

    @Override
    public void onDestroy(){
        cancelNotification();
        stopAndDestroyThread();
        if (mBillingManager != null) {
            mBillingManager.destroy();
        }
        super.onDestroy();
    }


    public void onButtonPlayClick(@SuppressWarnings("UnusedParameters") View view) {
        removeSnackbar();
        togglePlayback();
    }

    private void togglePlayback(){
        if (isRunning)
            stopPlayback();
        else
            startPlayback();
    }

    private void stopPlayback(){
        stopAndDestroyThread();
        playButton.setImageResource(android.R.drawable.ic_media_play);
        cancelNotification();
        stopTimer();
    }

    private void startPlayback(){
        createAndStartThread();
        playButton.setImageResource(android.R.drawable.ic_media_pause);
        setNotification();
        startTimer();
    }

    public void onButtonAdsClick(@SuppressWarnings("UnusedParameters") View view){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getResources().getString(R.string.ads_title));
        builder.setMessage(getResources().getString(R.string.ads_msg));

        builder.setPositiveButton(R.string.upgrade, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                upgrade();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {            }
        });
        builder.create().show();

    }


    private void upgrade(){
        if (mAcquireFragment == null) {
            mAcquireFragment = new AcquireFragment();
        }

        if (!isAcquireFragmentShown()) {
            mAcquireFragment.show(getSupportFragmentManager(), "dialog");

            if (mBillingManager != null
                    && mBillingManager.getBillingClientResponseCode()
                    > BILLING_MANAGER_NOT_INITIALIZED) {
                mAcquireFragment.onManagerReady(this);
            }
        }
    }


    private void premiumUnlock(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getResources().getString(R.string.premium_title));
        builder.setMessage(getResources().getString(R.string.premium_msg));

        final EditText input = new EditText(getApplicationContext());
        input.setTextColor(getResources().getColor(android.R.color.black));
        input.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        builder.setView(input);

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (input.getText().toString().trim().toLowerCase().equals("twincapps")) {
                    // Activate premium
                    SharedPreferences mainPrefs = getSharedPreferences(MAIN_PREFS, 0);
                    SharedPreferences.Editor editor = mainPrefs.edit();
                    editor.putBoolean("premium",true);
                    editor.apply();

                    //Also now hide the ad and 'why ads?' button and show the timer
                    updateUi(true);

                    Toast.makeText(getApplicationContext(), R.string.welcome_premium, Toast.LENGTH_LONG).show();
                }
                else
                    Toast.makeText(getApplicationContext(), R.string.wrong_code, Toast.LENGTH_LONG).show();
            }
        });

        builder.setNeutralButton(R.string.btn_contact_us, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                emailIntent.setData(Uri.parse("mailto:dev.twinc@gmail.com?subject=CoN%20premium"));

                try {
                    startActivity(emailIntent);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this,getResources().getString(R.string.txt_no_email),
                            Toast.LENGTH_LONG).show();
                }
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {            }
        });
        builder.create().show();
    }

    // TODO: Consolidate methods to remove code duplication
    public void onButtonWhiteClick(@SuppressWarnings("UnusedParameters") View v){
        image.setImageResource(R.drawable.headphones_white);
        textView.setText(R.string.white_txt);
        redShift = false;
        pinkShift = false;
        removeSnackbar();
        drawNoise(R.color.white);
        setNotification();
    }

    public void onButtonPinkClick(@SuppressWarnings("UnusedParameters") View v){
        image.setImageResource(R.drawable.headphones_pink);
        textView.setText(R.string.pink_txt);
        redShift = false;
        pinkShift = true;
        removeSnackbar();
        drawNoise(R.color.pink);
        setNotification();
    }
    public void onButtonRedClick(@SuppressWarnings("UnusedParameters") View v){
        image.setImageResource(R.drawable.headphones_red);
        textView.setText(R.string.red_txt);
        redShift = true;
        pinkShift = false;
        removeSnackbar();
        drawNoise(R.color.red);
        setNotification();
    }

    private void drawNoise(int color){
        changeFabColor(color);
        //noinspection deprecation
        seekBar.getProgressDrawable().setColorFilter(getResources().getColor(color), PorterDuff.Mode.MULTIPLY);
        //noinspection deprecation
        seekBar.getThumb().setColorFilter(getResources().getColor(color), PorterDuff.Mode.SRC_IN);
        SharedPreferences main_log = getSharedPreferences(MAIN_PREFS, 0);
        SharedPreferences.Editor editor = main_log.edit();
        if (color==R.color.red) {
            editor.putBoolean("red", true);
            editor.putBoolean("pink", false);
        }
        else if (color==R.color.pink){
            editor.putBoolean("red", false);
            editor.putBoolean("pink", true);
        }
        else {
            editor.putBoolean("red", false);
            editor.putBoolean("pink", false);
        }
        editor.apply();
    }

    private void removeSnackbar(){
        if (snackbar != null && snackbar.isShown())
            snackbar.dismiss();
    }

    private void changeFabColor(int color){
        if (Build.VERSION.SDK_INT < 23)
            return;
        fab.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(color,getTheme())));
    }

    private void createAndStartThread(){
        isRunning = true;
        // Start a new thread to synthesise audio
        thread = new Thread(){
            public void run(){
                // Set process priority
                setPriority(Thread.MAX_PRIORITY);

                int bufferSize = AudioTrack.getMinBufferSize(sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);

                // Create an audiotrack object
                AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STREAM);

                short samples[] = new short[bufferSize];
                Arrays.fill(samples,(short)0);

                // Start the audio
                audioTrack.play();
                Random random = new Random();

                short[] b = new short[3];

                // Define synthesis loop
                while(isRunning){
                    for(int i=0; i<bufferSize; i++){
                        short newWhite = (short)((-1.0 + 2.0 * random.nextFloat()) * amplitude);
                        b[0] = (short) (0.907*b[0] + 0.090042*newWhite);
                        b[1] = (short) (0.7643*b[1] + 0.23533*newWhite);
                        b[2] = (short) (0.35*b[2] + 0.646*newWhite);
                        if (redShift) {
                            samples[i] = b[0];
                        }
                        else if(pinkShift){
                            samples[i] = (short) ((b[0] + b[1] + b[2] + 0.1848*newWhite)*0.3);
                        }
                        else
                            samples[i] = (short) (newWhite*0.8);
                    }
                    audioTrack.write(samples, 0, bufferSize);
                }
                audioTrack.stop();
                audioTrack.release();
            }
        };

        // Start the audio thread
        thread.start();
    }

    private void stopAndDestroyThread(){
        if (isRunning) {
            isRunning = false;
            try {
                thread.join();
            } catch (InterruptedException | NullPointerException e) {
                e.printStackTrace();
            }
            thread = null;
        }
    }

    private void setNotification(){
        // Quick return if sound thread is not running
        if (!isRunning)
            return;

        // Build a notification
        // Create intent to open Main, load habit number in extras
        Intent openMainIntent = new Intent(this, MainActivity.class);

        openMainIntent.setAction("android.intent.action.MAIN");
        openMainIntent.addCategory("android.intent.category.LAUNCHER");

        String notificationText;
        if (redShift)
            notificationText = getString(R.string.red_txt);
        else if (pinkShift)
            notificationText = getString(R.string.pink_txt);
        else
            notificationText = getString(R.string.white_txt);

        PendingIntent openMainPendingIntent = PendingIntent.getActivity(this, 0, openMainIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                .setContentTitle(getString(R.string.app_name))
                .setContentText(notificationText)
                .setContentIntent(openMainPendingIntent)
                .setAutoCancel(false)
                .setOngoing(true)
                .setCategory(CATEGORY_SERVICE);
        Notification notification = mBuilder.build();

        // Hide small icon in Lollipop and Marshmallow notification pull down list as it looks shit
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            int smallIconViewId = this.getResources().getIdentifier("right_icon", "id", android.R.class.getPackage().getName());
            //noinspection deprecation
            if (notification.contentView != null)
                //noinspection deprecation
                notification.contentView.setViewVisibility(smallIconViewId, View.INVISIBLE);
        }

        // Issue notification
        NotificationManager mNotifyMgr = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (mNotifyMgr != null)
            mNotifyMgr.notify(0, notification);
    }

    private void cancelNotification(){
        NotificationManager mNotifyMgr = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (mNotifyMgr != null)
            mNotifyMgr.cancel(0);
    }

    @Override
    public void onBackPressed() {
        if (isRunning) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getResources().getString(R.string.alert_title));
            builder.setMessage(getResources().getString(R.string.alert_msg));

            builder.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    moveTaskToBack(true);
                }
            });

            builder.setNegativeButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                }
            });

            builder.create().show();
        }
        else
            super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_credits:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.icon_credit);
                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                builder.create().show();
                return true;
            case R.id.menu_share:
                String uri = "http://play.google.com/store/apps/details?id=" + getPackageName();
                Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
                sharingIntent.setType("text/plain");
                sharingIntent.putExtra(Intent.EXTRA_SUBJECT,getString(R.string.app_name));
                sharingIntent.putExtra(Intent.EXTRA_TEXT, uri);
                startActivity(Intent.createChooser(sharingIntent, getResources().getText(R.string.share)));
                return true;
            case R.id.menu_feedback:
                feedback();
                return true;
            case R.id.menu_premium:
                premiumUnlock();
                return true;
            case R.id.action_timer:
                menuTimerPressed();
                return true;
            case R.id.menu_purchase:
                upgrade();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void feedback(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getResources().getString(R.string.feedback_title));
        builder.setMessage(getResources().getString(R.string.feedback_msg));

        builder.setPositiveButton(getResources().getString(R.string.btn_rate_app), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                rateApp();
            }
        });

        builder.setNeutralButton(getResources().getString(R.string.btn_contact_us), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                emailIntent.setData(Uri.parse("mailto:dev.twinc@gmail.com?subject=CON%20feedback"));

                try {
                    startActivity(emailIntent);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this,getResources().getString(R.string.txt_no_email),
                            Toast.LENGTH_LONG).show();
                }
            }
        });
        builder.setNegativeButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        builder.create().show();
    }

    @SuppressWarnings("deprecation")
    private void rateApp(){
        Uri uri = Uri.parse("market://details?id=" + getPackageName());
        Intent goToMarket = new Intent(Intent.ACTION_VIEW, uri);
        // To count with Play market backstack, After pressing back button,
        // to taken back to our application, we need to add following flags to intent.
        goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            goToMarket.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT |
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        else {
            // Suppress deprecation
            goToMarket.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        }

        try {
            startActivity(goToMarket);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("http://play.google.com/store/apps/details?id=" + getPackageName())));
        }
    }

    private void menuTimerPressed(){
        SharedPreferences main_log = getSharedPreferences(MAIN_PREFS, 0);
        // Set timer if premium, otherwise display an upgrade dialog
        if (main_log.getBoolean("premium", false))
            setTimer();
        else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getResources().getString(R.string.timer_title));
            builder.setMessage(getResources().getString(R.string.timer_msg_upgrade));

            builder.setPositiveButton(getResources().getString(R.string.upgrade), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    upgrade();
                }
            });
            builder.setNegativeButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {                }
            });
            builder.create().show();
        }
    }

    private void setTimer(){
        AlertDialog.Builder timePicker = new AlertDialog.Builder(MainActivity.this);
        timePicker.setTitle(R.string.timer_title);
        timePicker.setMessage(R.string.timer_msg);

        View dialogView = MainActivity.this.getLayoutInflater().inflate(R.layout.timer_dialog, null);
        timePicker.setView(dialogView);

        SharedPreferences main_log = getSharedPreferences(MAIN_PREFS, 0);

        final NumberPicker numberPickerHour = dialogView.findViewById(R.id.numberPickerHour);
        numberPickerHour.setMinValue(0);
        numberPickerHour.setMaxValue(23);
        numberPickerHour.setValue(main_log.getInt("timer_hour", 0));

        final NumberPicker numberPickerMin = dialogView.findViewById(R.id.numberPickerMinute);
        numberPickerMin.setMinValue(0);
        numberPickerMin.setMaxValue(59);
        numberPickerMin.setValue(main_log.getInt("timer_min", 0));

        timePicker.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                SharedPreferences main_log = getSharedPreferences(MAIN_PREFS, 0);
                SharedPreferences.Editor editor = main_log.edit();

                int timerHour = numberPickerHour.getValue();
                int timerMin = numberPickerMin.getValue();
                secondsTimer = timerHour*3600 + timerMin*60;

                // Save the timer settings to display next time the user wants to set a timer
                editor.putInt("timer_hour", timerHour);
                editor.putInt("timer_min", timerMin);

                // Check for a previous timer and stop that one
                stopTimer();

                // Save the time remaining on the timer
                editor.putInt("secondsTimer", secondsTimer);
                editor.apply();

                // Display the time
                displayTimer();
                // Check if the timer should start counting down
                if (isRunning)
                    startTimer();
            }
        });

        timePicker.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

            }
        });
        timePicker.create().show();
    }

    private void startTimer(){
        SharedPreferences main_log = getSharedPreferences(MAIN_PREFS, 0);
        // Quick return if no timer is set
        if (main_log.getInt("secondsTimer",0) == 0)
            return;

        countDownTimer = new CountDownTimer(secondsTimer * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                secondsTimer -= 1;
                displayTimer();
            }

            @Override
            public void onFinish() {
                stopPlayback();
                secondsTimer = 0;
                displayTimer();
                // Clear the saved remaining time on the timer
                SharedPreferences main_log = getSharedPreferences(MAIN_PREFS, 0);
                SharedPreferences.Editor editor = main_log.edit();
                editor.putInt("secondsTimer", 0);
                editor.apply();
            }
        }.start();
    }

    private void stopTimer(){
        if (countDownTimer != null)
            countDownTimer.cancel();
        SharedPreferences main_log = getSharedPreferences(MAIN_PREFS, 0);
        SharedPreferences.Editor editor = main_log.edit();
        // Save the time remaining on the timer
        editor.putInt("secondsTimer", secondsTimer);
        editor.apply();
    }

    private void displayTimer(){
        int hour = secondsTimer/3600;
        int min = (secondsTimer - hour*3600)/60;
        int sec = secondsTimer - hour*3600 - min*60;
        if (hour > 0)
            textTimer.setText(String.format(Locale.UK,"%d:%02d:%02d", hour, min, sec));
        else
            textTimer.setText(String.format(Locale.UK,"%02d:%02d", min, sec));
    }
}
