package com.fonddefilant.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;

import java.io.File;

/**
 * Fond d'écran animé : affiche l'image choisie, plus large que l'écran,
 * et la fait défiler quand on change de page sur le bureau.
 *
 * Deux sources de défilement :
 *  - les "offsets" envoyés par le launcher (Nova, Lawnchair, Niagara, etc.)
 *  - la détection des glissements de doigt (pour Samsung One UI, Pixel Launcher…
 *    qui n'envoient pas de position de page au fond d'écran).
 */
public class ScrollWallpaperService extends WallpaperService {

    public static final String PREFS = "settings";
    public static final String IMAGE_FILE = "wallpaper.img";

    public static final String KEY_AMPLITUDE = "amplitude"; // % de largeur en plus (10..150)
    public static final String KEY_PAGES = "pages";         // nombre de pages (mode gestes)
    public static final String KEY_MODE = "mode";           // 0 auto, 1 launcher, 2 gestes
    public static final String KEY_STAMP = "imageStamp";

    public static final int MODE_AUTO = 0, MODE_LAUNCHER = 1, MODE_GESTURES = 2;

    @Override
    public Engine onCreateEngine() {
        return new ScrollEngine();
    }

    class ScrollEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private SharedPreferences prefs;

        private Bitmap bitmap;
        private int width, height;
        private boolean visible;

        // Offsets venant du launcher
        private float launcherOffset = 0.5f;
        private boolean launcherMoves = false;

        // Mode gestes
        private int page;
        private float current = 0.5f, target = 0.5f;
        private float downX, downY, downOffset;
        private boolean tracking, horizontal;
        private VelocityTracker velocity;
        private int touchSlop, minFling;

