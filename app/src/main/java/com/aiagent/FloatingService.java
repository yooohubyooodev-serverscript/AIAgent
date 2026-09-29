package com.aiagent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;

public class FloatingService extends Service {

    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_DATA = "resultData";

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private Handler mainHandler;
    private boolean isCapturing = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mainHandler = new Handler(Looper.getMainLooper());
        startForegroundNotification();

        if (intent != null) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            if (resultCode != 0 && data != null) {
                setupMediaProjection(resultCode, data);
            }
        }

        showFloatingWindow();
        return START_STICKY;
    }

    private void startForegroundNotification() {
        String channelId = "ai_agent_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "AI Agent", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, channelId);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
                .setContentTitle("AI Agent กำลังทำงาน")
                .setContentText("แตะ bubble เพื่อจับภาพ / หยุด")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);
    }

    private void setupMediaProjection(int resultCode, Intent data) {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpm.getMediaProjection(resultCode, data);

        if (mediaProjection == null) {
            Toast.makeText(this, "ไม่สามารถเริ่ม MediaProjection ได้", Toast.LENGTH_SHORT).show();
            return;
        }

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        int screenDensity = metrics.densityDpi;

        // ลดความละเอียดเล็กน้อยเพื่อประหยัด memory
        int captureW = Math.min(screenWidth, 720);
        int captureH = (int) (screenHeight * (captureW / (float) screenWidth));

        imageReader = ImageReader.newInstance(captureW, captureH, PixelFormat.RGBA_8888, 2);

        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                releaseCapture();
            }
        }, mainHandler);

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "AIAgentCapture",
                captureW, captureH, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, mainHandler);
    }

    /** จับภาพหน้าจอปัจจุบันเป็น Bitmap */
    public Bitmap captureScreen() {
        if (imageReader == null || isCapturing) return null;
        isCapturing = true;
        try {
            Image image = imageReader.acquireLatestImage();
            if (image == null) return null;

            int width = image.getWidth();
            int height = image.getHeight();
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * width;

            Bitmap bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            image.close();

            if (rowPadding > 0) {
                Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height);
                bitmap.recycle();
                return cropped;
            }
            return bitmap;
        } catch (Exception e) {
            return null;
        } finally {
            isCapturing = false;
        }
    }

    private void showFloatingWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setBackgroundColor(0xCC1565C0);
        layout.setPadding(20, 12, 20, 12);

        TextView tvAi = new TextView(this);
        tvAi.setText("  AI ●  ");
        tvAi.setTextColor(0xFFFFFFFF);
        tvAi.setTextSize(15);
        tvAi.setPadding(8, 4, 8, 4);

        TextView tvCapture = new TextView(this);
        tvCapture.setText("  📷  ");
        tvCapture.setTextColor(0xFFFFFFFF);
        tvCapture.setTextSize(15);
        tvCapture.setPadding(8, 4, 8, 4);

        TextView tvClose = new TextView(this);
        tvClose.setText("  ×  ");
        tvClose.setTextColor(0xFFFFCDD2);
        tvClose.setTextSize(15);
        tvClose.setPadding(8, 4, 8, 4);

        layout.addView(tvAi);
        layout.addView(tvCapture);
        layout.addView(tvClose);

        floatingView = layout;

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 50;
        params.y = 200;

        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                            moved = true;
                        }
                        params.x = initialX + dx;
                        params.y = initialY + dy;
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            float localX = event.getX();
                            int w = floatingView.getWidth();
                            if (localX < w * 0.4f) {
                                onAiButtonClick();
                            } else if (localX < w * 0.7f) {
                                onCaptureButtonClick();
                            } else {
                                stopSelf();
                                Toast.makeText(FloatingService.this, "หยุด AI Agent แล้ว", Toast.LENGTH_SHORT).show();
                            }
                        }
                        return true;
                }
                return false;
            }
        });

        try {
            windowManager.addView(floatingView, params);
        } catch (Exception e) {
            Toast.makeText(this, "ไม่สามารถแสดง Floating UI ได้: " + e.getMessage(), Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void onAiButtonClick() {
        String status = mediaProjection != null ? "พร้อมจับภาพ" : "ยังไม่มี MediaProjection";
        AIAccessibilityService svc = AIAccessibilityService.getInstance();
        String acc = (svc != null) ? "Accessibility เชื่อมต่อแล้ว" : "Accessibility ยังไม่พร้อม";
        Toast.makeText(this, "AI Agent\n" + status + "\n" + acc, Toast.LENGTH_LONG).show();
    }

    private void onCaptureButtonClick() {
        if (mediaProjection == null || imageReader == null) {
            Toast.makeText(this, "ยังไม่พร้อมจับภาพ (ต้องอนุญาตบันทึกหน้าจอ)", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "กำลังจับภาพ...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            Bitmap bmp = captureScreen();
            mainHandler.post(() -> {
                if (bmp != null) {
                    Toast.makeText(FloatingService.this,
                            "จับภาพสำเร็จ " + bmp.getWidth() + "x" + bmp.getHeight(),
                            Toast.LENGTH_SHORT).show();
                    // อนาคต: ส่ง bitmap ไป AI API แล้วใช้ Accessibility ทำ action
                    bmp.recycle();
                } else {
                    Toast.makeText(FloatingService.this, "จับภาพไม่สำเร็จ ลองอีกครั้ง", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void releaseCapture() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseCapture();
        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception ignored) {}
        }
    }
}
