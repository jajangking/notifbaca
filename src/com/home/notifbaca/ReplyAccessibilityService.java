package com.home.notifbaca;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

public class ReplyAccessibilityService extends AccessibilityService {
    private static final String TAG = "NotifBaca";
    private static volatile ReplyAccessibilityService instance;

    public static boolean isOnline() {
        return instance != null;
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        Log.i(TAG, "a11y connected (balas suara siap)");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean typeAndSend(String text, long waitMs) {
        final ReplyAccessibilityService svc = instance;
        if (svc == null || text == null || text.trim().isEmpty()) return false;
        long deadline = System.currentTimeMillis() + Math.max(1500, waitMs);
        boolean sent = false;
        while (!sent && System.currentTimeMillis() < deadline) {
            AccessibilityNodeInfo root = null;
            try {
                root = svc.getRootInActiveWindow();
            } catch (Exception e) {
                Log.w(TAG, "root aktif gagal: " + e);
            }
            if (root != null) {
                try {
                    List<AccessibilityNodeInfo> inputs = collectByClass(root, "android.widget.EditText");
                    if (!inputs.isEmpty()) {
                        AccessibilityNodeInfo input = inputs.get(inputs.size() - 1);
                        typeText(input, text);
                        input.recycle();
                        AccessibilityNodeInfo send = findSend(root);
                        if (send != null && send.isClickable()) {
                            send.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            send.recycle();
                            sent = true;
                            Log.i(TAG, "balasan terkirim lewat a11y");
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "type/send gagal: " + e);
                }
                root.recycle();
            }
            if (!sent) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
        return sent;
    }

    private static void typeText(AccessibilityNodeInfo input, String text) {
        input.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private static List<AccessibilityNodeInfo> collectByClass(AccessibilityNodeInfo node, String cls) {
        List<AccessibilityNodeInfo> out = new ArrayList<>();
        dfsClass(node, cls, out);
        return out;
    }

    private static void dfsClass(AccessibilityNodeInfo node, String cls, List<AccessibilityNodeInfo> out) {
        if (node == null) return;
        CharSequence c = node.getClassName();
        if (c != null && cls.equals(c.toString())) {
            out.add(AccessibilityNodeInfo.obtain(node));
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            dfsClass(child, cls, out);
            child.recycle();
        }
    }

    private static AccessibilityNodeInfo findSend(AccessibilityNodeInfo node) {
        if (node == null) return null;
        String text = nodeText(node);
        String cd = nodeCd(node);
        String vid = node.getViewIdResourceName();
        boolean match = (text != null && (text.contains("kirim") || text.equalsIgnoreCase("send")))
                || (cd != null && (cd.contains("kirim") || cd.toLowerCase().contains("send")))
                || (vid != null && vid.contains("send"));
        if (match && node.isClickable()) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo r = findSend(child);
            child.recycle();
            if (r != null) return r;
        }
        return null;
    }

    private static String nodeText(AccessibilityNodeInfo node) {
        CharSequence t = node.getText();
        return t == null ? null : t.toString();
    }

    private static String nodeCd(AccessibilityNodeInfo node) {
        CharSequence t = node.getContentDescription();
        return t == null ? null : t.toString();
    }
}