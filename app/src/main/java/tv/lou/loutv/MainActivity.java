package tv.lou.loutv;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * LOU TV: low-overhead Android TV launcher prototype.
 * Native platform views; no background workers, analytics, network or third-party libraries.
 * The Bluetooth panel is a system-settings shortcut, NOT a privileged A2DP switcher.
 */
public class MainActivity extends Activity {
    private static final int BG = Color.rgb(8, 9, 12);
    private static final int CARD = Color.rgb(24, 27, 34);
    private static final int RED = Color.rgb(255, 47, 70);
    private static final int WHITE = Color.rgb(244, 245, 247);
    private static final int MUTED = Color.rgb(171, 177, 190);
    private static final int REQUEST_BT = 41;

    private final List<TvApp> apps = new ArrayList<>();
    private SharedPreferences prefs;
    private FrameLayout root;
    private ScrollView scroll;
    private View audioBackdrop;
    private FrameLayout audioPanel;
    private String searchTerm = "";
    private LinearLayout favouritesSection;
    private LinearLayout allSection;
    private View firstCard;

    private static final class TvApp {
        final String label;
        final String packageName;
        final String className;
        final Drawable icon;
        TvApp(String label, String pkg, String cls, Drawable icon) {
            this.label = label; this.packageName = pkg; this.className = cls; this.icon = icon;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        prefs = getSharedPreferences("lou_tv", MODE_PRIVATE);
        loadInstalledApps();
        seedFavourites();
        renderHome();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable shape(int colour, int borderColour, int borderDp, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(colour); d.setCornerRadius(dp(radiusDp));
        if (borderDp > 0) d.setStroke(dp(borderDp), borderColour);
        return d;
    }

    private TextView label(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(sp); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setIncludeFontPadding(false);
        return v;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private void loadInstalledApps() {
        apps.clear();
        PackageManager pm = getPackageManager();
        Map<String, TvApp> found = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo r : pm.queryIntentActivities(query, 0)) {
                if (r.activityInfo == null) continue;
                if (getPackageName().equals(r.activityInfo.packageName)) continue;
                String pkg = r.activityInfo.packageName;
                if (found.containsKey(pkg)) continue;
                String title = String.valueOf(r.loadLabel(pm));
                found.put(pkg, new TvApp(title, pkg, r.activityInfo.name, r.loadIcon(pm)));
            }
        }
        apps.addAll(found.values());
        Collections.sort(apps, Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
    }

    private void seedFavourites() {
        if (prefs.contains("favourites")) return;
        Set<String> f = new HashSet<>();
        String[] ideal = {"stremio", "youtube", "netflix", "plex", "prime video", "spotify"};
        for (String brand : ideal) {
            for (TvApp a : apps) {
                if (a.label.toLowerCase(Locale.ROOT).contains(brand) ||
                        a.packageName.toLowerCase(Locale.ROOT).contains(brand.replace(" ", ""))) {
                    f.add(a.packageName); break;
                }
            }
        }
        for (TvApp a : apps) { if (f.size() >= 6) break; f.add(a.packageName); }
        prefs.edit().putStringSet("favourites", f).apply();
    }

    private Set<String> favourites() {
        return new HashSet<>(prefs.getStringSet("favourites", Collections.emptySet()));
    }

    private List<TvApp> orderedFavourites() {
        List<TvApp> result = new ArrayList<>();
        Set<String> fav = favourites();
        for (TvApp a : apps) if (fav.contains(a.packageName)) result.add(a);
        String[] order = {"stremio", "youtube", "netflix", "plex", "prime video", "spotify"};
        result.sort((a, b) -> Integer.compare(favouriteRank(a, order), favouriteRank(b, order)));
        return result;
    }

    private int favouriteRank(TvApp a, String[] order) {
        String s = (a.label + a.packageName).toLowerCase(Locale.ROOT);
        for (int i = 0; i < order.length; i++) if (s.contains(order[i].replace(" ", "")) || s.contains(order[i])) return i;
        return 100;
    }

    private List<TvApp> recentApps() {
        List<TvApp> result = new ArrayList<>();
        String previous = prefs.getString("recent", "");
        for (String pkg : previous.split(",")) {
            for (TvApp a : apps) if (a.packageName.equals(pkg)) result.add(a);
        }
        return result;
    }

    private void toggleFavourite(TvApp app) {
        Set<String> fav = favourites();
        boolean adding = !fav.contains(app.packageName);
        if (adding) fav.add(app.packageName); else fav.remove(app.packageName);
        prefs.edit().putStringSet("favourites", fav).apply();
        Toast.makeText(this, app.label + (adding ? " added to Favourites" : " removed from Favourites"), Toast.LENGTH_SHORT).show();
        renderHome();
    }

    private void openApp(TvApp app) {
        String old = prefs.getString("recent", "");
        List<String> names = new ArrayList<>();
        names.add(app.packageName);
        for (String pkg : old.split(",")) if (!pkg.isEmpty() && !names.contains(pkg) && names.size() < 12) names.add(pkg);
        prefs.edit().putString("recent", TextUtils.join(",", names)).apply();
        try {
            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.setClassName(app.packageName, app.className);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(launch);
        } catch (Exception e) {
            try {
                Intent fallback = getPackageManager().getLaunchIntentForPackage(app.packageName);
                if (fallback != null) startActivity(fallback);
                else Toast.makeText(this, "Unable to open " + app.label, Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {
                Toast.makeText(this, "Unable to open " + app.label, Toast.LENGTH_LONG).show();
            }
        }
    }

    private TextView action(String text, Runnable handler) {
        TextView t = label(text, 15, WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(6), dp(14), dp(6));
        t.setFocusable(true);
        t.setBackground(shape(Color.rgb(23, 24, 29), Color.rgb(52, 55, 64), 1, 10));
        t.setOnFocusChangeListener((v, hasFocus) -> {
            t.setBackground(shape(hasFocus ? Color.rgb(62, 23, 34) : Color.rgb(23, 24, 29),
                    hasFocus ? RED : Color.rgb(52, 55, 64), hasFocus ? 2 : 1, 10));
        });
        t.setOnClickListener(v -> handler.run());
        return t;
    }

    private void renderHome() {
        hideAudioPanel();
        firstCard = null;
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout page = vertical();
        page.setBackgroundColor(BG);
        scroll.addView(page);

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(28), dp(12), dp(28), dp(10));
        nav.setBackgroundColor(Color.rgb(11, 12, 15));
        page.addView(nav, new LinearLayout.LayoutParams(-1, dp(62)));

        TextView logo = label("LOU", 27, WHITE, true);
        nav.addView(logo);
        TextView logoTV = label("TV", 27, RED, true);
        LinearLayout.LayoutParams lpTV = new LinearLayout.LayoutParams(-2, -2);
        lpTV.setMargins(dp(3), 0, dp(28), 0);
        nav.addView(logoTV, lpTV);

        addNav(nav, "Home", () -> scroll.smoothScrollTo(0, 0));
        addNav(nav, "Apps", () -> moveTo(allSection));
        addNav(nav, "Favourites", () -> moveTo(favouritesSection));
        addNav(nav, "Search", this::showSearch);
        addNav(nav, "Settings", this::openSettings);

        View fill = new View(this);
        nav.addView(fill, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView audio = action("♫ Audio", this::showAudioPanel);
        nav.addView(audio, new LinearLayout.LayoutParams(-2, dp(40)));
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm"); clock.setFormat12Hour("h:mm");
        clock.setTextSize(17); clock.setTextColor(WHITE);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
        cp.setMargins(dp(20), 0, 0, 0);
        nav.addView(clock, cp);

        FrameLayout hero = new FrameLayout(this);
        page.addView(hero, new LinearLayout.LayoutParams(-1, dp(225)));
        ImageView art = new ImageView(this);
        art.setImageResource(R.drawable.frontier_hero);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams artParams = new FrameLayout.LayoutParams(dp(600), -1, Gravity.RIGHT);
        hero.addView(art, artParams);
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{BG, Color.rgb(8, 9, 12), 0xC808090C, 0x0008090C}));
        hero.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        View floor = new View(this);
        floor.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x0008090C, BG}));
        FrameLayout.LayoutParams foot = new FrameLayout.LayoutParams(-1, dp(85), Gravity.BOTTOM);
        hero.addView(floor, foot);

