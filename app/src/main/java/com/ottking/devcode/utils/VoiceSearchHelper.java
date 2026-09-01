package com.ottking.devcode.utils;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.ottking.devcode.R;

import java.util.ArrayList;

/**
 * Robust, crash-proof Voice Search Helper for Android TV and mobile devices.
 * Supports in-app SpeechRecognizer with custom DPAD-accessible listening UI,
 * graceful fallback to system RecognizerIntent, and safe permission handling.
 */
public class VoiceSearchHelper {

    public interface VoiceSearchCallback {
        void onResult(String query);
        void onCancelled();
    }

    private final Activity activity;
    private final VoiceSearchCallback callback;
    private ActivityResultLauncher<Intent> systemIntentLauncher;
    private ActivityResultLauncher<String> permissionLauncher;

    private SpeechRecognizer speechRecognizer;
    private Dialog voiceDialog;
    private TextView txtVoiceStatus;
    private ProgressBar progressVoice;
    private ImageView imgMicStatus;
    private Button btnVoiceCancel;
    private Button btnVoiceRetry;
    private View layoutMicContainer;

    private boolean isListening = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public VoiceSearchHelper(Activity activity, VoiceSearchCallback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    public void setLaunchers(ActivityResultLauncher<Intent> intentLauncher, ActivityResultLauncher<String> permLauncher) {
        this.systemIntentLauncher = intentLauncher;
        this.permissionLauncher = permLauncher;
    }

    /**
     * Start the voice search flow safely without crashing.
     */
    public void startVoiceSearch() {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        // 1. Check microphone permission
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            try {
                if (permissionLauncher != null) {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
                } else {
                    ActivityCompat.requestPermissions(activity,
                            new String[]{Manifest.permission.RECORD_AUDIO}, 101);
                }
            } catch (Exception e) {
                Toast.makeText(activity, R.string.toast_mic_permission_required, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // 2. Try In-App SpeechRecognizer first (most stable on Android TV)
        try {
            if (SpeechRecognizer.isRecognitionAvailable(activity)) {
                startInAppRecognition();
                return;
            }
        } catch (Throwable ignored) {
        }

        // 3. Fallback to System RecognizerIntent if available
        if (trySystemRecognizerIntent()) {
            return;
        }

        // 4. If neither works, show friendly toast without crashing
        Toast.makeText(activity, R.string.toast_voice_search_not_supported, Toast.LENGTH_SHORT).show();
        if (callback != null) {
            callback.onCancelled();
        }
    }

    private void startInAppRecognition() {
        destroyRecognizer();

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity);
            if (speechRecognizer == null) {
                if (!trySystemRecognizerIntent()) {
                    Toast.makeText(activity, R.string.toast_voice_search_not_supported, Toast.LENGTH_SHORT).show();
                }
                return;
            }

            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    isListening = true;
                    updateDialogState(true, "Listening... Say a channel name (e.g. Sports, News)", false);
                }

                @Override
                public void onBeginningOfSpeech() {
                    updateDialogState(true, "Recognizing your voice...", false);
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    // Audio level animation
                }

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    isListening = false;
                    updateDialogState(false, "Processing speech...", true);
                }

                @Override
                public void onError(int error) {
                    isListening = false;
                    handleRecognitionError(error);
                }

                @Override
                public void onResults(Bundle results) {
                    isListening = false;
                    dismissDialog();
                    if (results != null) {
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String query = matches.get(0);
                            if (query != null && !query.trim().isEmpty()) {
                                if (callback != null) {
                                    callback.onResult(query.trim());
                                }
                                return;
                            }
                        }
                    }
                    if (callback != null) {
                        callback.onCancelled();
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    if (partialResults != null) {
                        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String partial = matches.get(0);
                            if (txtVoiceStatus != null && partial != null && !partial.isEmpty()) {
                                txtVoiceStatus.setText(partial);
                            }
                        }
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });

            showVoiceDialog();

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

