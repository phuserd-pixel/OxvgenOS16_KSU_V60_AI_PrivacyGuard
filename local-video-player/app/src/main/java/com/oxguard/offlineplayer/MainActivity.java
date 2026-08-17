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
import android.media.MediaPlayer;
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
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.MediaController;
import android.widget.SeekBar;
import android.widget.TextView;

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

    private final ArrayList<VideoItem> allVideos = new ArrayList<>();
    private final ArrayList<VideoItem> visibleVideos = new ArrayList<>();

    private LinearLayout libraryPanel;
    private View toolbarView;
    private AdaptiveVideoView videoView;
    private TextView nowPlayingView;
    private TextView stateView;
    private TextView countView;
    private TextView volumeView;
    private Button resizeModeButton;
    private Button clarityButton;
    private EditText searchView;
    private VideoAdapter videoAdapter;
    private MediaController mediaController;
    private SharedPreferences preferences;

    private Uri currentUri;
    private MediaPlayer activeMediaPlayer;
    private String currentTitle = "未选择视频";
    private int pendingSeekMs;
    private int resizeMode = AdaptiveVideoView.MODE_FIT;
    private float appVolume = 1.0f;
    private boolean fullScreen;
    private boolean clarityMode;
    private boolean playWhenReady = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            preferences = getSharedPreferences("offline_player", MODE_PRIVATE);
            restoreSettings();
            buildLayoutSafely();
            showPreviousCrashIfAny();
            loadVideosIfPermitted(false);
            handleIncomingIntent(getIntent());
        } catch (Throwable throwable) {
            PlayerApplication.writeCrash(this, throwable);
            showFallbackCrashScreen(throwable);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Uri resumeUri = currentUri;
        String resumeTitle = currentTitle;
        int resumePosition = safeCurrentPosition();
        super.onConfigurationChanged(newConfig);
        try {
            pendingSeekMs = resumePosition;
            buildLayoutSafely();
            filterVideos();
            if (resumeUri != null) {
                playUri(resumeUri, resumeTitle, true);
            }
        } catch (Throwable throwable) {
            PlayerApplication.writeCrash(this, throwable);
            showFallbackCrashScreen(throwable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (currentUri != null && preferences != null) {
                preferences.edit()
                        .putString("last_uri", currentUri.toString())
                        .putString("last_title", currentTitle)
                        .putInt("last_position", safeCurrentPosition())
                        .apply();
            }
        } catch (Throwable throwable) {
            PlayerApplication.writeCrash(this, throwable);
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

    private void buildLayoutSafely() {
        try {
            buildLayout();
        } catch (Throwable throwable) {
            PlayerApplication.writeCrash(this, throwable);
            showFallbackCrashScreen(throwable);
        }
    }

    private void buildLayout() {
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        LinearLayout rootLayout = new LinearLayout(this);
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
        playerPanel.addView(buildPlaybackControlRow(), new LinearLayout.LayoutParams(
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
        applyFullScreenFlags(false);
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

        Button pickButton = toolbarButton("选择");
        pickButton.setOnClickListener(v -> openSystemPicker());
        toolbar.addView(pickButton);

        Button scanButton = toolbarButton("扫描");
        scanButton.setOnClickListener(v -> loadVideosIfPermitted(true));
        toolbar.addView(scanButton);

        Button resumeButton = toolbarButton("继续");
        resumeButton.setOnClickListener(v -> resumeLastPlayback());
        toolbar.addView(resumeButton);

        resizeModeButton = toolbarButton(resizeModeText());
        resizeModeButton.setOnClickListener(v -> cycleResizeMode());
        toolbar.addView(resizeModeButton);

        clarityButton = toolbarButton(clarityMode ? "清晰开" : "清晰关");
        clarityButton.setOnClickListener(v -> toggleClarityMode());
        toolbar.addView(clarityButton);

        Button fullScreenButton = toolbarButton(fullScreen ? "退出" : "全屏");
        fullScreenButton.setOnClickListener(v -> setFullScreen(!fullScreen));
        toolbar.addView(fullScreenButton);

        return toolbar;
    }

    private View buildPlaybackControlRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(6), dp(12), dp(6));
        row.setBackgroundColor(COLOR_BACKGROUND);

        volumeView = new TextView(this);
        volumeView.setTextColor(COLOR_TEXT_SECONDARY);
        volumeView.setTextSize(13);
        updateVolumeText();
        row.addView(volumeView, new LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT));

        SeekBar volumeSeekBar = new SeekBar(this);
        volumeSeekBar.setMax(100);
        volumeSeekBar.setProgress(Math.round(appVolume * 100));
        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                appVolume = Math.max(0.0f, Math.min(1.0f, progress / 100.0f));
                updateVolumeText();
                applyAppVolume();
                saveSettings();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        row.addView(volumeSeekBar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        return row;
    }

    private View buildVideoFrame() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);

        videoView = new AdaptiveVideoView(this);
        videoView.setResizeMode(resizeMode);
        mediaController = new MediaController(this);
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);
        videoView.setOnPreparedListener(mediaPlayer -> {
            try {
                activeMediaPlayer = mediaPlayer;
                videoView.setVideoSize(mediaPlayer.getVideoWidth(), mediaPlayer.getVideoHeight());
                applyAppVolume();
                applyClarityMode();
                hideState();
                if (pendingSeekMs > 0) {
                    videoView.seekTo(pendingSeekMs);
                    pendingSeekMs = 0;
                }
                if (playWhenReady) {
                    videoView.start();
                }
                mediaController.show(2500);
            } catch (Throwable throwable) {
                PlayerApplication.writeCrash(this, throwable);
                showState("播放启动失败：" + safeMessage(throwable));
            }
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
        stateView.setText("点“选择”打开单个本地视频，或点“扫描”读取视频库");
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
            if (position >= 0 && position < visibleVideos.size()) {
                VideoItem item = visibleVideos.get(position);
                pendingSeekMs = 0;
                playUri(item.uri, item.title, true);
            }
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

    private void restoreSettings() {
        if (preferences == null) {
            return;
        }
        appVolume = preferences.getFloat("app_volume", 1.0f);
        resizeMode = preferences.getInt("resize_mode", AdaptiveVideoView.MODE_FIT);
        clarityMode = preferences.getBoolean("clarity_mode", false);
    }

    private void saveSettings() {
        if (preferences == null) {
            return;
        }
        preferences.edit()
                .putFloat("app_volume", appVolume)
                .putInt("resize_mode", resizeMode)
                .putBoolean("clarity_mode", clarityMode)
                .apply();
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        Uri uri = intent.getData();
        if (uri == null) {
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_SEND.equals(action)) {
            pendingSeekMs = 0;
            playUri(uri, displayNameForUri(uri), true);
        }
    }

    private void cycleResizeMode() {
        if (resizeMode == AdaptiveVideoView.MODE_FIT) {
            resizeMode = AdaptiveVideoView.MODE_CROP;
        } else if (resizeMode == AdaptiveVideoView.MODE_CROP) {
            resizeMode = AdaptiveVideoView.MODE_STRETCH;
        } else if (resizeMode == AdaptiveVideoView.MODE_STRETCH) {
            resizeMode = AdaptiveVideoView.MODE_ORIGINAL;
        } else {
            resizeMode = AdaptiveVideoView.MODE_FIT;
        }
        if (videoView != null) {
            videoView.setResizeMode(resizeMode);
        }
        if (resizeModeButton != null) {
            resizeModeButton.setText(resizeModeText());
        }
        saveSettings();
        showState("画面比例：" + resizeModeText());
    }

    private String resizeModeText() {
        if (resizeMode == AdaptiveVideoView.MODE_CROP) {
            return "填充";
        }
        if (resizeMode == AdaptiveVideoView.MODE_STRETCH) {
            return "拉伸";
        }
        if (resizeMode == AdaptiveVideoView.MODE_ORIGINAL) {
            return "原始";
        }
        return "适应";
    }

    private void toggleClarityMode() {
        clarityMode = !clarityMode;
        if (clarityButton != null) {
            clarityButton.setText(clarityMode ? "清晰开" : "清晰关");
        }
        applyClarityMode();
        saveSettings();
        showState(clarityMode
                ? "清晰增强已开启：优化播放缩放和亮屏，不还原打码内容"
                : "清晰增强已关闭");
    }

    private void applyAppVolume() {
        if (activeMediaPlayer == null) {
            return;
        }
        try {
            activeMediaPlayer.setVolume(appVolume, appVolume);
        } catch (Throwable throwable) {
            PlayerApplication.writeCrash(this, throwable);
        }
    }

    private void applyClarityMode() {
        try {
            if (videoView != null) {
                videoView.setKeepScreenOn(clarityMode);
                videoView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
            }
            if (activeMediaPlayer != null) {
                activeMediaPlayer.setScreenOnWhilePlaying(clarityMode);
                activeMediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            }
        } catch (Throwable throwable) {
            PlayerApplication.writeCrash(this, throwable);
        }
    }

    private void updateVolumeText() {
        if (volumeView != null) {
            volumeView.setText("音量 " + Math.round(appVolume * 100) + "%");
        }
    }

    private void loadVideosIfPermitted(boolean requestIfNeeded) {
        if (hasVideoLibraryPermission()) {
            loadVideos();
            return;
        }
        if (requestIfNeeded) {
            requestVideoLibraryPermission();
        } else {
            showState("未扫描视频库。点“选择”可直接打开视频；点“扫描”会请求读取权限");
        }
    }

    private boolean hasVideoLibraryPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestVideoLibraryPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            loadVideos();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33) {
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
                showState("未授予视频读取权限。仍可点“选择”打开单个视频");
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
                    showState("已扫描到 " + allVideos.size() + " 个视频，点列表开始播放");
                }
            });
        }, "video-scan").start();
    }

    private ArrayList<VideoItem> scanVideos() {
        ArrayList<VideoItem> result = new ArrayList<>();
        String[] columns = new String[]{
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE
        };
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    columns,
                    null,
                    null,
                    MediaStore.Video.Media.DATE_MODIFIED + " DESC"
            );
        } catch (Throwable firstQueryError) {
            PlayerApplication.writeCrash(this, firstQueryError);
            try {
                cursor = getContentResolver().query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        columns,
                        null,
                        null,
                        null
                );
            } catch (Throwable secondQueryError) {
                PlayerApplication.writeCrash(this, secondQueryError);
                runOnUiThread(() -> showState("扫描视频库失败：" + safeMessage(secondQueryError)));
                return result;
            }
        }

        if (cursor == null) {
            return result;
        }

        try {
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
            int durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION);
            int sizeColumn = cursor.getColumnIndex(MediaStore.Video.Media.SIZE);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                String name = cursor.getString(nameColumn);
                long duration = durationColumn >= 0 ? cursor.getLong(durationColumn) : 0L;
                long size = sizeColumn >= 0 ? cursor.getLong(sizeColumn) : 0L;
                Uri uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                result.add(new VideoItem(name, "", uri, duration, size));
            }
        } catch (Throwable throwable) {
            PlayerApplication.writeCrash(this, throwable);
            runOnUiThread(() -> showState("读取视频列表失败：" + safeMessage(throwable)));
        } finally {
            cursor.close();
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
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_PICK_VIDEO);
        } catch (Throwable throwable) {
            PlayerApplication.writeCrash(this, throwable);
            showState("无法打开文件选择器：" + safeMessage(throwable));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_VIDEO && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignored) {
                // Some document providers only grant temporary read access.
            }
            pendingSeekMs = 0;
            playUri(uri, displayNameForUri(uri), true);
        }
    }

    private void playUri(Uri uri, String title, boolean autoStart) {
        try {
            currentUri = uri;
            currentTitle = TextUtils.isEmpty(title) ? "本地视频" : title;
            playWhenReady = autoStart;
            if (nowPlayingView != null) {
                nowPlayingView.setText(currentTitle);
            }
            showState("正在打开：" + currentTitle);
            videoView.setVideoURI(uri);
            videoView.requestFocus();
        } catch (Throwable throwable) {
            PlayerApplication.writeCrash(this, throwable);
            showState("打开视频失败：" + safeMessage(throwable));
        }
    }

    private void resumeLastPlayback() {
        if (preferences == null) {
            showState("没有可恢复的视频");
            return;
        }
        String uriValue = preferences.getString("last_uri", null);
        if (TextUtils.isEmpty(uriValue)) {
            showState("没有上次播放记录");
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
        } catch (Throwable throwable) {
            PlayerApplication.writeCrash(this, throwable);
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
        applyFullScreenFlags(enabled);
    }

    private void applyFullScreenFlags(boolean enabled) {
        View decorView = getWindow().getDecorView();
        decorView.post(() -> {
            try {
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
            } catch (Throwable throwable) {
                PlayerApplication.writeCrash(this, throwable);
            }
        });
    }

    private void showPreviousCrashIfAny() {
        String crash = PlayerApplication.readCrash(this);
        if (!TextUtils.isEmpty(crash)) {
            String firstLine = crash.split("\\n", 2)[0];
            showState("检测到上次闪退，已启用稳定启动模式。\n" + firstLine);
        }
    }

    private void showFallbackCrashScreen(Throwable throwable) {
        TextView textView = new TextView(this);
        textView.setTextColor(COLOR_TEXT);
        textView.setBackgroundColor(COLOR_BACKGROUND);
        textView.setTextSize(15);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(dp(20), dp(20), dp(20), dp(20));
        textView.setText("播放器启动失败，已保存 crash.txt\n\n" + safeMessage(throwable));
        setContentView(textView);
    }

    private void showState(String message) {
        if (stateView != null) {
            stateView.setVisibility(View.VISIBLE);
            stateView.setText(message);
        }
    }

    private void hideState() {
        if (stateView != null) {
            stateView.setVisibility(View.GONE);
        }
    }

    private int safeCurrentPosition() {
        try {
            return videoView == null ? pendingSeekMs : videoView.getCurrentPosition();
        } catch (Throwable throwable) {
            PlayerApplication.writeCrash(this, throwable);
            return pendingSeekMs;
        }
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        String message = throwable.getMessage();
        if (TextUtils.isEmpty(message)) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + message;
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

    private class VideoAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return visibleVideos.size();
        }

        @Override
        public Object getItem(int position) {
            return visibleVideos.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView titleView;
            TextView subtitleView;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
                titleView = (TextView) row.getChildAt(0);
                subtitleView = (TextView) row.getChildAt(1);
            } else {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(12), dp(8), dp(12), dp(8));
                row.setBackgroundColor(COLOR_ROW);

                titleView = new TextView(MainActivity.this);
                titleView.setTextColor(COLOR_TEXT);
                titleView.setTextSize(15);
                titleView.setSingleLine(true);
                titleView.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(titleView, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));

                subtitleView = new TextView(MainActivity.this);
                subtitleView.setTextColor(COLOR_TEXT_SECONDARY);
                subtitleView.setTextSize(12);
                subtitleView.setSingleLine(true);
                subtitleView.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(subtitleView, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }

            VideoItem item = visibleVideos.get(position);
            titleView.setText(item.title);
            String sizeText = formatSize(item.sizeBytes);
            String extra = TextUtils.isEmpty(sizeText)
                    ? formatDuration(item.durationMs)
                    : formatDuration(item.durationMs) + " · " + sizeText;
            subtitleView.setText(TextUtils.isEmpty(item.subtitle) ? extra : extra + " · " + item.subtitle);
            return row;
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