        LinearLayout heroCopy = vertical();
        heroCopy.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams heroPosition = new FrameLayout.LayoutParams(dp(520), -1, Gravity.LEFT);
        heroPosition.leftMargin = dp(33);
        hero.addView(heroCopy, heroPosition);
        TextView kicker = label("WELCOME TO YOUR ENTERTAINMENT", 12, MUTED, false);
        kicker.setLetterSpacing(0.18f);
        heroCopy.addView(kicker);
        TextView title = label("LOU TV", 53, WHITE, true);
        LinearLayout.LayoutParams titleMargin = new LinearLayout.LayoutParams(-2, -2);
        titleMargin.topMargin = dp(6);
        heroCopy.addView(title, titleMargin);
        TextView description = label("Your apps. Your entertainment. All in one place.", 17, WHITE, false);
        LinearLayout.LayoutParams descriptionMargin = new LinearLayout.LayoutParams(-2, -2);
        descriptionMargin.topMargin = dp(4);
        heroCopy.addView(description, descriptionMargin);
        TextView prompt = label("Press and hold OK on an app to change favourites", 12, MUTED, false);
        LinearLayout.LayoutParams promptMargin = new LinearLayout.LayoutParams(-2, -2);
        promptMargin.topMargin = dp(10);
        heroCopy.addView(prompt, promptMargin);

