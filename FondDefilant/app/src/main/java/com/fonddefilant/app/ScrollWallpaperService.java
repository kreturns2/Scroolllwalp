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
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.wallpaper.WallpaperService;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;

import java.io.File;

/**
 * Fond d'écran animé qui défile page par page, synchronisé avec le bureau.
 *
 * Fluidité :
 *  - rendu calé sur la fréquence de l'écran (Choreographer : 60/90/120 Hz)
 *  - canvas accéléré par le GPU (Android 8+)
 *  - image redimensionnée une seule fois, en arrière-plan : chaque image affichée
 *    n'est qu'une simple copie décalée
 *  - animations basées sur le temps réel (pas sur le nombre d'images)
 *
 * Synchronisation (mode gestes, pour Samsung/Pixel/Xiaomi…) :
 *  - le fond suit le doigt exactement comme une page du bureau
 *  - un glissement ne peut avancer que d'une page, avec butée à la première et
 *    à la dernière page
 *  - mêmes règles de changement de page que les launchers Android
 *    (40 % de l'écran ou geste rapide), même courbe d'animation
 *  - ignore : glissements verticaux, appui long (déplacement d'icône),
 *    gestes à deux doigts, geste « retour » depuis le bord de l'écran
 */
public class ScrollWallpaperService extends WallpaperService {

    public static final String PREFS = "settings";
    public static final String IMAGE_FILE = "wallpaper.img";

    public static final String KEY_AMPLITUDE = "amplitude";     // % de largeur en plus (10..150)
    public static final String KEY_PAGES = "pages";             // nombre de pages du bureau
    public static final String KEY_START_PAGE = "startPage";    // page d'accueil (0 = première)
    public static final String KEY_RESET_ON_RETURN = "resetOnReturn";
    public static final String KEY_MODE = "mode";               // 0 auto, 1 launcher, 2 gestes
    public static final String KEY_STAMP = "imageStamp";

    public static final int MODE_AUTO = 0, MODE_LAUNCHER = 1, MODE_GESTURES = 2;

    @Override
    public Engine onCreateEngine() {
        return new ScrollEngine();
    }

    class ScrollEngine extends Engine
            implements SharedPreferences.OnSharedPreferenceChangeListener, Choreographer.FrameCallback {

        private final Handler main = new Handler(Looper.getMainLooper());
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private SharedPreferences prefs;

        // Image prête à l'affichage (déjà à la bonne taille)
        private volatile Bitmap scaled;
        private int loadGeneration;
        private int width, height;
        private boolean visible, wasHidden;
        private boolean hardwareOk = Build.VERSION.SDK_INT >= 26;

        // Rendu
        private boolean framePending;

        // Launcher
        private float launcherOffset = 0.5f;
        private boolean launcherMoves;

        // Position (0 = tout à gauche, 1 = tout à droite)
        private float current;
        private int page;

        // Animation vers une page
        private boolean animating;
        private float animFrom, animTo;
        private long animStartNs = -1;
        private long animDurationMs;

        // Suivi du doigt
        private VelocityTracker velocity;
        private float downX, downY, downOffset;
        private long downTime;
        private int downPage;
        private boolean tracking, dragging;
        private int touchSlop, flingVelocity, edgeSize;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.registerOnSharedPreferenceChangeListener(this);
            ViewConfiguration vc = ViewConfiguration.get(ScrollWallpaperService.this);
            float density = getResources().getDisplayMetrics().density;
            touchSlop = vc.getScaledTouchSlop();
            flingVelocity = Math.round(500 * density);   // même seuil que les launchers Android
            edgeSize = Math.round(28 * density);         // zone du geste « retour »
            setTouchEventsEnabled(true);
            setOffsetNotificationsEnabled(true);
            jumpToStartPage();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            Choreographer.getInstance().removeFrameCallback(this);
            framePending = false;
            prefs.unregisterOnSharedPreferenceChangeListener(this);
            if (velocity != null) { velocity.recycle(); velocity = null; }
            Bitmap b = scaled;
            scaled = null;
            if (b != null) b.recycle();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            super.onSurfaceChanged(holder, format, w, h);
            boolean sizeChanged = w != width || h != height;
            width = w;
            height = h;
            if (sizeChanged || scaled == null) loadBitmapAsync();
            requestFrame();
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            visible = v;
            if (!v) {
                wasHidden = true;
                if (animating) { current = animTo; animating = false; }
                tracking = dragging = false;
                return;
            }
            if (wasHidden && !isPreview() && prefs.getBoolean(KEY_RESET_ON_RETURN, false)) {
                jumpToStartPage();
            }
            wasHidden = false;
            requestFrame();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            visible = false;
            Choreographer.getInstance().removeFrameCallback(this);
            framePending = false;
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xStep, float yStep,
                                     int xPixels, int yPixels) {
            if (Math.abs(xOffset - launcherOffset) > 0.0001f) launcherMoves = true;
            launcherOffset = xOffset;
            if (useLauncher()) requestFrame();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences p, String key) {
            if (KEY_STAMP.equals(key) || KEY_AMPLITUDE.equals(key)) loadBitmapAsync();
            if (KEY_PAGES.equals(key) || KEY_START_PAGE.equals(key)) jumpToStartPage();
            requestFrame();
        }