        private final Runnable animator = new Runnable() {
            @Override public void run() {
                float diff = target - current;
                if (Math.abs(diff) < 0.0008f) {
                    current = target;
                } else {
                    current += diff * 0.18f;
                    handler.postDelayed(this, 16);
                }
                draw();
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.registerOnSharedPreferenceChangeListener(this);
            ViewConfiguration vc = ViewConfiguration.get(ScrollWallpaperService.this);
            touchSlop = vc.getScaledTouchSlop();
            minFling = vc.getScaledMinimumFlingVelocity() * 4;
            setTouchEventsEnabled(true);
            setOffsetNotificationsEnabled(true);
            int pages = pages();
            page = pages / 2;
            current = target = pageToOffset(page, pages);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            handler.removeCallbacks(animator);
            prefs.unregisterOnSharedPreferenceChangeListener(this);
            if (bitmap != null) { bitmap.recycle(); bitmap = null; }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            super.onSurfaceChanged(holder, format, w, h);
            width = w;
            height = h;
            loadBitmap();
            draw();
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            visible = v;
            if (v) draw(); else handler.removeCallbacks(animator);
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            visible = false;
            handler.removeCallbacks(animator);
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xStep, float yStep,
                                     int xPixels, int yPixels) {
            if (Math.abs(xOffset - launcherOffset) > 0.0001f) launcherMoves = true;
            launcherOffset = xOffset;
            if (useLauncher()) draw();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences p, String key) {
            if (KEY_STAMP.equals(key) || KEY_AMPLITUDE.equals(key)) loadBitmap();
            if (KEY_PAGES.equals(key)) {
                int pages = pages();
                page = Math.min(page, pages - 1);
                current = target = pageToOffset(page, pages);
            }
            draw();
        }

        private int pages() { return Math.max(2, prefs.getInt(KEY_PAGES, 5)); }

        private float pageToOffset(int p, int pages) { return p / (float) (pages - 1); }

        private boolean useLauncher() {
            int mode = prefs.getInt(KEY_MODE, MODE_AUTO);
            if (mode == MODE_LAUNCHER) return true;
            if (mode == MODE_GESTURES) return false;
            return launcherMoves;
        }

        @Override
        public void onTouchEvent(MotionEvent e) {
            if (useLauncher() || width == 0) return;
            int pages = pages();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (velocity == null) velocity = VelocityTracker.obtain(); else velocity.clear();
                    velocity.addMovement(e);
                    downX = e.getX();
                    downY = e.getY();
                    downOffset = current;
                    tracking = true;
                    horizontal = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!tracking) break;
                    if (velocity != null) velocity.addMovement(e);
                    float dx = e.getX() - downX, dy = e.getY() - downY;
                    if (!horizontal) {
                        if (Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx)) { tracking = false; break; }
                        if (Math.abs(dx) > touchSlop) horizontal = true;
                    }
                    if (horizontal) {
                        // Le fond suit le doigt
                        float step = 1f / (pages - 1);
                        current = clamp(downOffset - (dx / width) * step);
                        handler.removeCallbacks(animator);
                        draw();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (tracking && horizontal) {
                        float moved = e.getX() - downX;
                        float vx = 0;
                        if (velocity != null) { velocity.addMovement(e); velocity.computeCurrentVelocity(1000); vx = velocity.getXVelocity(); }
                        if (moved < -width / 4f || vx < -minFling) page++;
                        else if (moved > width / 4f || vx > minFling) page--;
                        page = Math.max(0, Math.min(pages - 1, page));
                        target = pageToOffset(page, pages);
                        handler.removeCallbacks(animator);
                        handler.post(animator);
                    }
                    tracking = false;
                    horizontal = false;
                    break;
            }
        }

        private float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

        private void loadBitmap() {
            if (width == 0 || height == 0) return;
            if (bitmap != null) { bitmap.recycle(); bitmap = null; }
            File f = new File(getFilesDir(), IMAGE_FILE);
            if (!f.exists()) return;
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                if (o.outWidth <= 0 || o.outHeight <= 0) return;
                float amp = prefs.getInt(KEY_AMPLITUDE, 50) / 100f;
                int needW = (int) (width * (1 + amp));
                int sample = 1;
                while (o.outHeight / (sample * 2) >= height && o.outWidth / (sample * 2) >= needW) sample *= 2;
                o.inJustDecodeBounds = false;
                o.inSampleSize = sample;
                bitmap = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            } catch (Throwable t) {
                bitmap = null;
            }
        }

        private void draw() {
            if (!visible || width == 0) return;
            SurfaceHolder holder = getSurfaceHolder();
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c == null) return;
                if (bitmap == null) {
                    drawPlaceholder(c);
                } else {
                    float offset = useLauncher() ? launcherOffset : current;
                    float amp = prefs.getInt(KEY_AMPLITUDE, 50) / 100f;
                    int bw = bitmap.getWidth(), bh = bitmap.getHeight();
                    float scale = Math.max(height / (float) bh, width * (1 + amp) / bw);
                    float sw = bw * scale, sh = bh * scale;
                    float range = Math.max(0, sw - width);
                    matrix.setScale(scale, scale);
                    matrix.postTranslate(-range * offset, (height - sh) / 2f);
                    c.drawColor(Color.BLACK);
                    c.drawBitmap(bitmap, matrix, paint);
                }
            } finally {
                if (c != null) {
                    try { holder.unlockCanvasAndPost(c); } catch (IllegalArgumentException ignored) {}
                }
            }
        }

        private void drawPlaceholder(Canvas c) {
            Paint bg = new Paint();
            float offset = useLauncher() ? launcherOffset : current;
            float shift = -offset * width * 0.5f;
            bg.setShader(new LinearGradient(shift, 0, shift + width * 1.5f, height,
                    0xFF1E3C72, 0xFF8E44AD, Shader.TileMode.MIRROR));
            c.drawRect(0, 0, width, height, bg);
            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
            t.setColor(Color.WHITE);
            t.setTextAlign(Paint.Align.CENTER);
            t.setTextSize(width / 20f);
            c.drawText("Ouvre l'appli Fond Défilant", width / 2f, height / 2f, t);
            c.drawText("pour choisir une image", width / 2f, height / 2f + width / 14f, t);
        }
    }
}