        favouritesSection = createAppRow(page, "Favourite Apps", orderedFavourites(), "Press and hold OK to add or remove favourites");
        List<TvApp> recent = recentApps();
        if (!recent.isEmpty()) createAppRow(page, "Recently Used", recent, null);
        List<TvApp> filtered = new ArrayList<>();
        for (TvApp a : apps) {
            if (searchTerm.isEmpty() || a.label.toLowerCase(Locale.ROOT).contains(searchTerm.toLowerCase(Locale.ROOT))) filtered.add(a);
        }
        allSection = createAppRow(page, searchTerm.isEmpty() ? "All Apps" : "Search Results: " + searchTerm, filtered, null);
        TextView footer = label("LOU TV 1.0 prototype  •  Lightweight native Android TV launcher", 11, MUTED, false);
        footer.setPadding(dp(33), dp(10), 0, dp(25));
        page.addView(footer);
        if (firstCard != null) firstCard.requestFocus();
    }

    private void addNav(LinearLayout parent, String name, Runnable action) {
        TextView text = action(name, action);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(38));
        p.setMargins(0, 0, dp(8), 0);
        parent.addView(text, p);
    }

    private LinearLayout createAppRow(LinearLayout page, String title, List<TvApp> items, String hint) {
        LinearLayout section = vertical();
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
        sectionParams.setMargins(0, dp(5), 0, dp(12));
        page.addView(section, sectionParams);
        TextView heading = label(title, 21, WHITE, true);
        heading.setPadding(dp(33), 0, 0, dp(12));
        section.addView(heading);
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        horizontal.setClipToPadding(false);
        horizontal.setPadding(dp(32), dp(3), dp(32), dp(5));
        section.addView(horizontal);
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        horizontal.addView(strip);
        if (items.isEmpty()) {
            TextView empty = label("No apps in this row yet", 15, MUTED, false);
            strip.addView(empty, new LinearLayout.LayoutParams(dp(300), dp(96)));
        }
        for (TvApp app : items) {
            View card = createCard(app);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(163), dp(90));
            p.setMargins(0, 0, dp(11), 0);
            strip.addView(card, p);
            if (firstCard == null) firstCard = card;
        }
        if (hint != null) {
            TextView note = label(hint, 11, MUTED, false);
            note.setPadding(dp(33), dp(1), 0, 0);
            section.addView(note);
        }
        return section;
    }

    private View createCard(TvApp app) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(12), dp(7), dp(12), dp(7));
        card.setBackground(shape(CARD, Color.rgb(53, 55, 65), 1, 10));
        card.setFocusable(true); card.setClickable(true); card.setLongClickable(true);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView title = label(app.label, 14, WHITE, true);
        title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titlePos = new LinearLayout.LayoutParams(0, -2, 1);
        titlePos.setMargins(dp(8), 0, 0, 0);
        card.addView(title, titlePos);
        card.setOnFocusChangeListener((v, hasFocus) -> {
            card.setBackground(shape(hasFocus ? Color.rgb(43, 30, 42) : CARD,
                    hasFocus ? RED : Color.rgb(53, 55, 65), hasFocus ? 2 : 1, 10));
            card.setScaleX(hasFocus ? 1.035f : 1f);
            card.setScaleY(hasFocus ? 1.035f : 1f);
            card.setElevation(hasFocus ? dp(7) : 0);
        });
        card.setOnClickListener(v -> openApp(app));
        card.setOnLongClickListener(v -> { toggleFavourite(app); return true; });
        return card;
    }

    private void moveTo(View target) {
        if (target != null) scroll.smoothScrollTo(0, target.getTop());
    }

    private void openSettings() { openSettingsIntent(Settings.ACTION_SETTINGS); }

    private void openSettingsIntent(String action) {
        hideAudioPanel();
        try { startActivity(new Intent(action)); }
        catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (Exception ignored) { Toast.makeText(this, "Settings unavailable", Toast.LENGTH_LONG).show(); }
        }
    }

    private void showSearch() {
        EditText input = new EditText(this);
        input.setText(searchTerm); input.setSingleLine(true);
        input.setHint("Search installed apps");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setPadding(dp(18), 0, dp(18), 0);
        wrapper.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("Find an app")
                .setView(wrapper)
                .setPositiveButton("Search", (dialog, which) -> {
                    searchTerm = input.getText().toString().trim();
                    renderHome(); moveTo(allSection);
                })
                .setNeutralButton("Clear", (dialog, which) -> {
                    searchTerm = ""; renderHome();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAudioPanel() {
        if (Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BT);
            return;
        }
        hideAudioPanel();
        audioBackdrop = new View(this);
        audioBackdrop.setBackgroundColor(0x66000000);
        audioBackdrop.setOnClickListener(v -> hideAudioPanel());
        root.addView(audioBackdrop, new FrameLayout.LayoutParams(-1, -1));

        audioPanel = new FrameLayout(this);
        audioPanel.setBackground(shape(Color.rgb(20, 22, 28), Color.rgb(80, 83, 95), 1, 14));
        FrameLayout.LayoutParams panelPlacement = new FrameLayout.LayoutParams(dp(345), -2, Gravity.TOP | Gravity.RIGHT);
        panelPlacement.setMargins(0, dp(61), dp(27), 0);
        root.addView(audioPanel, panelPlacement);
        LinearLayout column = vertical();
        column.setPadding(dp(18), dp(16), dp(18), dp(16));
        audioPanel.addView(column);
        TextView header = label("♫  Audio Output", 23, WHITE, true);
        column.addView(header);
        TextView sub = label("Paired audio devices", 13, MUTED, false);
        sub.setPadding(0, dp(6), 0, dp(10));
        column.addView(sub);
        TextView speakers = action("TV speakers / system audio", () -> openSettingsIntent(Settings.ACTION_SOUND_SETTINGS));
        column.addView(speakers, spacingParams());

        List<BluetoothDevice> devices = pairedAudioDevices();
        if (devices.isEmpty()) {
            TextView empty = label("No paired audio devices found. Pair earbuds in Google TV Settings.", 13, MUTED, false);
            empty.setPadding(0, dp(7), 0, dp(12));
            column.addView(empty);
        } else {
            for (BluetoothDevice device : devices) {
                String name;
                try { name = device.getName(); } catch (SecurityException e) { name = null; }
                if (TextUtils.isEmpty(name)) name = "Paired Bluetooth device";
                // IMPORTANT: third-party launchers cannot reliably switch A2DP output on Google TV.
                // This row takes the user to the OS Bluetooth panel for the actual connection.
                TextView row = action("♫  " + name, () -> openSettingsIntent(Settings.ACTION_BLUETOOTH_SETTINGS));
                column.addView(row, spacingParams());
            }
        }
        TextView settings = action("⚙  Open Bluetooth settings", () -> openSettingsIntent(Settings.ACTION_BLUETOOTH_SETTINGS));
        column.addView(settings, spacingParams());
        TextView notice = label("V1: choose the connection in Google's Bluetooth screen. Direct switching needs firmware testing.", 12, MUTED, false);
        notice.setPadding(0, dp(8), 0, 0);
        column.addView(notice);
        settings.requestFocus();
    }

    private LinearLayout.LayoutParams spacingParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(43));
        p.setMargins(0, 0, 0, dp(7));
        return p;
    }

    private List<BluetoothDevice> pairedAudioDevices() {
        List<BluetoothDevice> result = new ArrayList<>();
        try {
            BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null || !adapter.isEnabled()) return result;
            for (BluetoothDevice device : adapter.getBondedDevices()) {
                BluetoothClass clazz = device.getBluetoothClass();
                if (clazz != null && clazz.getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO) result.add(device);
            }
            result.sort(Comparator.comparing(d -> {
                String name = d.getName();
                return name == null ? "" : name.toLowerCase(Locale.ROOT);
            }));
        } catch (SecurityException ignored) { }
        return result;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_BT) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) showAudioPanel();
            else Toast.makeText(this, "Bluetooth permission is needed to list paired earbuds", Toast.LENGTH_LONG).show();
        }
    }

    private void hideAudioPanel() {
        if (root != null) {
            if (audioBackdrop != null) root.removeView(audioBackdrop);
            if (audioPanel != null) root.removeView(audioPanel);
        }
        audioBackdrop = null; audioPanel = null;
    }

    @Override public void onBackPressed() {
        if (audioPanel != null) hideAudioPanel();
        else if (!searchTerm.isEmpty()) { searchTerm = ""; renderHome(); }
        else super.onBackPressed();
    }

    @Override protected void onResume() {
        super.onResume();
        // Refresh app list after the user installs or removes an app.
        if (prefs != null && root != null) {
            loadInstalledApps();
            renderHome();
        }
    }
}