        // ---------- Pages ----------

        private int pages() { return Math.max(2, prefs.getInt(KEY_PAGES, 5)); }

        private float offsetOf(int p) { return p / (float) (pages() - 1); }

        private void jumpToStartPage() {
            int pages = pages();
            int start = prefs.getInt(KEY_START_PAGE, -1);
            if (start < 0 || start >= pages) start = 0;
            page = start;
            animating = false;
            current = offsetOf(page);
        }

        private boolean useLauncher() {
            int mode = prefs.getInt(KEY_MODE, MODE_AUTO);
            if (mode == MODE_LAUNCHER) return true;
            if (mode == MODE_GESTURES) return false;
            return launcherMoves;
        }

        // ---------- Gestes ----------

        @Override
        public void onTouchEvent(MotionEvent e) {
            if (useLauncher() || width == 0) return;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    if (velocity == null) velocity = VelocityTracker.obtain(); else velocity.clear();
                    velocity.addMovement(e);
                    downX = e.getX();
                    downY = e.getY();
                    downTime = e.getEventTime();
                    dragging = false;
                    // Geste « retour » depuis le bord : le bureau ne bouge pas, nous non plus
                    tracking = !(gestureNavigation() && (downX < edgeSize || downX > width - edgeSize));
                    if (tracking && animating) {
                        // Le doigt rattrape la page en cours d'animation, comme sur le bureau
                        animating = false;
                        page = Math.round(animTo * (pages() - 1));
                    }
                    downOffset = current;
                    downPage = page;
                    break;
                }
                case MotionEvent.ACTION_POINTER_DOWN:
                    // Deux doigts = zoom/édition du bureau : on revient sur la page
                    if (tracking) { tracking = false; snapTo(downPage, 0); }
                    break;
                case MotionEvent.ACTION_MOVE: {
                    if (!tracking) break;
                    velocity.addMovement(e);
                    float dx = e.getX() - downX, dy = e.getY() - downY;
                    if (!dragging) {
                        if (Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx)) { tracking = false; break; }
                        if (Math.abs(dx) > touchSlop) {
                            // Appui long puis glissement = déplacement d'icône, pas de page
                            if (e.getEventTime() - downTime > ViewConfiguration.getLongPressTimeout()) {
                                tracking = false;
                                break;
                            }
                            dragging = true;
                            downX = e.getX() + (dx > 0 ? -touchSlop : touchSlop);
                            dx = e.getX() - downX;
                        }
                    }
                    if (dragging) {
                        // Suit le doigt 1:1, au maximum une page de chaque côté, butée aux extrémités
                        float step = 1f / (pages() - 1);
                        float lo = offsetOf(Math.max(0, downPage - 1));
                        float hi = offsetOf(Math.min(pages() - 1, downPage + 1));
                        current = Math.max(lo, Math.min(hi, downOffset - (dx / width) * step));
                        requestFrame();
                    }
                    break;
                }
                case MotionEvent.ACTION_UP: {
                    if (!(tracking && dragging)) { tracking = dragging = false; break; }
                    velocity.addMovement(e);
                    velocity.computeCurrentVelocity(1000);
                    float vx = velocity.getXVelocity();
                    float dx = e.getX() - downX;
                    boolean fling = Math.abs(vx) > flingVelocity;
                    boolean significant = Math.abs(dx) > width * 0.4f;
                    int target = downPage;
                    // Mêmes règles que le bureau Android (PagedView)
                    if ((significant && dx < 0 && !fling) || (fling && vx < 0)) target = downPage + 1;
                    else if ((significant && dx > 0 && !fling) || (fling && vx > 0)) target = downPage - 1;
                    target = Math.max(0, Math.min(pages() - 1, target));
                    snapTo(target, fling ? Math.abs(vx) : 0);
                    tracking = dragging = false;
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                    if (tracking && dragging) snapTo(downPage, 0);
                    tracking = dragging = false;
                    break;
            }
        }

        private boolean gestureNavigation() {
            try {
                return Settings.Secure.getInt(getContentResolver(), "navigation_mode", 0) == 2;
            } catch (Throwable t) {
                return false;
            }
        }

        private void snapTo(int target, float velocityPx) {
            page = target;
            animFrom = current;
            animTo = offsetOf(target);
            float distancePx = Math.abs(animTo - animFrom) * (pages() - 1) * width;
            long duration;
            if (velocityPx > 0) {
                duration = (long) (4000f * distancePx / velocityPx);
                duration = Math.max(200, Math.min(450, duration));
            } else {
                duration = (long) (250 + 300 * Math.min(1f, distancePx / Math.max(1, width)));
            }
            animDurationMs = duration;
            animStartNs = -1;
            animating = Math.abs(animTo - animFrom) > 0.00001f;
            if (!animating) current = animTo;
            requestFrame();
        }

        // Courbe de décélération des launchers Android
        private float interpolate(float t) {
            t -= 1f;
            return t * t * t * t * t + 1f;
        }

        // ---------- Rendu ----------

        private void requestFrame() {
            if (framePending || !visible) return;
            framePending = true;
            Choreographer.getInstance().postFrameCallback(this);
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            framePending = false;
            if (!visible) return;
            if (animating) {
                if (animStartNs < 0) animStartNs = frameTimeNanos;
                float t = (frameTimeNanos - animStartNs) / 1_000_000f / animDurationMs;
                if (t >= 1f) {
                    current = animTo;
                    animating = false;
                } else {
                    current = animFrom + (animTo - animFrom) * interpolate(t);
                }
            }
            drawNow();
            if (animating) requestFrame();
        }

        private Canvas lock(SurfaceHolder holder) {
            if (hardwareOk) {
                try {
                    return holder.lockHardwareCanvas();
                } catch (Throwable t) {
                    hardwareOk = false;
                }
            }
            return holder.lockCanvas();
        }

        private void drawNow() {
            if (width == 0) return;
            SurfaceHolder holder = getSurfaceHolder();
            Canvas c = null;
            try {
                c = lock(holder);
                if (c == null) return;
                float offset = useLauncher() ? launcherOffset : current;
                Bitmap b = scaled;
                if (b == null || b.isRecycled()) {
                    drawPlaceholder(c, offset);
                } else {
                    float range = Math.max(0, b.getWidth() - width);
                    float top = (height - b.getHeight()) / 2f;
                    if (b.getWidth() < width || b.getHeight() < height) c.drawColor(Color.BLACK);
                    c.drawBitmap(b, -range * offset, top, paint);
                }
            } catch (Throwable ignored) {
            } finally {
                if (c != null) {
                    try { holder.unlockCanvasAndPost(c); } catch (Throwable ignored) {}
                }
            }
        }

        private void drawPlaceholder(Canvas c, float offset) {
            Paint bg = new Paint();
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

        // ---------- Chargement de l'image (en arrière-plan) ----------

        private void loadBitmapAsync() {
            final int w = width, h = height;
            if (w == 0 || h == 0) return;
            final int gen = ++loadGeneration;
            final float amp = prefs.getInt(KEY_AMPLITUDE, 50) / 100f;
            final File f = new File(getFilesDir(), IMAGE_FILE);
            new Thread(() -> {
                final Bitmap result = f.exists() ? prepare(f, w, h, amp) : null;
                main.post(() -> {
                    if (gen != loadGeneration) { if (result != null) result.recycle(); return; }
                    Bitmap old = scaled;
                    scaled = result;
                    requestFrame();
                    if (old != null && old != result) main.postDelayed(old::recycle, 500);
                });
            }, "FondDefilant-load").start();
        }

        /** Décode et met l'image à la taille exacte de l'écran, une fois pour toutes. */
        private Bitmap prepare(File f, int w, int h, float amp) {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                if (o.outWidth <= 0 || o.outHeight <= 0) return null;
                int needW = (int) (w * (1 + amp));
                int sample = 1;
                while (o.outHeight / (sample * 2) >= h && o.outWidth / (sample * 2) >= needW) sample *= 2;
                o.inJustDecodeBounds = false;
                o.inSampleSize = sample;
                Bitmap src = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                if (src == null) return null;

                int bw = src.getWidth(), bh = src.getHeight();
                float scale = Math.max(h / (float) bh, needW / (float) bw);
                // Limite mémoire : pas plus de 5 écrans de large
                float maxScale = (w * 5f) / bw;
                if (scale > maxScale && maxScale * bh >= h) scale = maxScale;

                // On ne garde que la bande visible verticalement
                int cropH = Math.min(bh, Math.round(h / scale));
                int cropY = Math.max(0, (bh - cropH) / 2);
                Matrix m = new Matrix();
                m.setScale(scale, scale);
                Bitmap out = Bitmap.createBitmap(src, 0, cropY, bw, cropH, m, true);
                if (out != src) src.recycle();
                if (Build.VERSION.SDK_INT >= 19) out.prepareToDraw();
                return out;
            } catch (Throwable t) {
                return null;
            }
        }
    }
}
