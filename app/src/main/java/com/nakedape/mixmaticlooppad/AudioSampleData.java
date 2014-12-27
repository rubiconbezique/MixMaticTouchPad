package com.nakedape.mixmaticlooppad;

import android.app.Fragment;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Nathan on 10/10/2014.
 */
public class AudioSampleData extends Fragment {

    private String samplePath;
    private double sampleLength, selectionStartTime, selectionEndTime, windowStartTime, windowEndTime;

    private List<Line> waveFormData = new ArrayList<Line>();
    private List<BeatInfo> beatsData = new ArrayList<BeatInfo>();
    private List<Line> waveFormRender = new ArrayList<Line>();
    private List<BeatInfo> beatsRender = new ArrayList<BeatInfo>();

    public int color = 0;
    private String backgroundColor;
    private String foregroundColor;

    private boolean loop;

    private boolean isSliceMode;
    private int numSlices;

    private boolean isDecoding;
    private Uri fullMusicUri;
    private MediaPlayer mPlayer;

    private boolean isGeneratingWaveForm;
    private boolean showBeats;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // retain this fragment
        setRetainInstance(true);
    }

    public void setSamplePath(String samplePath) {this.samplePath = samplePath;}
    public String getSamplePath(){return samplePath;}

    public void setTimes(double sampleLength, double selectionStartTime, double selectionEndTime, double windowStartTime, double windowEndTime){
        this.sampleLength = sampleLength;
        this.selectionStartTime = selectionStartTime;
        this.selectionEndTime = selectionEndTime;
        this.windowStartTime = windowStartTime;
        this.windowEndTime = windowEndTime;
    }
    public double getSampleLength(){return sampleLength;}
    public double getSelectionStartTime() {return selectionStartTime;}
    public double getSelectionEndTime() {return selectionEndTime;}
    public double getWindowStartTime() {return windowStartTime;}
    public double getWindowEndTime() {return windowEndTime;}

    public void setWaveData(List<Line> waveFormData, List<BeatInfo> beatsData, List<Line> waveFormRender, List<BeatInfo> beatsRender){
        this.waveFormData = waveFormData;
        this.beatsData = beatsData;
        this.waveFormRender = waveFormRender;
        this.beatsRender = beatsRender;
    }
    public List<Line> getWaveFormData() {return waveFormData;}
    public List<BeatInfo> getBeatsData() {return beatsData;}
    public List<Line> getWaveFormRender() {return waveFormRender;}
    public List<BeatInfo> getBeatsRender() {return beatsRender;}

    public void setColor(int colorInt, String backgroundColor, String foregroundColor){
        this.color = colorInt;
        this.backgroundColor = backgroundColor;
        this.foregroundColor = foregroundColor;
    }
    public int getColor() {return color;}
    public String getBackgroundColor() {return backgroundColor;}
    public String getForegroundColor() {return foregroundColor;}

    public void setLoop(boolean loop){this.loop = loop;}
    public boolean getLoop() {return loop;}

    public void setSliceMode(boolean sliceMode) {this.isSliceMode = sliceMode;}
    public boolean isSliceMode() {return isSliceMode;}
    public void setNumSlices(int numSlices) {this.numSlices = numSlices;}
    public int getNumSlices() {return numSlices;}

    public void setDecoding(boolean isDecoding) {this.isDecoding = isDecoding;}
    public boolean isDecoding() {return isDecoding;}
    public void setFullMusicUri(Uri fullMusicUri) {this.fullMusicUri = fullMusicUri;}
    public Uri getFullMusicUri() {return fullMusicUri;}

    public void setGeneratingWaveForm(boolean isGeneratingWaveForm) {this.isGeneratingWaveForm = isGeneratingWaveForm;}
    public boolean isGeneratingWaveForm() {return isGeneratingWaveForm;}

    public void setmPlayer(MediaPlayer mPlayer){this.mPlayer = mPlayer;}
    public MediaPlayer getmPlayer(){return mPlayer;}

    public void setShowBeats(boolean showBeats) {this.showBeats = showBeats;}
    public boolean getShowBeats() {return showBeats;}

}
