package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Explicit-launch offline cinema. No HOME role or remote interception. */
public final class CinemaActivity extends Activity {
    static final long ROTATION_MS = 25000;
    private static final String[][] FILMS = {
        {"Sintel", "2010  ·  Fantasy / Adventure  ·  Not rated", "A young traveller searches for the dragon she befriended, across a world of mountains and mystery."},
        {"Big Buck Bunny", "2008  ·  Animation / Comedy  ·  Not rated", "A gentle rabbit finds his peaceful afternoon interrupted by three mischievous forest neighbours."},
        {"Tears of Steel", "2012  ·  Science fiction  ·  Not rated", "In a future Amsterdam, a team confronts a robotic threat and the memories that set it in motion."}
    };
    private static final String[] APPS = {"YouTube", "Netflix", "IPTV Smarters", "Shahid", "OSN", "Apple TV"};
    private static final String[] PACKAGES = {"com.google.android.youtube.tv", "com.netflix.ninja", "com.nst.iptvsmarterstvbox", "net.mbc.shahidTV", "com.osn.go", "com.apple.atve.androidtv.appletv"};
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int feature;
    private TextView title, meta, description, page;
    private Landscape art;
    private final Runnable rotate = new Runnable() {
        @Override public void run() { feature = (feature + 1) % FILMS.length; showFeature(true); handler.postDelayed(this, ROTATION_MS); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) feature = Math.floorMod(state.getInt("feature", 0), FILMS.length);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this); root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); root.setBackgroundColor(Color.BLACK);
        art = new Landscape(); root.addView(art, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout screen = new LinearLayout(this); screen.setOrientation(LinearLayout.VERTICAL); screen.setPadding(dp(42), dp(22), dp(42), dp(24)); screen.setClipChildren(false);
        root.addView(screen, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("CLEAN YOUR TV   /   CINEMA", 14, 0xffdddddf); brand.setLetterSpacing(.14f);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button inputs = button("Inputs", () -> open(new Intent("com.android.tv.action.VIEW_INPUTS")));
        Button wifi = button("Wi-Fi", () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        Button settings = button("Settings", () -> open(new Intent(Settings.ACTION_SETTINGS)));
        top.addView(inputs); top.addView(wifi); top.addView(settings); screen.addView(top);
        screen.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        TextView eyebrow = text("THE OPEN MOVIE COLLECTION", 11, 0xffc2cbdb); eyebrow.setLetterSpacing(.18f); screen.addView(eyebrow);
        title = text("", 44, Color.WHITE); title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL)); screen.addView(title);
        meta = text("", 13, 0xffd5d7df); screen.addView(meta);
        description = text("", 15, 0xffc8cbd2); description.setMaxLines(3); description.setLineSpacing(dp(3), 1);
        LinearLayout.LayoutParams copy = new LinearLayout.LayoutParams(dp(450), -2); copy.topMargin = dp(10); screen.addView(description, copy);
        TextView notice = text("Original landscape art · Metadata only · Offline collection", 10, 0xffa0a6b2);
        LinearLayout.LayoutParams note = new LinearLayout.LayoutParams(-2, -2); note.topMargin = dp(9); screen.addView(notice, note);
        page = text("", 11, 0xffc8cbd2); screen.addView(page);
        LinearLayout dock = new LinearLayout(this); dock.setClipChildren(false);
        for (int i = 0; i < APPS.length; i++) {
            final int index = i; Button tile = button(APPS[i], () -> launchApp(index)); tile.setTextSize(14);
            LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(0, dp(64), 1); slot.setMargins(dp(5), dp(16), dp(5), dp(8)); dock.addView(tile, slot);
            tile.setNextFocusDownId(tile.getId()); tile.setNextFocusUpId(inputs.getId());
        }
        for (int i = 0; i < 6; i++) { View tile = dock.getChildAt(i); tile.setNextFocusLeftId(dock.getChildAt(Math.max(0, i - 1)).getId()); tile.setNextFocusRightId(dock.getChildAt(Math.min(5, i + 1)).getId()); }
        screen.addView(dock, new LinearLayout.LayoutParams(-1, -2));
        inputs.setNextFocusDownId(dock.getChildAt(0).getId()); wifi.setNextFocusDownId(dock.getChildAt(0).getId()); settings.setNextFocusDownId(dock.getChildAt(0).getId());
        setContentView(root); showFeature(false); dock.getChildAt(0).requestFocus();
    }
    private void showFeature(boolean animate) {
        title.setText(FILMS[feature][0]); meta.setText(FILMS[feature][1]); description.setText(FILMS[feature][2]); page.setText("0" + (feature + 1) + "  /  03"); art.scene = feature; art.invalidate();
        if (animate && android.animation.ValueAnimator.areAnimatorsEnabled()) { art.animate().cancel(); art.setAlpha(.6f); art.animate().alpha(1).setDuration(650).start(); }
    }
    private TextView text(String s, int size, int color) { TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color); v.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL)); return v; }
    private Button button(String name, Runnable action) {
        Button b = new Button(this); b.setId(View.generateViewId()); b.setText(name); b.setAllCaps(false); b.setTextSize(12); b.setTextColor(Color.WHITE); b.setPadding(dp(10), 0, dp(10), 0); b.setBackground(surface(false)); b.setOnClickListener(v -> action.run());
        b.setOnFocusChangeListener((v, focus) -> { b.setBackground(surface(focus)); b.setTextColor(focus ? Color.BLACK : Color.WHITE); v.animate().cancel(); float scale = focus ? 1.045f : 1; v.animate().scaleX(scale).scaleY(scale).setDuration(150).start(); }); return b;
    }
    private GradientDrawable surface(boolean focus) { GradientDrawable d = new GradientDrawable(); d.setColor(focus ? 0xfff3f4f7 : 0xe51a1d24); d.setCornerRadius(dp(12)); d.setStroke(dp(1), focus ? Color.WHITE : 0xff3b3e47); return d; }
    private void launchApp(int i) { Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(PACKAGES[i]); if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(PACKAGES[i]); if (launch == null) Toast.makeText(this, APPS[i] + " is not installed", Toast.LENGTH_SHORT).show(); else open(launch); }
    private void open(Intent intent) { try { startActivity(intent); } catch (ActivityNotFoundException | SecurityException e) { Toast.makeText(this, "Unavailable. Use Google TV Settings or the original remote.", Toast.LENGTH_LONG).show(); } }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    @Override protected void onResume() { super.onResume(); handler.removeCallbacks(rotate); handler.postDelayed(rotate, ROTATION_MS); }
    @Override protected void onPause() { handler.removeCallbacks(rotate); art.animate().cancel(); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle out) { out.putInt("feature", feature); super.onSaveInstanceState(out); }
    /** Original MIT-licensed vector artwork; intentionally not a film still. */
    private final class Landscape extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); int scene;
        Landscape() { super(CinemaActivity.this); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); float w = getWidth(), h = getHeight(); int[] skies = {0xff41657a, 0xff5d7b58, 0xff586181};
            paint.setShader(new LinearGradient(0, 0, w, h, skies[scene], 0xff090d19, Shader.TileMode.CLAMP)); c.drawRect(0, 0, w, h, paint); paint.setShader(null);
            paint.setColor(0xffc6c2ab); c.drawCircle(w * .77f, h * .22f, h * .075f, paint);
            for (int layer = 0; layer < 3; layer++) { Path ridge = new Path(); ridge.moveTo(0, h); for (int i = 0; i <= 10; i++) ridge.lineTo(i * w / 10, h * (.42f + layer * .14f + (float)Math.sin(i * 2.7 + scene + layer) * .14f)); ridge.lineTo(w, h); ridge.close(); paint.setColor(new int[]{0xff263d48, 0xff182831, 0xff101922}[layer]); c.drawPath(ridge, paint); }
            paint.setShader(new LinearGradient(0, 0, w, 0, new int[]{0xef000000, 0x77000000, 0x10000000}, new float[]{0, .55f, 1}, Shader.TileMode.CLAMP)); c.drawRect(0, 0, w, h, paint);
            paint.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x66000000, 0, 0xff030406}, new float[]{0, .3f, 1}, Shader.TileMode.CLAMP)); c.drawRect(0, 0, w, h, paint); paint.setShader(null);
        }
    }
}
