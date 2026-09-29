package com.aiagent;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private static final int REQUEST_OVERLAY = 1002;

    private TextView statusAccessibility, statusOverlay, statusScreen, statusBackground, txtReady;
    private Button btnStart, btnCheck, btnAccessibility, btnOverlay, btnScreen, btnBackground;

    private boolean hasAccessibility = false;
    private boolean hasOverlay = false;
    private boolean hasScreen = false;
    private boolean hasBackground = false;

    // เก็บ result จาก MediaProjection เพื่อส่งต่อให้ FloatingService
    private Intent mediaProjectionData;
    private int mediaProjectionResultCode = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusAccessibility = findViewById(R.id.status_accessibility);
        statusOverlay = findViewById(R.id.status_overlay);
        statusScreen = findViewById(R.id.status_screen);
        statusBackground = findViewById(R.id.status_background);
        txtReady = findViewById(R.id.txt_ready);

        btnAccessibility = findViewById(R.id.btn_accessibility);
        btnOverlay = findViewById(R.id.btn_overlay);
        btnScreen = findViewById(R.id.btn_screen);
        btnBackground = findViewById(R.id.btn_background);
        btnCheck = findViewById(R.id.btn_check);
        btnStart = findViewById(R.id.btn_start);

        btnAccessibility.setOnClickListener(v -> openAccessibilitySettings());
        btnOverlay.setOnClickListener(v -> openOverlaySettings());
        btnScreen.setOnClickListener(v -> requestScreenCapture());
        btnBackground.setOnClickListener(v -> openBatterySettings());
        btnCheck.setOnClickListener(v -> checkPermissions());
        btnStart.setOnClickListener(v -> startAgent());

        checkPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
    }

    private void checkPermissions() {
        hasAccessibility = isAccessibilityEnabled();
        updateStatus(statusAccessibility, hasAccessibility);

        hasOverlay = Settings.canDrawOverlays(this);
        updateStatus(statusOverlay, hasOverlay);

        updateStatus(statusScreen, hasScreen);

        hasBackground = isIgnoringBatteryOptimizations();
        updateStatus(statusBackground, hasBackground);

        boolean allReady = hasAccessibility && hasOverlay && hasScreen && hasBackground;
        if (allReady) {
            txtReady.setText("✓ ระบบพร้อม");
            txtReady.setTextColor(0xFF4CAF50);
            btnStart.setEnabled(true);
            btnStart.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50));
        } else {
            txtReady.setText("○ ระบบยังไม่พร้อม");
            txtReady.setTextColor(0xFFFF9800);
            btnStart.setEnabled(false);
            btnStart.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF555555));
        }
    }

    private void updateStatus(TextView tv, boolean ok) {
        if (ok) {
            tv.setText("✓");
            tv.setTextColor(0xFF4CAF50);
        } else {
            tv.setText("○");
            tv.setTextColor(0xFFFF9800);
        }
    }

    private boolean isAccessibilityEnabled() {
        String service = getPackageName() + "/" + AIAccessibilityService.class.getCanonicalName();
        try {
            int enabled = Settings.Secure.getInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
            if (enabled == 1) {
                String setting = Settings.Secure.getString(getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if (setting != null) {
                    return setting.contains(service) || setting.contains(getPackageName());
                }
            }
        } catch (Settings.SettingNotFoundException e) {
            // ignore
        }
        return false;
    }

    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        return true;
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "เปิด AI Agent Accessibility แล้วกดกลับมา", Toast.LENGTH_LONG).show();
    }

    private void openOverlaySettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQUEST_OVERLAY);
    }

    private void requestScreenCapture() {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
    }

    private void openBatterySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            try {
                startActivity(intent);
            } catch (Exception e) {
                Intent fallback = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(fallback);
            }
        } else {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            startActivity(intent);
        }
        Toast.makeText(this, "อนุญาตให้ทำงานเบื้องหลังได้", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                hasScreen = true;
                mediaProjectionResultCode = resultCode;
                mediaProjectionData = data;
                Toast.makeText(this, "อนุญาตบันทึกหน้าจอแล้ว", Toast.LENGTH_SHORT).show();
            } else {
                hasScreen = false;
                mediaProjectionData = null;
                Toast.makeText(this, "ยังไม่ได้อนุญาตบันทึกหน้าจอ", Toast.LENGTH_SHORT).show();
            }
            checkPermissions();
        } else if (requestCode == REQUEST_OVERLAY) {
            checkPermissions();
        }
    }

    private void startAgent() {
        if (!hasOverlay) {
            Toast.makeText(this, "ต้องเปิดสิทธิ์แสดงทับแอปอื่นก่อน", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasScreen || mediaProjectionData == null) {
            Toast.makeText(this, "ต้องอนุญาตบันทึกหน้าจอก่อน", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasAccessibility) {
            Toast.makeText(this, "ต้องเปิด Accessibility ก่อน", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, FloatingService.class);
        intent.putExtra(FloatingService.EXTRA_RESULT_CODE, mediaProjectionResultCode);
        intent.putExtra(FloatingService.EXTRA_RESULT_DATA, mediaProjectionData);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        Toast.makeText(this, "เริ่ม AI Agent แล้ว", Toast.LENGTH_SHORT).show();
    }
}
