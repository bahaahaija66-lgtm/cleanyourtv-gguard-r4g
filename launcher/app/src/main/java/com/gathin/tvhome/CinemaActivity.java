package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Ordinary TV application: TMDB discovery and app shortcuts, never an Android HOME handler. */
public final class CinemaActivity extends Activity {
    static final long ROTATION_MS = 25000;
    private static final String[] APPS = {"YouTube", "Netflix", "IPTV Smarters", "Shahid", "OSN", "Apple TV"};
    private static final String[][] PACKAGES = {
        {"com.google.android.youtube.tv"}, {"com.netflix.ninja"},
        {"com.nst.iptvsmarterstvbox", "com.nst.smartersplayer"}, {"net.mbc.shahidTV", "net.mbc.shahid"},
        {"com.osn.go", "com.osn.osnplus"}, {"com.apple.atve.androidtv.appletv"}
    };
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService artworkWorker = Executors.newSingleThreadExecutor();
    private List<CinemaRepository.Film> films = CinemaRepository.placeholders();
    private CinemaRepository.Film displayedFilm = films.get(0);
    private CinemaRepository repository;
    private CinemaCredentials credentials;
    private ImageView backdrop, incoming, poster;
    private TextView title, metadata, description, position, connection, posterCaption;
    private Button artworkButton;
    private String token = "";
    private String serviceStatus = "Connect TMDB for movie artwork";
    private int feature, generation;
    private volatile boolean foreground;
    private boolean imageNetworkReady, refreshing, refreshAgain;
    private final Runnable rotate = new Runnable() {
        @Override public void run() {
            if (!foreground) return;
            feature = (feature + 1) % films.size();
            renderFeature();
            handler.postDelayed(this, ROTATION_MS);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) feature = Math.floorMod(state.getInt("feature", 0), films.size());
        repository = new CinemaRepository(new File(getFilesDir(), "tmdb-artwork"));
        credentials = new CinemaCredentials(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        buildScreen();
    }

    private void buildScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        root.setBackgroundColor(0xff07090e);
        root.setClipChildren(false);
        backdrop = image(ImageView.ScaleType.CENTER_CROP);
        incoming = image(ImageView.ScaleType.CENTER_CROP);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        root.addView(incoming, new FrameLayout.LayoutParams(-1, -1));
        incoming.setAlpha(0);
        root.addView(new Scrim(), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("CINEMA", 19, Color.WHITE);
        brand.setLetterSpacing(.22f);
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        artworkButton = action("Artwork & TMDB", this::showConnection);
        Button inputs = action("Inputs", () -> open(new Intent("com.android.tv.action.VIEW_INPUTS")));
        Button wifi = action("Wi-Fi", () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        Button settings = action("Settings", () -> open(new Intent(Settings.ACTION_SETTINGS)));
        header.addView(artworkButton);
        header.addView(inputs);
        header.addView(wifi);
        header.addView(settings);
        FrameLayout.LayoutParams head = new FrameLayout.LayoutParams(-1, dp(42), Gravity.TOP);
        head.setMargins(dp(44), dp(20), dp(36), 0);
        root.addView(header, head);

        LinearLayout information = new LinearLayout(this);
        information.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = text("THE CINEMA COLLECTION", 10, 0xffd0d6df);
        eyebrow.setLetterSpacing(.23f);
        information.addView(eyebrow);
        title = text("", 47, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setMaxLines(2);
        title.setLineSpacing(0, .94f);
        information.addView(title);
        metadata = text("", 12, 0xffe0e2e7);
        information.addView(metadata);
        description = text("", 14, 0xffc9ced7);
        description.setMaxLines(3);
        description.setLineSpacing(dp(3), 1);
        LinearLayout.LayoutParams synopsis = new LinearLayout.LayoutParams(-1, -2);
        synopsis.topMargin = dp(12);
        information.addView(description, synopsis);
        Button details = action("About this movie  ›", this::showDetails);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(dp(170), dp(36));
        detailParams.topMargin = dp(12);
        information.addView(details, detailParams);
        position = text("", 11, 0xffc1c8d3);
        LinearLayout.LayoutParams dots = new LinearLayout.LayoutParams(-2, -2);
        dots.topMargin = dp(12);
        information.addView(position, dots);
        int widthDp = Math.round(getResources().getDisplayMetrics().widthPixels
            / getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams info = new FrameLayout.LayoutParams(dp(Math.min(510, (int) (widthDp * .58f))), -2,
            Gravity.BOTTOM | Gravity.START);
        info.setMargins(dp(46), dp(82), 0, dp(156));
        root.addView(information, info);

        LinearLayout posterGroup = new LinearLayout(this);
        posterGroup.setOrientation(LinearLayout.VERTICAL);
        posterGroup.setGravity(Gravity.CENTER_HORIZONTAL);
        poster = image(ImageView.ScaleType.CENTER_CROP);
        poster.setVisibility(View.INVISIBLE);
        GradientDrawable posterShape = surface(false, 10);
        poster.setBackground(posterShape);
        poster.setClipToOutline(true);
        posterGroup.addView(poster, new LinearLayout.LayoutParams(dp(100), dp(150)));
        posterCaption = text("", 9, 0xffb6bdc9);
        posterCaption.setGravity(Gravity.CENTER);
        posterGroup.addView(posterCaption);
        FrameLayout.LayoutParams posterParams = new FrameLayout.LayoutParams(dp(106), -2, Gravity.BOTTOM | Gravity.END);
        posterParams.setMargins(0, 0, dp(48), dp(160));
        root.addView(posterGroup, posterParams);

        LinearLayout dock = new LinearLayout(this);
        dock.setPadding(dp(10), dp(8), dp(10), dp(8));
        dock.setGravity(Gravity.CENTER);
        dock.setClipChildren(false);
        dock.setBackground(surface(false, 20));
        for (int i = 0; i < APPS.length; i++) {
            final int index = i;
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setId(View.generateViewId());
            tile.setFocusable(true);
            tile.setClickable(true);
            tile.setContentDescription(APPS[i]);
            tile.setBackground(surface(false, 12));
            ImageView icon = image(ImageView.ScaleType.FIT_CENTER);
            icon.setImageDrawable(appIcon(i));
            tile.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));
            TextView label = text(APPS[i], 10, 0xffecedf1);
            label.setSingleLine(true);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams caption = new LinearLayout.LayoutParams(-1, -2);
            caption.topMargin = dp(5);
            tile.addView(label, caption);
            tile.setOnClickListener(v -> launchApp(index));
            tile.setOnFocusChangeListener((v, focused) -> focus(v, focused, 12));
            LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(0, -1, 1);
            slot.setMargins(dp(5), 0, dp(5), 0);
            dock.addView(tile, slot);
            tile.setNextFocusUpId(details.getId());
            tile.setNextFocusDownId(tile.getId());
        }
        for (int i = 0; i < 6; i++) {
            View tile = dock.getChildAt(i);
            tile.setNextFocusLeftId(dock.getChildAt(Math.max(0, i - 1)).getId());
            tile.setNextFocusRightId(dock.getChildAt(Math.min(5, i + 1)).getId());
        }
        int first = dock.getChildAt(0).getId();
        details.setNextFocusDownId(first);
        details.setNextFocusUpId(artworkButton.getId());
        for (int i = 0; i < header.getChildCount(); i++) header.getChildAt(i).setNextFocusDownId(details.getId());
        FrameLayout.LayoutParams dockParams = new FrameLayout.LayoutParams(-1, dp(94), Gravity.BOTTOM);
        dockParams.setMargins(dp(40), 0, dp(40), dp(33));
        root.addView(dock, dockParams);
        connection = text("", 9, 0xffabb2bf);
        connection.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams footer = new FrameLayout.LayoutParams(-1, dp(24), Gravity.BOTTOM);
        footer.bottomMargin = dp(3);
        root.addView(connection, footer);
        setContentView(root);
        applyCopy(films.get(feature));
        connection.setText("Connect TMDB for movie artwork · Film information only, no playback");
        renderFeature();
        dock.getChildAt(0).requestFocus();
    }

    private void renderFeature() {
        CinemaRepository.Film film = films.get(feature);
        int request = ++generation;
        artworkWorker.execute(() -> loadArtwork(film, request));
    }

    private void applyCopy(CinemaRepository.Film film) {
        displayedFilm = film;
        title.setText(film.title);
        metadata.setText(film.year + "   ·   " + film.genres
            + (film.rating.isEmpty() ? "" : "   ·   ★ " + film.rating));
        description.setText(film.description);
        position.setText((feature == 0 ? "●" : "○") + "    " + (feature == 1 ? "●" : "○")
            + "    " + (feature == 2 ? "●" : "○") + "     " + (feature + 1) + " / " + films.size());
    }

    private void loadArtwork(CinemaRepository.Film film, int request) {
        Bitmap background = null, cover = null;
        try {
            File file = repository.image(film.backdrop, false, imageNetworkReady, System.currentTimeMillis());
            background = decode(file, 1280, 720);
        } catch (IOException ignored) { /* Preserve the usable cached catalog; no invented artwork. */ }
        try {
            File file = repository.image(film.poster, true, imageNetworkReady, System.currentTimeMillis());
            cover = decode(file, 342, 513);
        } catch (IOException ignored) { /* The backdrop can still work without a poster. */ }
        Bitmap loadedBackground = background, loadedPoster = cover;
        handler.post(() -> {
            if (!foreground || request != generation || isFinishing() || isDestroyed()) {
                if (loadedBackground != null) loadedBackground.recycle();
                if (loadedPoster != null) loadedPoster.recycle();
                return;
            }
            if (loadedBackground == null && backdrop.getDrawable() != null) {
                // Keep a real cached scene rather than rotate to an empty screen while offline.
                if (loadedPoster != null) loadedPoster.recycle();
                connection.setText(serviceStatus + " · Showing the last available backdrop");
                return;
            }
            applyCopy(film);
            finishTransition();
            poster.setImageBitmap(loadedPoster);
            poster.setVisibility(loadedPoster == null ? View.INVISIBLE : View.VISIBLE);
            posterCaption.setText(loadedPoster == null ? "" : "TMDB · POSTER");
            if (loadedBackground != null) {
                incoming.setImageBitmap(loadedBackground);
                incoming.setAlpha(ValueAnimator.areAnimatorsEnabled() ? 0 : 1);
                incoming.animate().alpha(1).setDuration(650).withEndAction(() -> {
                    backdrop.setImageBitmap(loadedBackground);
                    incoming.setImageDrawable(null);
                    incoming.setAlpha(0);
                }).start();
            } else {
                backdrop.setImageDrawable(null);
                connection.setText(serviceStatus + " · No saved backdrop available");
            }
        });
    }

    private void refreshCatalog() {
        if (refreshing) { refreshAgain = true; return; }
        refreshAgain = false;
        refreshing = true;
        worker.execute(() -> {
            String savedToken = credentials.read();
            // Show disk data immediately; network timeouts must not delay offline artwork.
            if (!savedToken.isEmpty()) {
                CinemaRepository.Catalog cached = repository.catalog("", System.currentTimeMillis());
                handler.post(() -> {
                    if (!foreground || isDestroyed()) return;
                    token = savedToken;
                    films = cached.films;
                    imageNetworkReady = false;
                    serviceStatus = "Saved collection · connecting to TMDB";
                    connection.setText(serviceStatus + " · Film information only, no playback");
                    renderFeature();
                });
            }
            boolean ready = repository.configure(savedToken);
            CinemaRepository.Catalog catalog = repository.catalog(ready ? savedToken : "", System.currentTimeMillis());
            handler.post(() -> {
                refreshing = false;
                if (!foreground || isDestroyed()) return;
                if (refreshAgain) { refreshAgain = false; refreshCatalog(); return; }
                token = savedToken;
                imageNetworkReady = ready;
                films = catalog.films;
                String status = !savedToken.isEmpty() && !ready
                    ? "Unable to connect to TMDB · check your token or network" : catalog.status;
                serviceStatus = status;
                connection.setText(status + " · Film information only, no playback");
                renderFeature();
                if (ready) {
                    List<CinemaRepository.Film> collection = catalog.films;
                    artworkWorker.execute(() -> {
                        for (CinemaRepository.Film movie : collection) {
                            if (!foreground || Thread.currentThread().isInterrupted()) return;
                            try {
                                repository.image(movie.backdrop, false, true, System.currentTimeMillis());
                                if (!foreground) return;
                                repository.image(movie.poster, true, true, System.currentTimeMillis());
                            } catch (IOException ignored) { /* Existing cached scenes remain usable. */ }
                        }
                    });
                }
            });
        });
    }

    private void showDetails() {
        CinemaRepository.Film film = displayedFilm;
        new AlertDialog.Builder(this).setTitle(film.title + " (" + film.year + ")")
            .setMessage(film.genres + (film.rating.isEmpty() ? "" : "\n" + film.rating) + "\n\n" + film.description
                + "\n\nDiscovery information only. This launcher does not provide a stream, subscription access or playback rights."
                + "\n\n" + (film.fromTmdb ? "Metadata and images from TMDB. Ratings can change; saved data may be older."
                    : "Connect TMDB for provider metadata, current ratings and authentic artwork."))
            .setPositiveButton("Close", null).show();
    }

    private void showConnection() {
        handler.removeCallbacks(rotate);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(10), dp(24), dp(10));
        TextView attribution = text("This product uses the TMDB API but is not endorsed or certified by TMDB."
            + "\n\nUse your own TMDB API Read Access Token for personal, non-commercial use, subject to TMDB's API terms and the rights of image owners."
            + "\n\nThe token is encrypted on this device. It is never included in an APK or sent to the image CDN.", 13, Color.LTGRAY);
        content.addView(attribution);
        EditText input = new EditText(this);
        input.setHint(token.isEmpty() ? "Paste your TMDB API Read Access Token" : "Paste a replacement token, or leave blank to keep it");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xffaeb4c0);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        content.addView(input);
        Button disconnect = action("Disconnect TMDB", () -> worker.execute(() -> {
            boolean saved = credentials.save("");
            handler.post(() -> {
                if (isDestroyed()) return;
                if (saved) { token = ""; refreshCatalog(); Toast.makeText(this, "TMDB disconnected. Saved artwork remains available offline.", Toast.LENGTH_LONG).show(); }
                else Toast.makeText(this, "Unable to remove the saved token", Toast.LENGTH_LONG).show();
            });
        }));
        content.addView(disconnect);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Artwork & TMDB")
            .setView(content).setPositiveButton("Save & refresh", null)
            .setNeutralButton("TMDB terms", (d, which) -> open(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.themoviedb.org/api-terms-of-use"))))
            .setNegativeButton("Close", null).create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String entered = input.getText().toString().trim();
                if (entered.startsWith("Bearer ")) entered = entered.substring(7).trim();
                if (entered.isEmpty()) { dialog.dismiss(); refreshCatalog(); return; }
                if (!entered.matches("[A-Za-z0-9._~-]{20,2048}")) {
                    input.setError("Enter the API Read Access Token from your own TMDB account");
                    return;
                }
                String submitted = entered;
                worker.execute(() -> {
                    boolean saved = credentials.save(submitted);
                    handler.post(() -> {
                        if (isDestroyed()) return;
                        if (saved) { dialog.dismiss(); refreshCatalog(); }
                        else input.setError("Unable to save securely on this device");
                    });
                });
            });
        });
        dialog.setOnDismissListener(d -> {
            if (foreground) { handler.removeCallbacks(rotate); handler.postDelayed(rotate, ROTATION_MS); }
        });
        dialog.show();
    }

    private Drawable appIcon(int index) {
        for (String name : PACKAGES[index]) {
            try { return getPackageManager().getApplicationIcon(name); }
            catch (PackageManager.NameNotFoundException ignored) { /* Try another legitimate package variant. */ }
        }
        return getPackageManager().getDefaultActivityIcon();
    }

    private void launchApp(int index) {
        for (String name : PACKAGES[index]) {
            Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(name);
            if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(name);
            if (launch != null) { open(launch); return; }
        }
        Toast.makeText(this, APPS[index] + " is not installed", Toast.LENGTH_SHORT).show();
    }

    private void open(Intent intent) {
        try { startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Unavailable on this TV. Use Google TV Settings or the original remote.", Toast.LENGTH_LONG).show();
        }
    }

    private Button action(String label, Runnable click) {
        Button button = new Button(this);
        button.setId(View.generateViewId());
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(11);
        button.setTextColor(Color.WHITE);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(surface(false, 20));
        button.setOnClickListener(v -> click.run());
        button.setOnFocusChangeListener((v, focused) -> focus(v, focused, 20));
        return button;
    }

    private void focus(View view, boolean focused, int radius) {
        view.setBackground(surface(focused, radius));
        view.animate().cancel();
        float scale = focused ? 1.06f : 1;
        view.animate().scaleX(scale).scaleY(scale).setDuration(160).start();
    }

    private GradientDrawable surface(boolean focused, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(focused ? 0xe5383e4c : 0xb0181c25);
        shape.setCornerRadius(dp(radius));
        shape.setStroke(dp(focused ? 2 : 1), focused ? Color.WHITE : 0x403f4757);
        return shape;
    }

    private ImageView image(ImageView.ScaleType scale) {
        ImageView image = new ImageView(this);
        image.setScaleType(scale);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return image;
    }

    private TextView text(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return text;
    }

    private static Bitmap decode(File file, int width, int height) {
        if (file == null) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), options);
        if (options.outWidth <= 0 || options.outHeight <= 0 || options.outWidth > 8192 || options.outHeight > 8192) return null;
        options.inSampleSize = 1;
        while (options.outWidth / options.inSampleSize > width * 2
                || options.outHeight / options.inSampleSize > height * 2) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getPath(), options);
    }

    private void finishTransition() {
        incoming.animate().cancel();
        incoming.setImageDrawable(null);
        incoming.setAlpha(0);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        handler.removeCallbacks(rotate);
        handler.postDelayed(rotate, ROTATION_MS);
        refreshCatalog();
    }

    @Override protected void onPause() {
        foreground = false;
        generation++;
        handler.removeCallbacks(rotate);
        finishTransition();
        super.onPause();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        artworkWorker.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("feature", feature);
        super.onSaveInstanceState(out);
    }

    /** Static gradients only. Real movie art is an ImageView underneath, never drawn substitutes. */
    private final class Scrim extends View {
        private final Paint paint = new Paint();
        Scrim() { super(CinemaActivity.this); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth(), height = getHeight();
            paint.setShader(new LinearGradient(0, 0, width, 0,
                new int[]{0xe603050a, 0xa603050a, 0x0803050a}, new float[]{0, .43f, 1}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);
            paint.setShader(new LinearGradient(0, 0, 0, height,
                new int[]{0x9a03050a, 0x0003050a, 0xff07090e}, new float[]{0, .35f, 1}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);
        }
    }
}
