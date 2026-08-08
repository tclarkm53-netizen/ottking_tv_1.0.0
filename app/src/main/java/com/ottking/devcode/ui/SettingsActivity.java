package com.ottking.devcode.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ottking.devcode.R;
import com.ottking.devcode.model.UserInfo;
import com.ottking.devcode.network.ApiClient;
import com.ottking.devcode.preferences.AppPreferences;

import java.util.Arrays;
import java.util.List;

import com.ottking.devcode.utils.FocusManager;
import com.ottking.devcode.utils.InputFocusHelper;
import com.ottking.devcode.utils.UIUtils;
import com.ottking.devcode.viewmodel.FocusViewModel;

public class SettingsActivity extends AppCompatActivity {

    public static final String SCREEN_KEY = "SettingsActivity";

    private FrameLayout contentContainer;
    private AppPreferences prefs;
    private NavigationAdapter navAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIUtils.hideSystemUI(this);
        setContentView(R.layout.activity_settings);

        contentContainer = findViewById(R.id.contentContainer);
        prefs = AppPreferences.getInstance(this);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
        FocusManager.getInstance().trackFocus(this, SCREEN_KEY, btnBack, (v, hasFocus) -> {
            UIUtils.animateFocus(v, hasFocus);
        });
        btnBack.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                focusNavSection();
                return true;
            }
            return false;
        });

        RecyclerView recyclerSettingsNav = findViewById(R.id.recyclerSettingsNav);
        recyclerSettingsNav.setLayoutManager(new LinearLayoutManager(this));

        List<String> navItems = Arrays.asList("Account", "PlayerSettings", "TvSettings", "System");
        navAdapter = new NavigationAdapter(navItems, navItem -> loadSectionView(navItem));
        navAdapter.setNavigationListener(new NavigationAdapter.OnNavNavigationListener() {
            @Override
            public void onNavigateToContent() {
                focusContentSection();
            }

            @Override
            public void onNavigateToHeader() {
                btnBack.requestFocus();
            }
        });
        recyclerSettingsNav.setAdapter(navAdapter);

        // Load default section: Account
        loadSectionView("Account");
        recyclerSettingsNav.post(this::focusNavSection);

        FocusManager.getInstance().setupBackPressHandler(this, SCREEN_KEY, this::handleSettingsBackPressInternal);
    }

    private boolean handleSettingsBackPressInternal() {
        View currentFocus = getCurrentFocus();
        if (currentFocus != null && isViewInContentContainer(currentFocus)) {
            focusNavSection();
            return true;
        }
        return false;
    }

    private void loadSectionView(String section) {
        contentContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        switch (section) {
            case "Account":
                View accountView = inflater.inflate(R.layout.layout_settings_account, contentContainer, false);
                setupAccountView(accountView);
                contentContainer.addView(accountView);
                break;

            case "PlayerSettings":
                View playerView = inflater.inflate(R.layout.layout_settings_player, contentContainer, false);
                setupPlayerView(playerView);
                contentContainer.addView(playerView);
                break;

            case "TvSettings":
                View tvView = inflater.inflate(R.layout.layout_settings_tv, contentContainer, false);
                setupTvView(tvView);
                contentContainer.addView(tvView);
                break;

            case "System":
                View sysView = inflater.inflate(R.layout.layout_settings_system, contentContainer, false);
                setupSystemView(sysView);
                contentContainer.addView(sysView);
                break;
        }
    }

    private void setupAccountView(View view) {
        LinearLayout panelProfile = view.findViewById(R.id.panelProfile);
        LinearLayout panelLogin = view.findViewById(R.id.panelLogin);

        TextView txtProfileUsername = view.findViewById(R.id.txtProfileUsername);
        TextView txtProfilePackage = view.findViewById(R.id.txtProfilePackage);
        TextView txtProfileExpiry = view.findViewById(R.id.txtProfileExpiry);
        TextView txtProfileDeviceId = view.findViewById(R.id.txtProfileDeviceId);

        EditText edtUsername = view.findViewById(R.id.edtUsername);
        EditText edtPassword = view.findViewById(R.id.edtPassword);
        Button btnLogin = view.findViewById(R.id.btnLogin);
        Button btnLogout = view.findViewById(R.id.btnLogout);

        View.OnKeyListener openKeyboardKeyListener = (v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                v.setFocusableInTouchMode(true);
                if (v instanceof EditText) {
                    ((EditText) v).setCursorVisible(true);
                }
                v.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
                }
                return true;
            }
            return false;
        };

        if (edtUsername != null) {
            InputFocusHelper.bind(edtUsername, this, null, null);
            edtUsername.setOnKeyListener(openKeyboardKeyListener);
            edtUsername.setOnClickListener(v -> InputFocusHelper.activate(this, edtUsername, null));
        }
        if (edtPassword != null) {
            InputFocusHelper.bind(edtPassword, this, null, null);
            edtPassword.setOnKeyListener(openKeyboardKeyListener);
            edtPassword.setOnClickListener(v -> InputFocusHelper.activate(this, edtPassword, null));
        }

        String session = prefs.getSessionToken();
        if (!session.isEmpty()) {
            panelProfile.setVisibility(View.VISIBLE);
            panelLogin.setVisibility(View.GONE);

            txtProfileUsername.setText("Username: " + prefs.getUsername());
            txtProfilePackage.setText("Subscription: " + prefs.getUserPackage());
            txtProfileExpiry.setText("Expiry Date: " + prefs.getUserExpiry());
            txtProfileDeviceId.setText("Bound Device ID: " + prefs.getDeviceId());
        } else {
            panelProfile.setVisibility(View.GONE);
            panelLogin.setVisibility(View.VISIBLE);
        }

        if (btnLogin != null) {
            UIUtils.applyFocusAnimation(btnLogin, 1.06f, 8f);
        }
        if (btnLogout != null) {
            UIUtils.applyFocusAnimation(btnLogout, 1.06f, 8f);
        }

        btnLogin.setOnClickListener(v -> {
            String u = edtUsername.getText().toString().trim();
            String p = edtPassword.getText().toString().trim();

            if (u.isEmpty() || p.isEmpty()) {
                Toast.makeText(this, "Please enter username and password", Toast.LENGTH_SHORT).show();
                return;
            }

            btnLogin.setEnabled(false);
            btnLogin.setText("Signing In...");

            ApiClient.getInstance(this).login(u, p, new ApiClient.ApiCallback<UserInfo>() {
                @Override
                public void onSuccess(UserInfo result) {
                    Toast.makeText(SettingsActivity.this, "Welcome " + result.getUsername() + "!", Toast.LENGTH_SHORT).show();
                    loadSectionView("Account");
                }

                @Override
                public void onError(String errorMessage) {
                    btnLogin.setEnabled(true);
                    btnLogin.setText(getString(R.string.btn_sign_in));
                    new CustomDialog.Builder(SettingsActivity.this)
                            .setTitle(getString(R.string.title_login_failed))
                            .setMessage(errorMessage)
                            .setPositiveButton(getString(R.string.btn_ok), dialog -> dialog.dismiss())
                            .show();
                }
            });
        });

        btnLogout.setOnClickListener(v -> {
            btnLogout.setEnabled(false);
            btnLogout.setText("Logging out...");
            ApiClient.getInstance(this).logout(new ApiClient.ApiCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    Toast.makeText(SettingsActivity.this, result, Toast.LENGTH_SHORT).show();
                    loadSectionView("Account");
                }

                @Override
                public void onError(String errorMessage) {
                    Toast.makeText(SettingsActivity.this, "Logged out", Toast.LENGTH_SHORT).show();
                    loadSectionView("Account");
                }
            });
        });
    }

    private void setupPlayerView(View view) {
        Spinner spinnerPlayerAspectRatio = view.findViewById(R.id.spinnerPlayerAspectRatio);
        Spinner spinnerPlayerResolution = view.findViewById(R.id.spinnerPlayerResolution);
        SwitchCompat switchHwDecoder = view.findViewById(R.id.switchHwDecoder);
        Spinner spinnerPlayerBuffer = view.findViewById(R.id.spinnerPlayerBuffer);

        // 1. Aspect Ratio Setup
        String[] aspectRatios = {"Fit to Screen", "Stretch (Full Screen)", "Zoom 16:9", "Original (4:3)"};
        ArrayAdapter<String> aspectAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, aspectRatios);
        if (spinnerPlayerAspectRatio != null) {
            spinnerPlayerAspectRatio.setAdapter(aspectAdapter);
            int curRatio = prefs.getVideoScreenSize();
            if (curRatio >= 0 && curRatio < aspectRatios.length) {
                spinnerPlayerAspectRatio.setSelection(curRatio);
            }
            spinnerPlayerAspectRatio.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View v, int position, long id) {
                    if (v instanceof TextView) ((TextView) v).setTextColor(getColor(R.color.text_primary));
                    prefs.setVideoScreenSize(position);
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }

        // 2. Resolution Setup
        String[] resolutions = {"Auto (Adaptive)", "1080p Full HD", "720p HD", "480p SD", "360p Low"};
        ArrayAdapter<String> resAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, resolutions);
        if (spinnerPlayerResolution != null) {
            spinnerPlayerResolution.setAdapter(resAdapter);
            String curRes = prefs.getVideoResolution();
            int curResIdx = 0;
            for (int i = 0; i < resolutions.length; i++) {
                if (resolutions[i].equalsIgnoreCase(curRes)) { curResIdx = i; break; }
            }
            spinnerPlayerResolution.setSelection(curResIdx);
            spinnerPlayerResolution.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View v, int position, long id) {
                    if (v instanceof TextView) ((TextView) v).setTextColor(getColor(R.color.text_primary));
                    prefs.setVideoResolution(resolutions[position]);
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }

        // 3. Hardware Decoder Setup
        if (switchHwDecoder != null) {
            switchHwDecoder.setChecked(prefs.isHardwareAccelerationEnabled());
            switchHwDecoder.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.setHardwareAccelerationEnabled(isChecked);
                Toast.makeText(this, isChecked ? "HW Acceleration Enabled" : "SW Decoding Enabled", Toast.LENGTH_SHORT).show();
            });
        }

        // 4. Buffer Setup
        String[] buffers = {"Fast Start (1 sec)", "Standard (3 sec)", "Smooth Playback (5 sec)", "Large Buffer (10 sec)"};
        ArrayAdapter<String> bufferAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, buffers);
        if (spinnerPlayerBuffer != null) {
            spinnerPlayerBuffer.setAdapter(bufferAdapter);
            String curBuf = prefs.getBufferSettings();
            int curBufIdx = 1;
            for (int i = 0; i < buffers.length; i++) {
                if (buffers[i].equalsIgnoreCase(curBuf)) { curBufIdx = i; break; }
            }
            spinnerPlayerBuffer.setSelection(curBufIdx);
            spinnerPlayerBuffer.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View v, int position, long id) {
                    if (v instanceof TextView) ((TextView) v).setTextColor(getColor(R.color.text_primary));
                    prefs.setBufferSettings(buffers[position]);
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }
    }

    private void setupTvView(View view) {
        View cardBootPlayer = view.findViewById(R.id.cardBootPlayer);
        SwitchCompat switchBootPlayer = view.findViewById(R.id.switchBootPlayer);
        SwitchCompat switchAutoSync = view.findViewById(R.id.switchAutoSync);
        TextView txtLastSyncTime = view.findViewById(R.id.txtLastSyncTime);
        Button btnForceSync = view.findViewById(R.id.btnForceSync);

        if (switchBootPlayer != null) {
            switchBootPlayer.setChecked(prefs.isBootPlayerEnabled());
            switchBootPlayer.setFocusable(false);
            switchBootPlayer.setClickable(false);

            if (cardBootPlayer != null) {
                cardBootPlayer.setFocusable(true);
                UIUtils.applyFocusAnimation(cardBootPlayer, 1.03f, 4f);
                cardBootPlayer.setOnClickListener(v -> {
                    boolean newState = !prefs.isBootPlayerEnabled();
                    prefs.setBootPlayerEnabled(newState);
                    switchBootPlayer.setChecked(newState);
                    Toast.makeText(this, "Boot Player Mode " + (newState ? "ENABLED" : "DISABLED"), Toast.LENGTH_SHORT).show();
                });
            }
        }

        if (switchAutoSync != null) {
            switchAutoSync.setChecked(prefs.isAutoSyncEnabled());
            switchAutoSync.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.setAutoSyncEnabled(isChecked);
                Toast.makeText(this, "Auto Sync " + (isChecked ? "ENABLED" : "DISABLED"), Toast.LENGTH_SHORT).show();
            });
        }

        if (txtLastSyncTime != null) {
            txtLastSyncTime.setText(prefs.getLastSyncTime());
        }

        if (btnForceSync != null) {
            UIUtils.applyFocusAnimation(btnForceSync, 1.06f, 8f);
            btnForceSync.setOnClickListener(v -> {
                btnForceSync.setEnabled(false);
                btnForceSync.setText("Syncing...");
                ApiClient.getInstance(this).syncCategoriesAndChannels(new ApiClient.ApiCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean result) {
                        btnForceSync.setEnabled(true);
                        btnForceSync.setText("Force Sync Now");
                        if (txtLastSyncTime != null) {
                            txtLastSyncTime.setText(prefs.getLastSyncTime());
                        }
                        Toast.makeText(SettingsActivity.this, "Server sync completed!", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        btnForceSync.setEnabled(true);
                        btnForceSync.setText("Force Sync Now");
                        Toast.makeText(SettingsActivity.this, "Sync error: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }
    }

    private void setupSystemView(View view) {
        View btnAppUpdate = view.findViewById(R.id.btnAppUpdate);
        View btnAppInfo = view.findViewById(R.id.btnAppInfo);
        View btnDevInfo = view.findViewById(R.id.btnDevInfo);
        View btnReportProblem = view.findViewById(R.id.btnReportProblem);

        btnAppUpdate.setOnClickListener(v -> {
            new com.ottking.devcode.utils.UpdateManager(this).checkAndUpdate(true);
        });

        btnAppInfo.setOnClickListener(v -> {
            String vName = com.ottking.devcode.BuildConfig.VERSION_NAME;
            int vCode = com.ottking.devcode.BuildConfig.VERSION_CODE;
            new CustomDialog.Builder(this)
                    .setTitle(getString(R.string.title_app_info))
                    .setMessage(com.ottking.devcode.utils.Dev.getAppInfo(vName, vCode))
                    .setWidthPercent(0.95f)
                    .setPositiveButton(getString(R.string.btn_close), dialog -> dialog.dismiss())
                    .show();
        });

        btnDevInfo.setOnClickListener(v -> {
            new CustomDialog.Builder(this)
                    .setTitle(getString(R.string.title_dev_info))
                    .setMessage(com.ottking.devcode.utils.Dev.getDevInfo(this))
                    .setWidthPercent(0.95f)
                    .setPositiveButton(getString(R.string.btn_close), dialog -> dialog.dismiss())
                    .show();
        });

        btnReportProblem.setOnClickListener(v -> showReportDialog());
    }

    private void showReportDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(10, 10, 10, 10);

        TextView label1 = new TextView(this);
        label1.setText("Select Issue Category:");
        label1.setTextColor(getColor(R.color.white));
        layout.addView(label1);

        RadioGroup radioGroup = new RadioGroup(this);
        radioGroup.setOrientation(RadioGroup.VERTICAL);

        RadioButton rb1 = new RadioButton(this);
        rb1.setText("Video Stuttering / Buffering");
        rb1.setTextColor(getColor(R.color.text_secondary));

        RadioButton rb2 = new RadioButton(this);
        rb2.setText("Channel Offline / Black Screen");
        rb2.setTextColor(getColor(R.color.text_secondary));

        RadioButton rb3 = new RadioButton(this);
        rb3.setText("Audio Out of Sync");
        rb3.setTextColor(getColor(R.color.text_secondary));

        RadioButton rb4 = new RadioButton(this);
        rb4.setText("Account / Subscription Issue");
        rb4.setTextColor(getColor(R.color.text_secondary));

        radioGroup.addView(rb1);
        radioGroup.addView(rb2);
        radioGroup.addView(rb3);
        radioGroup.addView(rb4);
        rb1.setChecked(true);
        layout.addView(radioGroup);

        TextView label2 = new TextView(this);
        label2.setText("Detailed Description:");
        label2.setTextColor(getColor(R.color.white));
        label2.setPadding(0, 20, 0, 10);
        layout.addView(label2);

        EditText edtDescription = new EditText(this);
        edtDescription.setHint("Write details about the issue here...");
        edtDescription.setTextColor(getColor(R.color.white));
        edtDescription.setHintTextColor(getColor(R.color.text_secondary));
        edtDescription.setBackgroundResource(R.drawable.bg_card_normal);
        edtDescription.setPadding(20, 20, 20, 20);
        layout.addView(edtDescription);

        new CustomDialog.Builder(this)
                .setTitle(getString(R.string.title_report_problem))
                .setView(layout)
                .setWidthPercent(0.95f)
                .setPositiveButton(getString(R.string.btn_submit_report), dialog -> {
                    int selectedId = radioGroup.getCheckedRadioButtonId();
                    RadioButton selectedRb = radioGroup.findViewById(selectedId);
                    String category = (selectedRb != null) ? selectedRb.getText().toString() : "General Issue";
                    String desc = edtDescription.getText().toString().trim();

                    dialog.dismiss();

                    ApiClient.getInstance(this).submitReport(category, desc, new ApiClient.ApiCallback<String>() {
                        @Override
                        public void onSuccess(String result) {
                            Toast.makeText(SettingsActivity.this, result, Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            Toast.makeText(SettingsActivity.this, "Failed to submit report: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", dialog -> dialog.dismiss())
                .show();
    }

    public void focusContentSection() {
        if (contentContainer == null) return;
        contentContainer.post(() -> {
            View focusable = findFirstFocusable(contentContainer);
            if (focusable != null) {
                focusable.setFocusable(true);
                focusable.setFocusableInTouchMode(true);
                focusable.post(() -> focusable.requestFocus());
            }
        });
    }

    private View findFirstFocusable(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE || !view.isEnabled()) {
            return null;
        }

        if (view instanceof EditText
                || view instanceof Button
                || view instanceof SwitchCompat
                || view instanceof RadioButton
                || view instanceof Spinner
                || view instanceof RadioGroup
                || view instanceof android.widget.SeekBar) {
            return view;
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                View target = findFirstFocusable(child);
                if (target != null) {
                    return target;
                }
            }
        }
        return null;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            return FocusManager.getInstance().handleBackPress(this, SCREEN_KEY, this::handleSettingsBackPressInternal, null);
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
            View currentFocus = getCurrentFocus();
            if (currentFocus != null && isViewInContentContainer(currentFocus)) {
                View nextFocus = currentFocus.focusSearch(View.FOCUS_LEFT);
                if (nextFocus == null || !isViewInContentContainer(nextFocus)) {
                    focusNavSection();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean isViewInContentContainer(View view) {
        if (view == null || contentContainer == null) return false;
        View parent = view;
        while (parent != null) {
            if (parent == contentContainer) {
                return true;
            }
            if (parent.getParent() instanceof View) {
                parent = (View) parent.getParent();
            } else {
                break;
            }
        }
        return false;
    }

    public void focusNavSection() {
        RecyclerView recyclerSettingsNav = findViewById(R.id.recyclerSettingsNav);
        if (recyclerSettingsNav != null && navAdapter != null) {
            int pos = navAdapter.getSelectedPosition();
            recyclerSettingsNav.scrollToPosition(pos);
            RecyclerView.ViewHolder vh = recyclerSettingsNav.findViewHolderForAdapterPosition(pos);
            if (vh != null && vh.itemView != null) {
                vh.itemView.setFocusable(true);
                vh.itemView.setFocusableInTouchMode(true);
                vh.itemView.requestFocus();
            } else {
                recyclerSettingsNav.post(() -> {
                    RecyclerView.ViewHolder vh2 = recyclerSettingsNav.findViewHolderForAdapterPosition(pos);
                    if (vh2 != null && vh2.itemView != null) {
                        vh2.itemView.setFocusable(true);
                        vh2.itemView.setFocusableInTouchMode(true);
                        vh2.itemView.requestFocus();
                    }
                });
            }
        }
    }

    private void restoreFocus() {
        View current = getCurrentFocus();
        if (current != null && current.getId() != View.NO_ID && current != findViewById(android.R.id.content)) {
            return;
        }
        if (!FocusManager.getInstance().restoreFocus(this, SCREEN_KEY, null)) {
            focusNavSection();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        UIUtils.hideSystemUI(this);
        restoreFocus();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            UIUtils.hideSystemUI(this);
            restoreFocus();
        }
    }
}
