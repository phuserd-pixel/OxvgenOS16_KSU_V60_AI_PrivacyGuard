package com.oxguard.offlineplayer;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_MEDIA_PERMISSION = 1001;
    private static final int REQUEST_PICK_VIDEO = 1002;

    private static final int COLOR_BACKGROUND = 0xFF101418;
    private static final int COLOR_PANEL = 0xFF161C22;
    private static final int COLOR_ROW = 0xFF141A20;
    private static final int COLOR_DIVIDER = 0xFF27313B;
    private static final int COLOR_TEXT = 0xFFF4F7FA;
    private static final int COLOR_TEXT_SECONDARY = 0xFFAEB8C2;
    private static final int COLOR_ACCENT = 0xFF4EA3FF;

    private final ArrayList<VideoItem> allVideos = new ArrayList<>();
    private final ArrayList<VideoItem> visibleVideos = new ArrayList<>();

    private LinearLayout rootLayout;
    private LinearLayout libraryPanel;
    private View toolbarView;
    private VideoView videoView;
    private TextView nowPlayingView;
    private TextView stateView;
    private TextView countView;
    private EditText searchView;
    private Button fullScreenButton;
    private VideoAdapter videoAdapter;
    private MediaController mediaController;
    private SharedPreferences preferences;

    private Uri currentUri;
    private String currentTitle = "未选择视频";
    private int pendingSeekMs;
    private boolean fullScreen;
    private boolean playWhenReady = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("offline_player", MODE_PRIVATE);
        buildLayout();
        prepareLibrary();
        restoreLastPlayback();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        int resumePosition = videoView == null ? pendingSeekMs : videoView.getCurrentPosition();
        Uri resumeUri = currentUri;
        String resumeTitle = currentTitle;
        super.onConfigurationChanged(newConfig);
        pendingSeekMs = resumePosition;
        buildLayout();
        filterVideos();
        if (resumeUri != null) {
            playUri(resumeUri, resumeTitle, true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (currentUri != null) {
            preferences.edit()
                    .putString("last_uri", currentUri.toString())
                    .putString("last_title", currentTitle)
                    .putInt("last_position", videoView == null ? 0 : videoView.getCurrentPosition())
                    .apply();
        }
    }

    @Override
    public void onBackPressed() {
        if (fullScreen) {
            setFullScreen(false);
            return;
        }
        super.onBackPressed();
    }

    private void buildLayout() {
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(COLOR_BACKGROUND);
        setContentView(rootLayout);

        LinearLayout playerPanel = new LinearLayout(this);
        playerPanel.setOrientation(LinearLayout.VERTICAL);
        playerPanel.setBackgroundColor(COLOR_BACKGROUND);

        libraryPanel = new LinearLayout(this);
        libraryPanel.setOrientation(LinearLayout.VERTICAL);
        libraryPanel.setBackgroundColor(COLOR_PANEL);

        if (landscape) {
            rootLayout.addView(playerPanel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.45f));
            rootLayout.addView(libraryPanel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        } else {
            rootLayout.addView(playerPanel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
            rootLayout.addView(libraryPanel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        }

        toolbarView = buildToolbar();
        playerPanel.addView(toolbarView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        playerPanel.addView(buildVideoFrame(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));

        libraryPanel.addView(buildSearchRow(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        libraryPanel.addView(buildVideoList(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));

        setFullScreen(fullScreen);
    }

    private View buildToolbar() {
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(12), dp(10), dp(12), dp(10));
        toolbar.setBackgroundColor(COLOR_PANEL);

        nowPlayingView = new TextView(this);
        nowPlayingView.setText(currentTitle);
        nowPlayingView.setTextColor(COLOR_TEXT);
        nowPlayingView.setTextSize(16);
        nowPlayingView.setSingleLine(true);
        nowPlayingView.setEllipsize(TextUtils.TruncateAt.END);
        toolbar.addView(nowPlayingView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        Button refreshButton = toolbarButton("刷新");
        refreshButton.setOnClickListener(v -> prepareLibrary());
        toolbar.addView(refreshButton);

        Button pickButton = toolbarButton("选择");
        pickButton.setOnClickListener(v -> openSystemPicker());
        toolbar.addView(pickButton);

        fullScreenButton = toolbarButton(fullScreen ? "退出" : "全屏");
        fullScreenButton.setOnClickListener(v -> setFullScreen(!fullScreen));
        toolbar.addView(fullScreenButton);

        return toolbar;
    }

    private View buildVideoFrame() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);

        videoView = new VideoView(this);
        mediaController = new MediaController(this);
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);
        videoView.setOnPreparedListener(mediaPlayer -> {
            stateView.setVisibility(View.GONE);
            if (pendingSeekMs > 0) {
                videoView.seekTo(pendingSeekMs);
                pendingSeekMs = 0;
            }
            if (playWhenReady) {
                videoView.start();
            }
            mediaController.show(2500);
        });
        videoView.setOnErrorListener((mediaPlayer, what, extra) -> {
            showState("无法播放这个视频，可能是编码不被系统播放器支持");
            return true;
        });
        videoView.setOnCompletionListener(mediaPlayer -> showState("播放完成"));
        frame.addView(videoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
        ));

        stateView = new TextView(this);
        stateView.setText("选择一个本地视频开始播放");
        stateView.setTextColor(COLOR_TEXT_SECONDARY);
        stateView.setTextSize(15);
        stateView.setGravity(Gravity.CENTER);
        stateView.setPadding(dp(20), dp(20), dp(20), dp(20));
        frame.addView(stateView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        return frame;
    }

    private View buildSearchRow() {
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(dp(12), dp(10), dp(12), dp(8));
        searchRow.setBackgroundColor(COLOR_PANEL);

        searchView = new EditText(this);
        searchView.setSingleLine(true);
        searchView.setHint("搜索本地视频");
        searchView.setTextColor(COLOR_TEXT);
        searchView.setHintTextColor(COLOR_TEXT_SECONDARY);
        searchView.setTextSize(15);
        searchView.setPadding(dp(10), 0, dp(10), 0);
        searchView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterVideos();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        searchRow.addView(searchView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        countView = new TextView(this);
        countView.setText("0/0");
        countView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        countView.setTextColor(COLOR_TEXT_SECONDARY);
        countView.setTextSize(13);
        searchRow.addView(countView, new LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT));

        return searchRow;
    }

    private View buildVideoList() {
        ListView listView = new ListView(this);
        listView.setBackgroundColor(COLOR_BACKGROUND);
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setDivider(new ColorDrawable(COLOR_DIVIDER));
        listView.setDividerHeight(1);
        listView.setPadding(0, 0, 0, dp(8));
        listView.setClipToPadding(false);

        videoAdapter = new VideoAdapter();
        listView.setAdapter(videoAdapter);
        listView.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            VideoItem item = visibleVideos.get(position);
            pendingSeekMs = 0;
            playUri(item.uri, item.title, true);
        });
        return listView;
    }

    private Button toolbarButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        button.setTextColor(COLOR_TEXT);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(10), dp(6), dp(10), dp(6));
        button.setBackgroundColor(COLOR_DIVIDER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = dp(8);
        button.setLayoutParams(params);
        return button;
    }

    private void prepareLibrary() {
        if (hasVideoLibraryPermission()) {
            loadVideos();
        } else {
            showState("需要视频读取权限才能扫描本地视频；也可以点“选择”手动打开单个视频");
            requestVideoLibraryPermission();
        }
    }

    private boolean hasVideoLibraryPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= 34) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestVideoLibraryPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 34) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            }, REQUEST_MEDIA_PERMISSION);
        } else if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO}, REQUEST_MEDIA_PERMISSION);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_MEDIA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MEDIA_PERMISSION) {
            if (hasVideoLibraryPermission()) {
                loadVideos();
            } else {
                showState("未授予视频读取权限。可以继续点“选择”打开单个本地视频");
            }
        }
    }

    private void loadVideos() {
        showState("正在扫描本地视频...");
        new Thread(() -> {
            ArrayList<VideoItem> scanned = scanVideos();
            runOnUiThread(() -> {
                allVideos.clear();
                allVideos.addAll(scanned);
                filterVideos();
                if (allVideos.isEmpty() && currentUri == null) {
                    showState("没有扫描到视频。点“选择”可手动打开本地视频文件");
                } else if (currentUri == null) {
                    showState("选择一个本地视频开始播放");
                }
            });
        }, "video-scan").start();
    }

    @SuppressWarnings("deprecation")
    private ArrayList<VideoItem> scanVideos() {
        ArrayList<VideoItem> result = new ArrayList<>();
        ArrayList<String> columns = new ArrayList<>();
        columns.add(MediaStore.Video.Media._ID);
        columns.add(MediaStore.Video.Media.DISPLAY_NAME);
        columns.add(MediaStore.Video.Media.DURATION);
        columns.add(MediaStore.Video.Media.SIZE);
        columns.add(MediaStore.Video.Media.DATE_MODIFIED);
        if (Build.VERSION.SDK_INT >= 29) {
            columns.add(MediaStore.Video.Media.RELATIVE_PATH);
        } else {
            columns.add(MediaStore.Video.Media.DATA);
        }

        try (Cursor cursor = getContentResolver().query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                columns.toArray(new String[0]),
                null,
                null,
                MediaStore.Video.Media.DATE_MODIFIED + " DESC"
        )) {
            if (cursor == null) {
                return result;
            }

            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
            int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
            int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
            int pathColumn = Build.VERSION.SDK_INT >= 29
                    ? cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                    : cursor.getColumnIndex(MediaStore.Video.Media.DATA);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                String name = cursor.getString(nameColumn);
                long duration = cursor.getLong(durationColumn);
                long size = cursor.getLong(sizeColumn);
                String path = pathColumn >= 0 ? cursor.getString(pathColumn) : "";
                Uri uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                result.add(new VideoItem(name, path, uri, duration, size));
            }
        } catch (SecurityException securityException) {
            runOnUiThread(() -> showState("权限不足，无法扫描视频库"));
        }
        return result;
    }

    private void filterVideos() {
        String query = searchView == null ? "" : searchView.getText().toString().trim().toLowerCase(Locale.ROOT);
        visibleVideos.clear();
        for (VideoItem item : allVideos) {
            if (query.isEmpty()
                    || item.title.toLowerCase(Locale.ROOT).contains(query)
                    || item.subtitle.toLowerCase(Locale.ROOT).contains(query)) {
                visibleVideos.add(item);
            }
        }
        if (videoAdapter != null) {
            videoAdapter.notifyDataSetChanged();
        }
        if (countView != null) {
            countView.setText(visibleVideos.size() + "/" + allVideos.size());
        }
    }

    private void openSystemPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_PICK_VIDEO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_VIDEO && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Some document providers only grant temporary read access.
            }
            pendingSeekMs = 0;
            playUri(uri, displayNameForUri(uri), true);
        }
    }

    private void playUri(Uri uri, String title, boolean autoStart) {
        currentUri = uri;
        currentTitle = TextUtils.isEmpty(title) ? "本地视频" : title;
        playWhenReady = autoStart;
        if (nowPlayingView != null) {
            nowPlayingView.setText(currentTitle);
        }
        showState("正在打开：" + currentTitle);
        videoView.setVideoURI(uri);
        videoView.requestFocus();
    }

    private void restoreLastPlayback() {
        String uriValue = preferences.getString("last_uri", null);
        if (TextUtils.isEmpty(uriValue)) {
            return;
        }
        currentTitle = preferences.getString("last_title", "上次播放");
        pendingSeekMs = preferences.getInt("last_position", 0);
        playUri(Uri.parse(uriValue), currentTitle, true);
    }

    private String displayNameForUri(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameColumn >= 0) {
                    String name = cursor.getString(nameColumn);
                    if (!TextUtils.isEmpty(name)) {
                        return name;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        String lastSegment = uri.getLastPathSegment();
        return TextUtils.isEmpty(lastSegment) ? "本地视频" : lastSegment;
    }

    private void setFullScreen(boolean enabled) {
        fullScreen = enabled;
        if (toolbarView != null) {
            toolbarView.setVisibility(enabled ? View.GONE : View.VISIBLE);
        }
        if (libraryPanel != null) {
            libraryPanel.setVisibility(enabled ? View.GONE : View.VISIBLE);
        }
        if (fullScreenButton != null) {
            fullScreenButton.setText(enabled ? "退出" : "全屏");
        }
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (enabled) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                }
            }
        } else {
            View decorView = getWindow().getDecorView();
            if (enabled) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                decorView.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        }
    }

    private void showState(String message) {
        if (stateView != null) {
            stateView.setVisibility(View.VISIBLE);
            stateView.setText(message);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String formatDuration(long durationMs) {
        if (durationMs <= 0) {
            return "--:--";
        }
        long totalSeconds = durationMs / 1000;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = totalSeconds / 3600;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) {
            return "";
        }
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    private class VideoAdapter extends ArrayAdapter<VideoItem> {
        VideoAdapter() {
            super(MainActivity.this, android.R.layout.simple_list_item_2, visibleVideos);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            VideoItem item = visibleVideos.get(position);
            TextView titleView = view.findViewById(android.R.id.text1);
            TextView subtitleView = view.findViewById(android.R.id.text2);

            titleView.setText(item.title);
            titleView.setTextColor(COLOR_TEXT);
            titleView.setTextSize(15);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);

            String sizeText = formatSize(item.sizeBytes);
            String extra = TextUtils.isEmpty(sizeText)
                    ? formatDuration(item.durationMs)
                    : formatDuration(item.durationMs) + " · " + sizeText;
            subtitleView.setText(TextUtils.isEmpty(item.subtitle) ? extra : extra + " · " + item.subtitle);
            subtitleView.setTextColor(COLOR_TEXT_SECONDARY);
            subtitleView.setTextSize(12);
            subtitleView.setSingleLine(true);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);

            view.setBackgroundColor(COLOR_ROW);
            view.setPadding(dp(8), dp(6), dp(8), dp(6));
            return view;
        }
    }

    private static class VideoItem {
        final String title;
        final String subtitle;
        final Uri uri;
        final long durationMs;
        final long sizeBytes;

        VideoItem(String title, String subtitle, Uri uri, long durationMs, long sizeBytes) {
            this.title = TextUtils.isEmpty(title) ? "未命名视频" : title;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.uri = uri;
            this.durationMs = durationMs;
            this.sizeBytes = sizeBytes;
        }
    }
}
