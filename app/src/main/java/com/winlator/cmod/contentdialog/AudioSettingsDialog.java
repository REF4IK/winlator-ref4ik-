package com.winlator.cmod.contentdialog;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;

public class AudioSettingsDialog extends PreferenceDialogFragmentCompat {
    private View dialogView;
    private CheckBox checkAudioEnabled;
    private Spinner spinnerAudioBufferSize;
    private Spinner spinnerAudioSampleRate;
    private Spinner spinnerAudioChannels;
    private CheckBox checkAudioLatencyMode;
    private CheckBox checkAudioDspEnabled;
    
    private SharedPreferences sharedPreferences;
    
    public static AudioSettingsDialog newInstance(String key) {
        AudioSettingsDialog fragment = new AudioSettingsDialog();
        Bundle bundle = new Bundle();
        bundle.putString(PreferenceDialogFragmentCompat.ARG_KEY, key);
        fragment.setArguments(bundle);
        return fragment;
    }
    
    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        
        Context context = getContext();
        if (context == null) return;
        
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        
        dialogView = View.inflate(context, R.layout.audio_settings_dialog, null);
        builder.setView(dialogView);
        
        initViews();
        loadCurrentSettings();
        
        builder.setTitle(R.string.audio_settings_title);
    }
    
    private void initViews() {
        checkAudioEnabled = dialogView.findViewById(R.id.check_audio_enabled);
        spinnerAudioBufferSize = dialogView.findViewById(R.id.spinner_audio_buffer_size);
        spinnerAudioSampleRate = dialogView.findViewById(R.id.spinner_audio_sample_rate);
        spinnerAudioChannels = dialogView.findViewById(R.id.spinner_audio_channels);
        checkAudioLatencyMode = dialogView.findViewById(R.id.check_audio_latency_mode);
        checkAudioDspEnabled = dialogView.findViewById(R.id.check_audio_dsp_enabled);
    }
    
    private void loadCurrentSettings() {
        checkAudioEnabled.setChecked(sharedPreferences.getBoolean("audio_enabled", true));
        
        String bufferSize = sharedPreferences.getString("audio_buffer_size", "512");
        setSpinnerSelection(spinnerAudioBufferSize, bufferSize);
        
        String sampleRate = sharedPreferences.getString("audio_sample_rate", "44100 Hz");
        setSpinnerSelection(spinnerAudioSampleRate, sampleRate);
        
        String channels = sharedPreferences.getString("audio_channels", "Stereo (2)");
        setSpinnerSelection(spinnerAudioChannels, channels);
        
        checkAudioLatencyMode.setChecked(sharedPreferences.getBoolean("audio_low_latency_mode", false));
        checkAudioDspEnabled.setChecked(sharedPreferences.getBoolean("audio_dsp_enabled", false));
    }
    
    private void setSpinnerSelection(Spinner spinner, String value) {
        for (int i = 0; i < spinner.getCount(); i++) {
            if (spinner.getItemAtPosition(i).toString().equals(value)) {
                spinner.setSelection(i);
                break;
            }
        }
    }
    
    private void saveSettings() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        
        editor.putBoolean("audio_enabled", checkAudioEnabled.isChecked());
        editor.putString("audio_buffer_size", spinnerAudioBufferSize.getSelectedItem().toString());
        editor.putString("audio_sample_rate", spinnerAudioSampleRate.getSelectedItem().toString());
        editor.putString("audio_channels", spinnerAudioChannels.getSelectedItem().toString());
        editor.putBoolean("audio_low_latency_mode", checkAudioLatencyMode.isChecked());
        editor.putBoolean("audio_dsp_enabled", checkAudioDspEnabled.isChecked());
        
        editor.apply();
    }
    
    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            saveSettings();
        }
    }
}