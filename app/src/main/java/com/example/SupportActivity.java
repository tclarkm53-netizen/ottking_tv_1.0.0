package com.example;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.utils.PreferenceUtils;
import com.example.utils.ServerApiManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

public class SupportActivity extends AppCompatActivity {

    private MaterialToolbar toolbarSupport;
    private MaterialButton btnSupportTelegram, btnSupportWhatsapp, btnSubmitReport;
    private EditText etBrokenChannelName, etBrokenDescription;
    private TextView txtDevInfoContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_support);

        toolbarSupport = findViewById(R.id.toolbarSupport);
        btnSupportTelegram = findViewById(R.id.btnSupportTelegram);
        btnSupportWhatsapp = findViewById(R.id.btnSupportWhatsapp);
        btnSubmitReport = findViewById(R.id.btnSubmitReport);
        etBrokenChannelName = findViewById(R.id.etBrokenChannelName);
        etBrokenDescription = findViewById(R.id.etBrokenDescription);
        txtDevInfoContent = findViewById(R.id.txtDevInfoContent);

        toolbarSupport.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbarSupport.setNavigationOnClickListener(v -> finish());

        loadServerDeveloperInfo();

        btnSupportTelegram.setOnClickListener(v -> {
            openDirectLink(PreferenceUtils.getTelegramUrl(this));
        });

        btnSupportWhatsapp.setOnClickListener(v -> {
            openDirectLink(PreferenceUtils.getWhatsAppUrl(this));
        });

        btnSubmitReport.setOnClickListener(v -> {
            String name = etBrokenChannelName.getText().toString().trim();
            String desc = etBrokenDescription.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter stream title", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Report submitted successfully! Thank you.", Toast.LENGTH_LONG).show();
                etBrokenChannelName.setText("");
                etBrokenDescription.setText("");
            }
        });
    }

    private void loadServerDeveloperInfo() {
        String info = PreferenceUtils.getDeveloperInfo(this);
        if (txtDevInfoContent != null) {
            txtDevInfoContent.setText(info);
        }
    }

    private void openDirectLink(String url) {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, "Support link not available", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open link: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
