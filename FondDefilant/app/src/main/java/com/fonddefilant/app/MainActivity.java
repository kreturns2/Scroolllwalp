package com.fonddefilant.app;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final int REQ_PICK = 1;

    private SharedPreferences prefs;
    private ImageView preview;
    private TextView ampLabel, pagesLabel, startLabel;
    private SeekBar startBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(ScrollWallpaperService.PREFS, MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF121212);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        root.setPadding(p, p, p, p);
        scroll.addView(root);

        TextView title = text("Fond Défilant", 26, Color.WHITE);
        root.addView(title);
        root.addView(text("Choisis n'importe quelle image : elle défilera quand tu changes de page sur le bureau.", 14, 0xFFBBBBBB));

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable frame = new GradientDrawable();
        frame.setColor(0xFF2A2A2A);
        frame.setCornerRadius(dp(16));
        preview.setBackground(frame);
        preview.setClipToOutline(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(220));
        lp.topMargin = dp(16);
        root.addView(preview, lp);
        loadPreview();

        root.addView(button("1. Choisir une image", v -> pickImage()));
        root.addView(button("2. Appliquer comme fond d'écran", v -> applyWallpaper()));

        // Amplitude
        ampLabel = text("", 15, Color.WHITE);
        ampLabel.setPadding(0, dp(24), 0, 0);
        root.addView(ampLabel);
        SeekBar amp = new SeekBar(this);
        amp.setMax(140); // 10..150 %
        amp.setProgress(prefs.getInt(ScrollWallpaperService.KEY_AMPLITUDE, 50) - 10);
        amp.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean user) {
                prefs.edit().putInt(ScrollWallpaperService.KEY_AMPLITUDE, v + 10).apply();
                updateLabels();
            }
        });
        root.addView(amp);

        // Mode
        TextView modeTitle = text("Détection du changement de page", 15, Color.WHITE);
        modeTitle.setPadding(0, dp(20), 0, 0);
        root.addView(modeTitle);
        RadioGroup modes = new RadioGroup(this);
        String[] labels = {
                "Automatique (recommandé)",
                "Launcher uniquement (Nova, Lawnchair…)",
                "Glissement du doigt (Samsung, Pixel…)"
        };
        int currentMode = prefs.getInt(ScrollWallpaperService.KEY_MODE, ScrollWallpaperService.MODE_AUTO);
        for (int i = 0; i < labels.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(100 + i);
            rb.setText(labels[i]);
            rb.setTextColor(0xFFDDDDDD);
            modes.addView(rb);
            if (i == currentMode) rb.setChecked(true);
        }
        modes.setOnCheckedChangeListener((g, id) ->
                prefs.edit().putInt(ScrollWallpaperService.KEY_MODE, id - 100).apply());
        root.addView(modes);

        // Nombre de pages
        pagesLabel = text("", 15, Color.WHITE);
        pagesLabel.setPadding(0, dp(20), 0, 0);
        root.addView(pagesLabel);
        SeekBar pages = new SeekBar(this);
        pages.setMax(7); // 2..9 pages
        pages.setProgress(prefs.getInt(ScrollWallpaperService.KEY_PAGES, 5) - 2);
        root.addView(pages);

        // Page d'accueil
        startLabel = text("", 15, Color.WHITE);
        startLabel.setPadding(0, dp(20), 0, 0);
        root.addView(startLabel);
        startBar = new SeekBar(this);
        int nbPages = prefs.getInt(ScrollWallpaperService.KEY_PAGES, 5);
        startBar.setMax(nbPages - 1);
        startBar.setProgress(Math.min(nbPages - 1, Math.max(0, prefs.getInt(ScrollWallpaperService.KEY_START_PAGE, 0))));
        startBar.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean user) {
                prefs.edit().putInt(ScrollWallpaperService.KEY_START_PAGE, v).apply();
                updateLabels();
            }
        });
        root.addView(startBar);

        pages.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean user) {
                int n = v + 2;
                prefs.edit().putInt(ScrollWallpaperService.KEY_PAGES, n).apply();
                startBar.setMax(n - 1);
                if (startBar.getProgress() > n - 1) startBar.setProgress(n - 1);
                updateLabels();
            }
        });

        CheckBox reset = new CheckBox(this);
        reset.setText("Revenir sur la page d'accueil à chaque retour sur le bureau");
        reset.setTextColor(0xFFDDDDDD);
        reset.setChecked(prefs.getBoolean(ScrollWallpaperService.KEY_RESET_ON_RETURN, false));
        reset.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean(ScrollWallpaperService.KEY_RESET_ON_RETURN, checked).apply());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(8);
        root.addView(reset, rlp);

        TextView hint = text("Mode glissement : indique exactement le nombre de pages et la page d'accueil de ton bureau. "
                + "Le fond avance d'une page par glissement et s'arrête net à la première et à la dernière page. "
                + "Active la case ci-dessus si ton bureau revient sur la page d'accueil quand tu quittes une appli.", 13, 0xFF999999);
        hint.setPadding(0, dp(8), 0, 0);
        root.addView(hint);

        updateLabels();
        setContentView(scroll);
    }

    private void updateLabels() {
        ampLabel.setText("Amplitude du défilement : " + prefs.getInt(ScrollWallpaperService.KEY_AMPLITUDE, 50) + " %");
        pagesLabel.setText("Nombre de pages du bureau : " + prefs.getInt(ScrollWallpaperService.KEY_PAGES, 5));
        if (startLabel != null) {
            startLabel.setText("Page d'accueil : " + (prefs.getInt(ScrollWallpaperService.KEY_START_PAGE, 0) + 1));
        }
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(i, REQ_PICK);
        } catch (Exception e) {
            Intent g = new Intent(Intent.ACTION_GET_CONTENT);
            g.setType("image/*");
            startActivityForResult(g, REQ_PICK);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        Toast.makeText(this, "Import de l'image…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean ok = copyImage(uri);
            runOnUiThread(() -> {
                if (ok) {
                    prefs.edit().putLong(ScrollWallpaperService.KEY_STAMP, System.currentTimeMillis()).apply();
                    loadPreview();
                    Toast.makeText(this, "Image prête ! Appuie sur « Appliquer ».", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Impossible de lire cette image.", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private boolean copyImage(Uri uri) {
        File tmp = new File(getFilesDir(), "wallpaper.tmp");
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(tmp)) {
            if (in == null) return false;
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (Exception e) {
            return false;
        }
        File dest = new File(getFilesDir(), ScrollWallpaperService.IMAGE_FILE);
        return tmp.renameTo(dest);
    }

    private void loadPreview() {
        File f = new File(getFilesDir(), ScrollWallpaperService.IMAGE_FILE);
        if (!f.exists()) { preview.setImageDrawable(null); return; }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= 800) sample *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        preview.setImageBitmap(b);
    }

    private void applyWallpaper() {
        if (!new File(getFilesDir(), ScrollWallpaperService.IMAGE_FILE).exists()) {
            Toast.makeText(this, "Choisis d'abord une image.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(this, ScrollWallpaperService.class));
        try {
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
                Toast.makeText(this, "Sélectionne « Fond Défilant » dans la liste.", Toast.LENGTH_LONG).show();
            } catch (Exception e2) {
                Toast.makeText(this, "Ouvre Paramètres > Fond d'écran > Fonds animés.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setPadding(0, dp(4), 0, 0);
        return t;
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF7C4DFF);
        bg.setCornerRadius(dp(28));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(14);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar s) {}
        @Override public void onStopTrackingTouch(SeekBar s) {}
    }
}