            speechRecognizer.startListening(intent);

        } catch (Throwable t) {
            destroyRecognizer();
            dismissDialog();
            if (!trySystemRecognizerIntent()) {
                Toast.makeText(activity, R.string.toast_voice_search_not_supported, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean trySystemRecognizerIntent() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say channel name (e.g. Sports, News)...");

            if (systemIntentLauncher != null) {
                systemIntentLauncher.launch(intent);
                return true;
            } else if (intent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivityForResult(intent, 102);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void showVoiceDialog() {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        if (voiceDialog == null) {
            voiceDialog = new Dialog(activity);
            voiceDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            voiceDialog.setContentView(R.layout.dialog_voice_search);

            if (voiceDialog.getWindow() != null) {
                voiceDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }

            txtVoiceStatus = voiceDialog.findViewById(R.id.txtVoiceStatus);
            progressVoice = voiceDialog.findViewById(R.id.progressVoice);
            imgMicStatus = voiceDialog.findViewById(R.id.imgMicStatus);
            btnVoiceCancel = voiceDialog.findViewById(R.id.btnVoiceCancel);
            btnVoiceRetry = voiceDialog.findViewById(R.id.btnVoiceRetry);
            layoutMicContainer = voiceDialog.findViewById(R.id.layoutMicContainer);

            if (btnVoiceCancel != null) {
                btnVoiceCancel.setOnClickListener(v -> {
                    cancelListening();
                    dismissDialog();
                    if (callback != null) callback.onCancelled();
                });
            }

            if (btnVoiceRetry != null) {
                btnVoiceRetry.setOnClickListener(v -> {
                    startInAppRecognition();
                });
            }

            voiceDialog.setOnCancelListener(dialog -> {
                cancelListening();
                if (callback != null) callback.onCancelled();
            });
        }

        updateDialogState(true, "Listening... Say a channel name (e.g. Sports, News)", false);
        try {
            voiceDialog.show();
            if (btnVoiceCancel != null) {
                btnVoiceCancel.requestFocus();
            }
        } catch (Throwable ignored) {}
    }

    private void updateDialogState(boolean isListening, String statusText, boolean isProcessing) {
        mainHandler.post(() -> {
            if (txtVoiceStatus != null) {
                txtVoiceStatus.setText(statusText);
            }
            if (progressVoice != null) {
                progressVoice.setVisibility(isProcessing ? View.VISIBLE : View.GONE);
            }
            if (imgMicStatus != null) {
                imgMicStatus.setVisibility(isProcessing ? View.INVISIBLE : View.VISIBLE);
            }
            if (btnVoiceRetry != null) {
                btnVoiceRetry.setVisibility(View.GONE);
            }
            if (layoutMicContainer != null && isListening) {
                startPulseAnimation(layoutMicContainer);
            } else if (layoutMicContainer != null) {
                layoutMicContainer.clearAnimation();
            }
        });
    }

    private void handleRecognitionError(int error) {
        String errorMsg;
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                errorMsg = "Audio recording error. Please check your microphone.";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                errorMsg = "Voice search cancelled or client error.";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                errorMsg = "Microphone permission required for voice search.";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                errorMsg = "Network error. Please check connection.";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                errorMsg = "Could not hear any channel name. Please try again.";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                errorMsg = "Voice recognizer is busy. Retrying...";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                errorMsg = "No speech detected. Please speak clearly into the remote.";
                break;
            default:
                errorMsg = "Voice search did not catch that. Please speak again.";
                break;
        }

        mainHandler.post(() -> {
            if (txtVoiceStatus != null) {
                txtVoiceStatus.setText(errorMsg);
            }
            if (progressVoice != null) {
                progressVoice.setVisibility(View.GONE);
            }
            if (imgMicStatus != null) {
                imgMicStatus.setVisibility(View.VISIBLE);
            }
            if (layoutMicContainer != null) {
                layoutMicContainer.clearAnimation();
            }
            if (btnVoiceRetry != null) {
                btnVoiceRetry.setVisibility(View.VISIBLE);
                btnVoiceRetry.requestFocus();
            }
        });
    }

    private void startPulseAnimation(View view) {
        Animation anim = new AlphaAnimation(0.4f, 1.0f);
        anim.setDuration(700);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        view.startAnimation(anim);
    }

    private void cancelListening() {
        try {
            if (speechRecognizer != null) {
                speechRecognizer.cancel();
            }
        } catch (Throwable ignored) {}
        isListening = false;
    }

    public void dismissDialog() {
        try {
            if (voiceDialog != null && voiceDialog.isShowing()) {
                voiceDialog.dismiss();
            }
        } catch (Throwable ignored) {}
    }

    public void destroy() {
        cancelListening();
        destroyRecognizer();
        dismissDialog();
        voiceDialog = null;
    }

    private void destroyRecognizer() {
        try {
            if (speechRecognizer != null) {
                speechRecognizer.destroy();
                speechRecognizer = null;
            }
        } catch (Throwable ignored) {}
    }
}
