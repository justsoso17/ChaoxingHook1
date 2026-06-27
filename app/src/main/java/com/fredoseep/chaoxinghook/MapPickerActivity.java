package com.fredoseep.chaoxinghook;

import com.fredoseep.chaoxinghook.BuildConfig;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapPickerActivity extends AppCompatActivity {

    private static final String AMAP_KEY = BuildConfig.AMAP_WEB_KEY;
    private static final String SEARCH_URL = "https://restapi.amap.com/v3/place/text";
    private static final String TIPS_URL = "https://restapi.amap.com/v3/assistant/inputtips";

    private MapView mapView;
    private AMap aMap;
    private EditText etSearch;
    private TextView tvLat;
    private TextView tvLng;
    private TextView tvHint;
    private LinearLayout layoutSuggestions;
    private LatLng selectedLatLng;
    private boolean ignoreTextChange;
    private float touchDownX, touchDownY;
    private long touchDownTime;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);

        setContentView(R.layout.activity_map_picker);

        View mapContainer = findViewById(R.id.map_container);
        mapView = new MapView(MapPickerActivity.this);
        mapView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        ((FrameLayout) mapContainer).addView(mapView, 0);
        mapView.onCreate(savedInstanceState);

        etSearch = findViewById(R.id.et_search);
        tvLat = findViewById(R.id.tv_lat);
        tvLng = findViewById(R.id.tv_lng);
        tvHint = findViewById(R.id.tv_hint);
        layoutSuggestions = findViewById(R.id.layout_suggestions);
        View btnConfirm = findViewById(R.id.btn_confirm);
        View btnBack = findViewById(R.id.btn_back);
        View btnSearch = findViewById(R.id.btn_search);

        // Add status bar height to top padding
        LinearLayout searchArea = (LinearLayout) etSearch.getParent().getParent();
        int statusBarHeight = getStatusBarHeight();
        searchArea.setPadding(
                searchArea.getPaddingLeft(),
                searchArea.getPaddingTop() + statusBarHeight,
                searchArea.getPaddingRight(),
                searchArea.getPaddingBottom());

        btnBack.setOnClickListener(v -> finish());

        aMap = mapView.getMap();
        if (aMap != null) {
            aMap.getUiSettings().setZoomControlsEnabled(false);
            aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(39.90923, 116.397428), 15));

            mapView.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchDownX = event.getX();
                        touchDownY = event.getY();
                        touchDownTime = System.currentTimeMillis();
                        break;
                    case MotionEvent.ACTION_UP:
                        float dx = event.getX() - touchDownX;
                        float dy = event.getY() - touchDownY;
                        long dt = System.currentTimeMillis() - touchDownTime;
                        if (Math.abs(dx) < 30 && Math.abs(dy) < 30 && dt < 300) {
                            android.graphics.Point pt = new android.graphics.Point((int) event.getX(), (int) event.getY());
                            onMapClick(aMap.getProjection().fromScreenLocation(pt));
                        }
                        break;
                }
                return false;
            });
        }

        TextWatcher searchTextWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (ignoreTextChange) return;
                String keyword = s.toString().trim();
                if (keyword.isEmpty()) {
                    layoutSuggestions.setVisibility(View.GONE);
                    return;
                }
                fetchSuggestions(keyword);
            }
        };
        etSearch.addTextChangedListener(searchTextWatcher);

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                layoutSuggestions.setVisibility(View.GONE);
                doSearch(etSearch.getText().toString().trim());
                return true;
            }
            return false;
        });

        btnSearch.setOnClickListener(v -> {
            layoutSuggestions.setVisibility(View.GONE);
            doSearch(etSearch.getText().toString().trim());
        });

        btnConfirm.setOnClickListener(v -> {
            if (selectedLatLng == null) {
                Toast.makeText(this, "请先点击地图选取坐标", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent result = new Intent();
            result.putExtra("latitude", selectedLatLng.latitude);
            result.putExtra("longitude", selectedLatLng.longitude);
            setResult(RESULT_OK, result);
            finish();
        });
    }

    // org.json optString returns "[]" when the field is a JSONArray — treat that as empty
    private String safeOptString(JSONObject obj, String key) {
        Object val = obj.opt(key);
        if (val == null) return "";
        if (val instanceof JSONArray) return "";
        String s = val.toString();
        return "[]".equals(s) ? "" : s;
    }

    private int getStatusBarHeight() {
        try {
            Resources resources = getResources();
            int id = resources.getIdentifier("status_bar_height", "dimen", "android");
            if (id > 0) return resources.getDimensionPixelSize(id);
        } catch (Exception ignored) {}
        return 72; // fallback dp
    }

    private void fetchSuggestions(String keyword) {
        executor.execute(() -> {
            try {
                String urlStr = TIPS_URL + "?key=" + AMAP_KEY
                        + "&keywords=" + URLEncoder.encode(keyword, "UTF-8")
                        + "&datatype=all";
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();

                JSONObject json = new JSONObject(sb.toString());
                mainHandler.post(() -> buildSuggestionList(json));
            } catch (Exception ignored) {
            }
        });
    }

    private void buildSuggestionList(JSONObject json) {
        try {
            JSONArray tips = json.optJSONArray("tips");
            if (tips == null || tips.length() == 0) {
                layoutSuggestions.setVisibility(View.GONE);
                return;
            }

            layoutSuggestions.removeAllViews();
            int count = Math.min(tips.length(), 6);

            for (int i = 0; i < count; i++) {
                JSONObject tip = tips.getJSONObject(i);
                final String name = safeOptString(tip, "name");
                final String location = safeOptString(tip, "location");

                TextView item = new TextView(this);
                item.setText(name);
                item.setTextSize(14);
                item.setTextColor(0xFF2D2640);
                item.setPadding(32, 22, 32, 22);
                item.setMaxLines(1);
                item.setEllipsize(android.text.TextUtils.TruncateAt.END);
                item.setClickable(true);
                item.setFocusable(true);

                item.setOnClickListener(v -> {
                    ignoreTextChange = true;
                    etSearch.setText(name);
                    ignoreTextChange = false;
                    layoutSuggestions.setVisibility(View.GONE);

                    if (!location.isEmpty() && location.contains(",")) {
                        String[] parts = location.split(",");
                        double lng = Double.parseDouble(parts[0]);
                        double lat = Double.parseDouble(parts[1]);
                        selectLocation(lat, lng);
                    } else {
                        doSearch(name);
                    }
                });

                layoutSuggestions.addView(item);

                if (i < count - 1) {
                    View divider = new View(this);
                    divider.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1));
                    divider.setBackgroundColor(0x1A000000);
                    layoutSuggestions.addView(divider);
                }
            }

            layoutSuggestions.setVisibility(View.VISIBLE);
        } catch (Exception ignored) {
            layoutSuggestions.setVisibility(View.GONE);
        }
    }

    private void selectLocation(double lat, double lng) {
        selectedLatLng = new LatLng(lat, lng);
        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLng, 17));
        tvLat.setText(String.format("%.6f", lat));
        tvLng.setText(String.format("%.6f", lng));
        tvHint.setVisibility(View.GONE);
    }

    private void doSearch(String keyword) {
        if (keyword.isEmpty()) return;
        executor.execute(() -> {
            try {
                String urlStr = SEARCH_URL + "?key=" + AMAP_KEY
                        + "&keywords=" + URLEncoder.encode(keyword, "UTF-8")
                        + "&offset=10&page=1&extensions=all";
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();

                JSONObject json = new JSONObject(sb.toString());
                mainHandler.post(() -> handleSearchResult(json));
            } catch (Exception e) {
                mainHandler.post(() ->
                        Toast.makeText(this, "搜索失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void handleSearchResult(JSONObject json) {
        try {
            if (!"1".equals(json.optString("status"))) {
                Toast.makeText(this, "搜索失败", Toast.LENGTH_SHORT).show();
                return;
            }
            JSONArray pois = json.optJSONArray("pois");
            if (pois == null || pois.length() == 0) {
                Toast.makeText(this, "未找到结果", Toast.LENGTH_SHORT).show();
                return;
            }

            JSONObject first = pois.getJSONObject(0);
            String location = safeOptString(first, "location");

            if (!location.isEmpty() && location.contains(",")) {
                String[] parts = location.split(",");
                double lng = Double.parseDouble(parts[0]);
                double lat = Double.parseDouble(parts[1]);
                selectLocation(lat, lng);
            }
        } catch (Exception e) {
            Toast.makeText(this, "解析结果失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void onMapClick(LatLng latLng) {
        selectedLatLng = latLng;
        tvLat.setText(String.format("%.6f", latLng.latitude));
        tvLng.setText(String.format("%.6f", latLng.longitude));
        tvHint.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
        executor.shutdown();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }
}
