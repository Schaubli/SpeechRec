/* ====================================================================
 * Copyright (c) 2014 Alpha Cephei Inc.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY ALPHA CEPHEI INC. ``AS IS'' AND
 * ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL CARNEGIE MELLON UNIVERSITY
 * NOR ITS EMPLOYEES BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ====================================================================
 */

package edu.cmu.pocketsphinx.demo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

import static android.widget.Toast.makeText;

public class PocketSphinxActivity extends Activity implements
        RecognitionListener {


    /* Keyword we are looking for to activate menu */
    private static String keyword = "alexa";
    private static float keywordThreshold = 50f;
    private static String KWS_SEARCH = "Default search";
    public boolean isStarting = false;
    public boolean hasUpdate = false;
    private static String debugPrefix = "earbuddy ";

    /* Used to handle permission request */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    private SpeechRecognizer recognizer;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        // Prepare the data for UI
        setContentView(R.layout.main);
       // ((TextView) findViewById(R.id.caption_text))
        //        .setText("Preparing the recognizer");
        Log.d(debugPrefix + "Init","Checking permisssion");
        // Check if user has given permission to record audio
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {

            Log.d(debugPrefix + "Init","Requesting permisssion");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
            return;
        }
        keyword = ((EditText) findViewById(R.id.et_name)).getText().toString();
        runRecognizerSetup();
        addListeners();
    }

    private void addListeners() {
        ((SeekBar) findViewById(R.id.seekBar)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                keywordThreshold = seekBar.getProgress();
                Log.d(debugPrefix + "New Threshold","Changed threshold bar to "+seekBar.getProgress()+" ");
                changedRecognizerSettings();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar){}


            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,boolean fromUser) {
                ((TextView) findViewById(R.id.seekBarValue)).setText(progress+"%");
                 /* t1.setTextSize(progress); Toast.makeText(getApplicationContext(), String.valueOf(progress),Toast.LENGTH_LONG).show();*/
            }

        });

        ((EditText) findViewById(R.id.et_name)).addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
                keyword = s.toString();
                Log.d(debugPrefix + "ChangedKeyword", "Changed keyword to "+keyword);
                changedRecognizerSettings();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
                //if(s.length() != 0)
                    //Field2.setText("");
            }
        });
    }


    public void changedRecognizerSettings() {
        Log.d("OnChangedValue", "Restarting recognizer with keyword "+keyword+" and threshold of "+keywordThreshold);
        setStatusText("Restarting...");
        restartRecognizer();
    }

    public void restartRecognizer() {
        if(isStarting == false) {
            destroyRecognizer();
            runRecognizerSetup();
        } else {
            hasUpdate = true;
        }
    }

    public void setStatusText(String s) {
        ((TextView) findViewById(R.id.status_text)).setText(s);
    }

    private void runRecognizerSetup() {
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        isStarting = true;

        Log.d(debugPrefix + "Setup", "Running recognizer setup");

        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(PocketSphinxActivity.this);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                if (result != null) {
                    isStarting = false;
                    if(hasUpdate) {
                        hasUpdate = false;
                        Log.d(debugPrefix + "Setup", "Restarting recognizer");
                        restartRecognizer();
                    }
                    if(result.getMessage().contains("Microphone")){
                        //restartRecognizer();
                        return;
                    }
                    setStatusText("Error: " + result.getMessage());
                } else {
                    isStarting = false;
                    if(hasUpdate) {
                        hasUpdate = false;
                        restartRecognizer();
                    }
                    if(recognizer != null) {
                        recognizer.startListening(KWS_SEARCH);
                    }
                    Log.d(debugPrefix + "Setup", "Successfully started recognizer");
                    setStatusText("Recognizer is running");
                }
            }
        }.execute();
    }


    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them

        if(keyword.length()==0) {
            Log.e(debugPrefix + "Setup", "No keyword set, not setting up recognizer");
            throw new IOException("Keyword required");
        }

        float calculatedThreshold = (float) Math.pow(10f,(100f-keywordThreshold)/100f*-38f);

        recognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))
                .setKeywordThreshold(calculatedThreshold)
                .setFloat("-beam",calculatedThreshold)

                //.setRawLogDir(assetsDir) // To disable logging of raw audio comment out this call (takes a lot of space on the device)

                .getRecognizer();

        String[] words = keyword.split("\\s+");
        for( int i=0; i<words.length; i++) {
            if(recognizer.getDecoder().lookupWord(words[i].toLowerCase()) == null) {
                Log.e(debugPrefix + "Setup", "Could not find keyword \""+words[i]+"\" in dictionary");
                throw new IOException("Name "+words[i]+" not found in dictionary");
            }
        }

        recognizer.addListener(this);
        // Create keyword-activation search.
        recognizer.addKeyphraseSearch(KWS_SEARCH, keyword.toLowerCase());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                restartRecognizer();
            } else {
                Log.e(debugPrefix + "Setup", "Record audio permission denied by user");
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        destroyRecognizer();
    }

    public void destroyRecognizer() {
        if (recognizer != null) {
            recognizer.stop();
            recognizer.removeListener(this);
            recognizer.cancel();
            recognizer = null;
        }
    }

    @Override
    public void onBeginningOfSpeech() {
        /*Log.d(debugPrefix + "Recognization","Beginning of speech");*/
    }

    /**
     * In partial result we get quick updates about current hypothesis. In
     * keyword spotting mode we can react here, in other modes we need to wait
     * for final result in onResult.
     */
    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null){
            return;
        }

        String text = hypothesis.getHypstr();
        if (text.equals(keyword.toLowerCase())&& recognizer != null) {
            recognizer.stop();
            recognizer.startListening(KWS_SEARCH);
            Log.d(debugPrefix + "Recognization", "Recognized keyword \""+keyword+"\"");
            makeText(getApplicationContext(), "Keyword \""+keyword+"\" spotted", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * This callback is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {
        //((TextView) findViewById(R.id.status_text)).setText("");
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
        } else {

        }
    }

    /**
     * We stop recognizer here to get a final result
     */
    @Override
    public void onEndOfSpeech() {
        /*Log.d(debugPrefix + "Recognization","End of speech");*/
    }


    @Override
    public void onError(Exception error) {
        Log.d(debugPrefix + "Setup", "Got error setting up recognizer "+error.getMessage());
        ((TextView) findViewById(R.id.status_text)).setText(error.getMessage());
        restartRecognizer();
    }

    @Override
    public void onTimeout() {
        Log.d(debugPrefix + "OnTimeout", "Recognizer timed out.");
        recognizer.startListening(KWS_SEARCH);
    }
}
