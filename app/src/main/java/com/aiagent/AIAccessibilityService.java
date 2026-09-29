package com.aiagent;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * Accessibility Service สำหรับ AI Agent
 * - มองเห็น hierarchy ของหน้าจอ
 * - ทำ gesture (แตะ, ลาก, ปัด)
 * - ค้นหา element จากข้อความ / contentDescription / viewId
 */
public class AIAccessibilityService extends AccessibilityService {

    private static final String TAG = "AIAccessibility";
    private static AIAccessibilityService instance;

    public static AIAccessibilityService getInstance() {
        return instance;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkg = event.getPackageName();
            CharSequence cls = event.getClassName();
            Log.d(TAG, "Window: " + pkg + " / " + cls);
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "AI Accessibility Service connected");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    // ===================== Gesture helpers =====================

    /** แตะที่พิกัด (x, y) บนหน้าจอ */
    public boolean tap(float x, float y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 50);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        return dispatchGesture(builder.build(), null, null);
    }

    /** ลากจาก (x1,y1) ไป (x2,y2) ใช้เวลา durationMs */
    public boolean swipe(float x1, float y1, float x2, float y2, long durationMs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, Math.max(100, durationMs));
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        return dispatchGesture(builder.build(), null, null);
    }

    /** ปัดขึ้น (scroll down content) */
    public boolean swipeUp(float centerX, float startY, float endY) {
        return swipe(centerX, startY, centerX, endY, 300);
    }

    /** ปัดลง */
    public boolean swipeDown(float centerX, float startY, float endY) {
        return swipe(centerX, startY, centerX, endY, 300);
    }

    /** กดปุ่ม Back */
    public boolean performBack() {
        return performGlobalAction(GLOBAL_ACTION_BACK);
    }

    /** กดปุ่ม Home */
    public boolean performHome() {
        return performGlobalAction(GLOBAL_ACTION_HOME);
    }

    /** เปิด Recent apps */
    public boolean performRecents() {
        return performGlobalAction(GLOBAL_ACTION_RECENTS);
    }

    // ===================== Node search helpers =====================

    /** ค้นหา node ที่มีข้อความตรงหรือมีคำที่ระบุ */
    public AccessibilityNodeInfo findNodeByText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(text);
        if (list != null && !list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }

    /** ค้นหา node จาก viewId (เช่น "com.example:id/btn_ok") */
    public AccessibilityNodeInfo findNodeByViewId(String viewId) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByViewId(viewId);
        if (list != null && !list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }

    /** คลิก node ที่หาเจอ */
    public boolean clickNode(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isClickable()) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null && parent.isClickable()) {
            boolean ok = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            parent.recycle();
            return ok;
        }
        android.graphics.Rect bounds = new android.graphics.Rect();
        node.getBoundsInScreen(bounds);
        if (!bounds.isEmpty()) {
            return tap(bounds.centerX(), bounds.centerY());
        }
        return false;
    }

    /** ค้นหาและคลิกตามข้อความ */
    public boolean clickByText(String text) {
        AccessibilityNodeInfo node = findNodeByText(text);
        if (node == null) return false;
        boolean ok = clickNode(node);
        node.recycle();
        return ok;
    }

    /** ดึงข้อความทั้งหมดที่มองเห็นบนหน้าจอ (สำหรับส่งให้ AI) */
    public String dumpVisibleText() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "";
        StringBuilder sb = new StringBuilder();
        collectText(root, sb, 0);
        root.recycle();
        return sb.toString();
    }

    private void collectText(AccessibilityNodeInfo node, StringBuilder sb, int depth) {
        if (node == null || depth > 30) return;
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        if (text != null && text.length() > 0) {
            sb.append(text).append("\n");
        } else if (desc != null && desc.length() > 0) {
            sb.append(desc).append("\n");
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectText(child, sb, depth + 1);
                child.recycle();
            }
        }
    }
}
