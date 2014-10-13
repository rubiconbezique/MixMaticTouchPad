package com.nakedape.mixmatictouchpad;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.*;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;


public class LaunchPadActivity extends Activity implements AudioTrack.OnPlaybackPositionUpdateListener{

    public static String TOUCHPAD_ID = "com.nakedape.mixmatictouchpad.touchpadid";
    public static String SAMPLE_PATH = "com.nakedape.mixmatictouchpad.samplepath";
    public static String COLOR = "com.nakedape.mixmatictouchpad.color";
    public static String LOOP = "com.nakedape.mixmatictouchpad.loop";
    public static String LAUNCHMODE = "com.nakedape.mixmatictouchpad.launchmode";
    public static String NUM_SLICES = "com.nakedape.mixmatictouchpad.numslices";
    public static String SLICE_PATHS = "com.nakedape.mixmatictouchpad.slicepaths";
    private static int GET_SAMPLE = 0;
    private static int GET_SLICES = 1;
    private static final int COUNTER_UPDATE = 3;

    private boolean isEditMode = false;
    private boolean isPlaying = false;
    private TextView counterTextView;
    private long counter;
    private int bpm = 120;
    private int timeSignature = 4;
    private Handler counterHandler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg){
            switch (msg.what){
                case COUNTER_UPDATE:
                    double beatsPerSec = (double)bpm / 60;
                    double sec = (double)counter / 1000;
                    double beats = sec * beatsPerSec;
                    int bars = (int)Math.floor(beats / timeSignature);
                    counterTextView.setText(String.format(Locale.US, "%d BPM  %2d : %.2f", bpm, bars, beats % timeSignature + 1));
                    break;
            }
        }
    };

    private Context context;
    private HashMap<Integer, Sample> samples;
    private File homeDir;
    private int numTouchPads;
    private AudioManager am;
    private SharedPreferences pref;
    private LaunchPadData savedData;
    private boolean savedDataLoaded = false;

    private int selectedSampleID;
    private boolean multiSelect = false;
    private ArrayList<String> selections;
    private ActionMode launchPadActionMode;
    private ActionMode emptyPadActionMode;
    private ActionMode.Callback emptyPadActionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.launch_pad_empty_context, menu);
            isEditMode = true;
            View newView = findViewById(selectedSampleID);
            newView.setSelected(true);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            Intent intent;
            switch (item.getItemId()){
                case R.id.action_load_sample:
                    intent = new Intent(Intent.ACTION_SEND, null, context, SampleEditActivity.class);
                    intent.putExtra(TOUCHPAD_ID, selectedSampleID);
                    startActivityForResult(intent, GET_SAMPLE);
                    return true;
                case R.id.action_multi_select:
                    multiSelect = true;
                    selections = new ArrayList<String>();
                    selections.add(String.valueOf(selectedSampleID));
                    Menu menu = mode.getMenu();
                    MenuItem item2 = menu.findItem(R.id.action_load_sample);
                    item2.setVisible(false);
                    item2 = menu.findItem(R.id.action_multi_select);
                    item2.setVisible(false);
                    Toast.makeText(context, R.string.slice_multi_select, Toast.LENGTH_SHORT).show();
                    return true;
                case R.id.action_load_sample_mode:
                    if (multiSelect){
                        if (selections.size() > 0) {
                            intent = new Intent(Intent.ACTION_SEND, null, context, SampleEditActivity.class);
                            intent.putExtra(NUM_SLICES, selections.size());
                            startActivityForResult(intent, GET_SLICES);
                        }
                        else {
                            Toast.makeText(context, R.string.slice_selection_error, Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    }
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            emptyPadActionMode = null;
            isEditMode = false;
            multiSelect = false;
            View oldView = findViewById(selectedSampleID);
            oldView.setSelected(false);
            if (selections != null) {
                for (String s : selections) {
                    oldView = findViewById(Integer.parseInt(s));
                    oldView.setSelected(false);
                }
                selections = null;
            }
        }
    };
    private ActionMode.Callback launchPadActionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.launch_pad_context, menu);
            isEditMode = true;
            View oldView = findViewById(selectedSampleID);
            oldView.setSelected(true);
            if (samples.containsKey(selectedSampleID)) {
                Sample s = (Sample) samples.get(selectedSampleID);
                MenuItem item = menu.findItem(R.id.action_loop_mode);
                item.setChecked(s.getLoopMode());
                if (s.getLaunchMode() == Sample.LAUNCHMODE_TRIGGER) {
                    item = menu.findItem(R.id.action_launch_mode_trigger);
                    item.setChecked(true);
                }
                else {
                    item = menu.findItem(R.id.action_launch_mode_gate);
                    item.setChecked(true);
                }

            }
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            SharedPreferences.Editor prefEditor = pref.edit();
            switch (item.getItemId()){
                case R.id.action_edit_sample:
                    Intent intent = new Intent(Intent.ACTION_SEND, null, context, SampleEditActivity.class);
                    intent.putExtra(TOUCHPAD_ID, selectedSampleID);
                    if (samples.containsKey(selectedSampleID)){
                        intent.putExtra(SAMPLE_PATH, homeDir.getAbsolutePath() + "/" + String.valueOf(selectedSampleID) + ".wav");
                        intent.putExtra(COLOR, pref.getInt(String.valueOf(selectedSampleID) + COLOR, 0));
                    }
                    startActivityForResult(intent, GET_SAMPLE);
                    return true;
                case R.id.action_loop_mode:
                    if (item.isChecked()) {
                        item.setChecked(false);
                        if (samples.containsKey(selectedSampleID)) {
                            Sample s = (Sample)samples.get(selectedSampleID);
                            s.setLoopMode(false);
                            prefEditor.putBoolean(String.valueOf(selectedSampleID) + LOOP, false);
                            prefEditor.apply();
                        }
                    }
                    else {
                        item.setChecked(true);
                        if (samples.containsKey(selectedSampleID)) {
                            Sample s = (Sample)samples.get(selectedSampleID);
                            s.setLoopMode(true);
                            prefEditor.putBoolean(String.valueOf(selectedSampleID) + LOOP, true);
                            prefEditor.apply();
                        }
                    }
                    return true;
                case R.id.action_remove_sample:
                    if (samples.containsKey(selectedSampleID)){
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setMessage(getString(R.string.warning_remove_sample));
                        builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Sample s = (Sample)samples.get(selectedSampleID);
                                File f = new File(s.getPath());
                                f.delete();
                                samples.remove(selectedSampleID);
                                View v = findViewById(selectedSampleID);
                                v.setBackgroundResource(R.drawable.launch_pad_empty);
                                Toast.makeText(context, "Sample removed", Toast.LENGTH_SHORT).show();
                            }
                        });
                        builder.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                    return true;
                case R.id.action_launch_mode_gate:
                    if (samples.containsKey(selectedSampleID)){
                        Sample s = (Sample)samples.get(selectedSampleID);
                        s.setLaunchMode(Sample.LAUNCHMODE_GATE);
                        item.setChecked(true);
                        prefEditor.putInt(String.valueOf(selectedSampleID) + LAUNCHMODE, Sample.LAUNCHMODE_GATE);
                        prefEditor.apply();
                    }
                    return true;
                case R.id.action_launch_mode_trigger:
                    if (samples.containsKey(selectedSampleID)){
                        Sample s = (Sample)samples.get(selectedSampleID);
                        s.setLaunchMode(Sample.LAUNCHMODE_TRIGGER);
                        item.setChecked(true);
                        prefEditor.putInt(String.valueOf(selectedSampleID) + LAUNCHMODE, Sample.LAUNCHMODE_TRIGGER);
                        prefEditor.apply();
                    }
                    return true;
                case R.id.action_pick_color:
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle(R.string.color_dialog_title);
                    builder.setItems(R.array.color_names, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (samples.containsKey(selectedSampleID)){
                                View v = findViewById(selectedSampleID);// Load shared preferences to save color
                                SharedPreferences.Editor editor = pref.edit();
                                switch (which){ // Set and save color
                                    case 0:
                                        v.setBackgroundResource(R.drawable.launch_pad_blue);
                                        editor.putInt(String.valueOf(selectedSampleID) + COLOR, 0);
                                        break;
                                    case 1:
                                        v.setBackgroundResource(R.drawable.launch_pad_red);
                                        editor.putInt(String.valueOf(selectedSampleID) + COLOR, 1);
                                        break;
                                    case 2:
                                        v.setBackgroundResource(R.drawable.launch_pad_green);
                                        editor.putInt(String.valueOf(selectedSampleID) + COLOR, 2);
                                        break;
                                    case 3:
                                        v.setBackgroundResource(R.drawable.launch_pad_orange);
                                        editor.putInt(String.valueOf(selectedSampleID) + COLOR, 3);
                                        break;
                                }
                                editor.apply();
                            }
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.show();
                default:
                    return true;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            launchPadActionMode = null;
            View oldView = findViewById(selectedSampleID);
            oldView.setSelected(false);
            isEditMode = false;
        }
    };

    // Handles touch events in play mode;
    private View.OnTouchListener TouchPadTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (!isEditMode && samples.containsKey(v.getId())) {
                if (!isPlaying){
                    isPlaying = true;
                    new Thread(new CounterThread()).start();
                }
                Sample s = samples.get(v.getId());
                switch (event.getAction()) {
                    case MotionEvent.ACTION_UP:
                        switch (s.getLaunchMode()){
                            case Sample.LAUNCHMODE_GATE:
                                s.stop();
                                v.setPressed(false);
                                break;
                            default:
                                break;
                        }
                        return true;
                    case MotionEvent.ACTION_DOWN:
                        if (s.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                            s.stop();
                            v.setPressed(false);
                            return true;
                        }
                        else if (s.hasPlayed())
                            s.reset();
                        s.play();
                        v.setPressed(true);
                        return true;
                    default:
                        return false;
                }
            }
            return false;
        }
    };

    // Handles click events in edit mode
    private View.OnClickListener TouchPadClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (isEditMode && !multiSelect) {
                View oldView = findViewById(selectedSampleID);
                oldView.setSelected(false);
                selectedSampleID = v.getId();
                v.setSelected(true);
                if (samples.containsKey(v.getId())) {
                    if (emptyPadActionMode != null)
                        emptyPadActionMode = null;
                    if (launchPadActionMode == null)
                        launchPadActionMode = startActionMode(launchPadActionModeCallback);
                    Sample s = (Sample) samples.get(v.getId());
                    Menu menu = launchPadActionMode.getMenu();
                    MenuItem item = menu.findItem(R.id.action_loop_mode);
                    item.setChecked(s.getLoopMode());
                    if (s.getLaunchMode() == Sample.LAUNCHMODE_TRIGGER) {
                        item = menu.findItem(R.id.action_launch_mode_trigger);
                        item.setChecked(true);
                    }
                    else {
                        item = menu.findItem(R.id.action_launch_mode_gate);
                        item.setChecked(true);
                    }
                }
                else{
                    if (launchPadActionMode != null)
                        launchPadActionMode = null;
                    if (emptyPadActionMode == null)
                        emptyPadActionMode = startActionMode(emptyPadActionModeCallback);
                }
            }
            else if (multiSelect){
                if (!samples.containsKey(v.getId())) {
                    if (v.isSelected()) {
                        v.setSelected(false);
                        selections.remove(String.valueOf(v.getId()));
                    } else {
                        v.setSelected(true);
                        selections.add(String.valueOf(v.getId()));
                    }
                }
                else {
                    multiSelect = false;
                    onClick(v);
                }
            }
            else {
                selectedSampleID = v.getId();
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        if (requestCode == GET_SAMPLE && resultCode == RESULT_OK) {
            String path = data.getData().getPath();
            File f = new File(path); // File to contain the new sample
            if (!homeDir.isDirectory()) // If the home directory doesn't exist, create it
                homeDir.mkdir();
            // Create a new file to contain the new sample
            File sampleFile = new File(homeDir, String.valueOf(data.getIntExtra(TOUCHPAD_ID, 0)) + ".wav");
            if (sampleFile.isFile()) // Delete it if it already exists
                sampleFile.delete();
            // Copy new sample over
            try {
                CopyFile(f, sampleFile);
            } catch (IOException e){e.printStackTrace();}
            if (sampleFile.isFile()) { // If successful, prepare touchpad
                int id = data.getIntExtra(TOUCHPAD_ID, 0);
                samples.put(id, new Sample(sampleFile.getAbsolutePath(), id));
                TouchPad t = (TouchPad)findViewById(id);
                // Load shared preferences editor to save color
                SharedPreferences.Editor editor = pref.edit();
                switch (data.getIntExtra(COLOR, 0)){ // Set and save color
                    case 0:
                        t.setBackgroundResource(R.drawable.launch_pad_blue);
                        editor.putInt(String.valueOf(id) + COLOR, 0);
                        break;
                    case 1:
                        t.setBackgroundResource(R.drawable.launch_pad_red);
                        editor.putInt(String.valueOf(id) + COLOR, 1);
                        break;
                    case 2:
                        t.setBackgroundResource(R.drawable.launch_pad_green);
                        editor.putInt(String.valueOf(id) + COLOR, 2);
                        break;
                    case 3:
                        t.setBackgroundResource(R.drawable.launch_pad_orange);
                        editor.putInt(String.valueOf(id) + COLOR, 3);
                        break;
                }
                editor.apply();
            }
        }
        else if (requestCode == GET_SLICES && resultCode == RESULT_OK){
            String[] slicePaths = data.getStringArrayExtra(SLICE_PATHS);
            Log.d("Slice path", slicePaths[1]);
            for (int i = 0; i < selections.size(); i++){
                File tempFile = new File(slicePaths[i]);
                File sliceFile = new File(homeDir, selections.get(i) + ".wav" );
                if (sliceFile.isFile()) sliceFile.delete();
                // Copy new sample over
                try {
                    CopyFile(tempFile, sliceFile);
                } catch (IOException e){e.printStackTrace();}
                if (sliceFile.isFile()) { // If successful, prepare touchpad
                    int id = Integer.parseInt(selections.get(i));
                    // Load shared preferences editor to save color
                    SharedPreferences.Editor editor = pref.edit();
                    Sample sample = new Sample(sliceFile.getAbsolutePath(), id);
                    sample.setLaunchMode(Sample.LAUNCHMODE_GATE);
                    editor.putInt(String.valueOf(selectedSampleID) + LAUNCHMODE, Sample.LAUNCHMODE_GATE);
                    samples.put(id, sample);
                    TouchPad t = (TouchPad) findViewById(id);
                    switch (data.getIntExtra(COLOR, 0)) { // Set and save color
                        case 0:
                            t.setBackgroundResource(R.drawable.launch_pad_blue);
                            editor.putInt(String.valueOf(id) + COLOR, 0);
                            break;
                        case 1:
                            t.setBackgroundResource(R.drawable.launch_pad_red);
                            editor.putInt(String.valueOf(id) + COLOR, 1);
                            break;
                        case 2:
                            t.setBackgroundResource(R.drawable.launch_pad_green);
                            editor.putInt(String.valueOf(id) + COLOR, 2);
                            break;
                        case 3:
                            t.setBackgroundResource(R.drawable.launch_pad_orange);
                            editor.putInt(String.valueOf(id) + COLOR, 3);
                            break;
                    }
                    editor.apply();
                }
            }
        }
    }

    // Activity over rides
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch_pad);
        context = this;
        homeDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath() + "/MixMatic");
        pref = getPreferences(MODE_PRIVATE);
        counterTextView = (TextView)findViewById(R.id.textViewCounter);

        // find the retained fragment on activity restarts
        FragmentManager fm = getFragmentManager();
        savedData = (LaunchPadData) fm.findFragmentByTag("data");
        if (savedData != null){
            samples = savedData.getSamples();
            counter = savedData.getCounter();
            double sec = (double)counter / 1000;
            int min = (int)Math.floor(sec / 60);
            counterTextView.setText(String.format(Locale.US, "%d BPM  %2d : %.2f", bpm, min, sec % 60));
            savedDataLoaded = true;
        }
        else{
            savedData = new LaunchPadData();
            fm.beginTransaction().add(savedData, "data").commit();
        }
        // Set up touch pads and load samples
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT){
            setupPortrait();
        } else {
            setupLandscape();
        }

        // Set up audio
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
    }
    private void setupPortrait(){
        LinearLayout mainLayout = (LinearLayout)findViewById(R.id.mainLayout);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        if (!savedDataLoaded)
            samples = new HashMap<Integer, Sample>();
        int id = 0;
        for (int i = 0; i < 6; i++){
            LinearLayout l = new LinearLayout(this);
            l.setOrientation(LinearLayout.HORIZONTAL);
            mainLayout.addView(l);
            for (int j = 0; j < 4; j++){
                if (id == 6)
                    id = 35;
                TouchPad t = new TouchPad(this);
                t.setId(id);
                t.setWidth(metrics.widthPixels / 4);
                t.setHeight((metrics.heightPixels - 150) / 6);
                t.setOnTouchListener(TouchPadTouchListener);
                t.setOnClickListener(TouchPadClickListener);
                l.addView(t);
                if (homeDir.isDirectory()){ // If the home directory already exists, try to load samples
                    File sample = new File(homeDir, String.valueOf(id) + ".wav");
                    if (sample.isFile()){ // If the sample exists, load it
                        if (!savedDataLoaded) {
                            Sample s = new Sample(sample.getAbsolutePath(), id);
                            s.setOnPlayFinishedListener(this);
                            samples.put(id, s);
                            s.setLoopMode(pref.getBoolean(String.valueOf(id) + LOOP, false));
                            s.setLaunchMode(pref.getInt(String.valueOf(id) + LAUNCHMODE, Sample.LAUNCHMODE_TRIGGER));
                        }
                        int color = pref.getInt(String.valueOf(id) + COLOR, 0);
                        switch (color){ // Load and set color
                            case 0:
                                t.setBackgroundResource(R.drawable.launch_pad_blue);
                                break;
                            case 1:
                                t.setBackgroundResource(R.drawable.launch_pad_red);
                                break;
                            case 2:
                                t.setBackgroundResource(R.drawable.launch_pad_green);
                                break;
                            case 3:
                                t.setBackgroundResource(R.drawable.launch_pad_orange);
                                break;
                        }
                    }
                    else{
                        t.setBackgroundResource(R.drawable.launch_pad_empty);
                    }
                }
                if (id == 35)
                    id = 6;
                id++;
            }
        }
        numTouchPads = id;
    }
    private void setupLandscape(){
        getActionBar().hide();
        LinearLayout mainLayout = (LinearLayout)findViewById(R.id.mainLayout);
        LinearLayout padLayout = new LinearLayout(this);
        padLayout.setOrientation(LinearLayout.HORIZONTAL);
        mainLayout.addView(padLayout);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        if (!savedDataLoaded)
            samples = new HashMap<Integer, Sample>();
        int id = 0;
        for (int i = 0; i < 6; i++){
            LinearLayout l = new LinearLayout(this);
            l.setOrientation(LinearLayout.VERTICAL);
            padLayout.addView(l);
            for (int j = 0; j < 4; j++){
                if (id == 6)
                    id = 35;
                TouchPad t = new TouchPad(this);
                t.setId(id);
                t.setWidth(metrics.widthPixels / 6);
                t.setHeight((metrics.heightPixels - 75) / 4);
                t.setOnTouchListener(TouchPadTouchListener);
                t.setOnClickListener(TouchPadClickListener);
                l.addView(t);
                if (homeDir.isDirectory()){ // If the home directory already exists, try to load samples
                    File sample = new File(homeDir, String.valueOf(id) + ".wav");
                    if (sample.isFile()){ // If the sample exists, load it
                        if (!savedDataLoaded) {
                            Sample s = new Sample(sample.getAbsolutePath(), id);
                            s.setOnPlayFinishedListener(this);
                            samples.put(id, s);
                            s.setLoopMode(pref.getBoolean(String.valueOf(id) + LOOP, false));
                            s.setLaunchMode(pref.getInt(String.valueOf(id) + LAUNCHMODE, Sample.LAUNCHMODE_TRIGGER));
                        }
                        int color = pref.getInt(String.valueOf(id) + COLOR, 0);
                        switch (color){ // Load and set color
                            case 0:
                                t.setBackgroundResource(R.drawable.launch_pad_blue);
                                break;
                            case 1:
                                t.setBackgroundResource(R.drawable.launch_pad_red);
                                break;
                            case 2:
                                t.setBackgroundResource(R.drawable.launch_pad_green);
                                break;
                            case 3:
                                t.setBackgroundResource(R.drawable.launch_pad_orange);
                                break;
                        }
                    }
                    else{
                        t.setBackgroundResource(R.drawable.launch_pad_empty);
                    }
                }
                if (id == 35)
                    id = 6;
                id++;
            }
        }
        numTouchPads = id;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.touch_pad, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            isPlaying = false;
            Intent intent = new Intent(EditPreferencesActivity.LAUNCHPAD_PREFS, null, context, EditPreferencesActivity.class);
            startActivity(intent);
            return true;
        }
        else if (id == R.id.action_edit_mode) {
            isEditMode = true;
            isPlaying = false;
            View v = findViewById(0);
            v.callOnClick();
        }
        else if (id == R.id.action_stop){
            isPlaying = false;
            for (int i = 0; i < numTouchPads; i++) {
                if (samples.containsKey(i)) {
                    Sample s = (Sample) samples.get(i);
                    s.stop();
                }
            }

        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    protected void onDestroy(){
        isPlaying = false;
        savedData.setSamples(samples);
        savedData.setCounter(counter);
        super.onDestroy();
    }

    // AudioTrack playback update listener overrides used to deselect a touchpad when the sound stops
    @Override
    public void onMarkerReached(AudioTrack track){
        int id = track.getAudioSessionId();
        View v = findViewById(id);
        if (v != null) {
            v.setPressed(false);
            Sample s = (Sample)samples.get(id);
            s.stop();
        }

    }
    @Override
    public void onPeriodicNotification(AudioTrack track){
    }

    // Counter thread keeps track of time
    private class CounterThread implements Runnable {
        @Override
        public void run(){
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            long startMillis = SystemClock.elapsedRealtime();
            do {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {}
                counter = SystemClock.elapsedRealtime() - startMillis;
                Message msg = counterHandler.obtainMessage(COUNTER_UPDATE);
                msg.sendToTarget();
            } while (isPlaying);
        }
    }

    /**
     * copy file from source to destination
     *
     * @param src source
     * @param dst destination
     * @throws java.io.IOException in case of any problems
     */
    private void CopyFile(File src, File dst) throws IOException {
        FileChannel inChannel = new FileInputStream(src).getChannel();
        FileChannel outChannel = new FileOutputStream(dst).getChannel();
        try {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } finally {
            if (inChannel != null)
                inChannel.close();
            if (outChannel != null)
                outChannel.close();
        }
    }

    public class Sample{
        // Public fields
        public static final int LAUNCHMODE_GATE = 0;
        public static final int LAUNCHMODE_TRIGGER = 1;

        // Private fields
        private int id;
        private String path;
        private boolean loop = false;
        private int loopMode = 0;
        private int launchMode = LAUNCHMODE_TRIGGER;
        private File sampleFile;
        private int sampleByteLength;
        private boolean played = false;
        private AudioTrack audioTrack;
        private AudioTrack.OnPlaybackPositionUpdateListener listener;

        // Constructors
        public Sample(String path, int id){
            this.path = path;
            this.id = id;
            sampleFile = new File(path);
            if (sampleFile.isFile()){
                sampleByteLength = (int)sampleFile.length() - 44;
                loadAudioTrack();
            }
        }
        public Sample(String path, int launchMode, boolean loopMode){
            this.path = path;
            loop = loopMode;
            if (!setLaunchMode(launchMode))
                this.launchMode = LAUNCHMODE_TRIGGER;
            loadAudioTrack();
        }

        // Public methods
        public void setViewId(int id){this.id = id;}
        public int getViewId(){return id;}
        public String getPath(){return path;}
        public void setLoopMode(boolean loop){
            this.loop = loop;
            if (loop) {
                loopMode = -1;
                if (hasPlayed()) {
                    audioTrack.stop();
                    audioTrack.flush();
                    audioTrack.reloadStaticData();
                    played = false;
                }
                if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED)
                    loadAudioTrack();
                audioTrack.setLoopPoints(0, sampleByteLength / 4, -1);
                audioTrack.setNotificationMarkerPosition(0);
                audioTrack.setPlaybackPositionUpdateListener(null);
            }
            else {
                loopMode = 0;
                if (hasPlayed()) {
                    audioTrack.stop();
                    audioTrack.flush();
                    audioTrack.reloadStaticData();
                    played = false;
                }
                try {
                    audioTrack.setLoopPoints(0, 0, 0);
                } catch (Exception e) {}
                setOnPlayFinishedListener(listener);
            }
        }
        public boolean getLoopMode(){
            return loop;
        }
        public int getLoopModeInt() {
            return loopMode;
        }
        public boolean setLaunchMode(int launchMode){
            if (launchMode == LAUNCHMODE_GATE){
                this.launchMode = LAUNCHMODE_GATE;
                return true;
            }
            else if (launchMode == LAUNCHMODE_TRIGGER){
                this.launchMode = LAUNCHMODE_TRIGGER;
                return true;
            }
            else
                return false;
        }
        public int getLaunchMode(){
            return launchMode;
        }
        public void play(){
            played = true;
            resetMarker();
            if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED)
                audioTrack.play();
            else {
                Log.d("AudioTrack", String.valueOf(id) + " uninitialized");
                loadAudioTrack();
            }
        }
        public void stop(){
            if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                try {
                    audioTrack.pause();
                    audioTrack.stop();
                    audioTrack.flush();
                    audioTrack.release();
                } catch (IllegalStateException e) {
                }
                loadAudioTrack();
            }
        }
        public void pause(){
            audioTrack.pause();
        }
        public void reset(){
            if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING)
                audioTrack.stop();
            audioTrack.flush();
            audioTrack.release();
            loadAudioTrack();
        }
        public boolean hasPlayed(){
            return played;
        }
        public void setOnPlayFinishedListener(AudioTrack.OnPlaybackPositionUpdateListener listener){
            this.listener = listener;
            audioTrack.setPlaybackPositionUpdateListener(listener);
            resetMarker();
        }
        public void resetMarker(){
            audioTrack.setNotificationMarkerPosition(sampleByteLength / 4 - 2000);
        }

        // Private methods
        private void loadAudioTrack() {
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    44100,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    sampleByteLength,
                    AudioTrack.MODE_STATIC, id);
            InputStream stream = null;
            try {
                stream = new BufferedInputStream(new FileInputStream(sampleFile));
                stream.skip(44);
                byte[] bytes = new byte[sampleByteLength];
                stream.read(bytes);
                short[] shorts = new short[bytes.length / 2];
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
                audioTrack.write(shorts, 0, shorts.length);
                stream.close();
                played = false;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (listener != null) {
                audioTrack.setPlaybackPositionUpdateListener(listener);
                resetMarker();
            }

            if (loop) {
                setLoopMode(true);
            }
        }
    }
}
