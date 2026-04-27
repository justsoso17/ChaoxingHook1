package com.fredoseep.chaoxinghook;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MainHook implements IXposedHookLoadPackage {

    private static final Set<String> hookedWebViewClients = new HashSet<>();

    // ==========================================================
    // 混淆字典配置区
    // ==========================================================
    public static class ObfuscationMap {
        public static final String CLASS_SPLASH_VIEW_MODEL = "com.chaoxing.mobile.activity.SplashViewModel";
        public static final String METHOD_SPLASH_A = "a";
        public static final String CLASS_HOME_PAGE_HEADER = "com.chaoxing.mobile.study.home.mainpage.view.HomePageHeader";
        public static final String METHOD_HOME_HEADER_G = "g";
        public static final String CLASS_CATEGORY_HOLDER = "com.chaoxing.mobile.study.home.mainpage2.adapter.viewholder.MainRecordCategoryHolder";
        public static final String METHOD_CATEGORY_HOLDER_O = "o";
        public static final String FIELD_CATEGORY_HOLDER_TV_LEFT = "c";
        public static final String CLASS_MAIN_PAGE_RECORD_ADAPTER = "com.chaoxing.mobile.study.home.mainpage2.adapter.MainPageRecordAdapter";
        public static final String METHOD_ADAPTER_GET_ITEM_COUNT = "getItemCount";
        public static final String FIELD_ADAPTER_LIST_A = "a";
        public static final String FIELD_ADAPTER_LIST_F82688A = "f82688a";
        public static final String CLASS_DB_QUERY = "lo.b0";
        public static final String METHOD_DB_QUERY_W = "W";
        public static final String CLASS_CHAT_MANAGER_Q1 = "com.chaoxing.mobile.chat.manager.q1";
        public static final String METHOD_CHAT_MANAGER_C1 = "c1";
        public static final String CLASS_EM_CMD_MESSAGE_BODY = "com.hyphenate.chat.EMCmdMessageBody";
        public static final String METHOD_EM_CMD_ACTION = "action";
    }

    private static class SignConfig {
        boolean modifyLocation = false;
        String longitude = "";
        String latitude = "";
        boolean modifyAddress = false;
        String address = "";
        boolean modifyName = false;
        String name = "";
        // 核心：一键随机指纹开关
        boolean randomizeDeviceFlag = false;
    }

    private static SignConfig cachedConfig = null;
    private static long lastReadTime = 0;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.chaoxing.mobile")) {
            return;
        }

        // 1. 原有的基础功能屏蔽 (广告、推荐等)
        try { XposedBridge.hookAllMethods(XposedHelpers.findClass(ObfuscationMap.CLASS_SPLASH_VIEW_MODEL, lpparam.classLoader), ObfuscationMap.METHOD_SPLASH_A, new XC_MethodHook() { @Override protected void afterHookedMethod(MethodHookParam param) { param.setResult(null); } }); } catch (Throwable t) {}
        try { XposedBridge.hookAllMethods(XposedHelpers.findClass(ObfuscationMap.CLASS_HOME_PAGE_HEADER, lpparam.classLoader), ObfuscationMap.METHOD_HOME_HEADER_G, new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam param) { if (param.args.length > 0) param.args[0] = null; } }); } catch (Throwable t) {}
        try { XposedBridge.hookAllMethods(XposedHelpers.findClass(ObfuscationMap.CLASS_CATEGORY_HOLDER, lpparam.classLoader), ObfuscationMap.METHOD_CATEGORY_HOLDER_O, new XC_MethodHook() { @Override protected void afterHookedMethod(MethodHookParam param) { Object viewHolder = param.thisObject; android.widget.TextView tvLeft = (android.widget.TextView) XposedHelpers.getObjectField(viewHolder, ObfuscationMap.FIELD_CATEGORY_HOLDER_TV_LEFT); if (tvLeft != null && tvLeft.getText() != null) { String title = tvLeft.getText().toString(); if (title.contains("推荐") || title.contains("Recommend")) { android.view.View itemView = (android.view.View) XposedHelpers.getObjectField(viewHolder, "itemView"); if (itemView != null) { itemView.setVisibility(android.view.View.GONE); android.view.ViewGroup.LayoutParams layoutParams = itemView.getLayoutParams(); layoutParams.height = 0; layoutParams.width = 0; itemView.setLayoutParams(layoutParams); } } } } }); } catch (Throwable t) {}
        try { XposedBridge.hookAllMethods(XposedHelpers.findClass(ObfuscationMap.CLASS_MAIN_PAGE_RECORD_ADAPTER, lpparam.classLoader), ObfuscationMap.METHOD_ADAPTER_GET_ITEM_COUNT, new XC_MethodHook() { @Override protected void afterHookedMethod(MethodHookParam param) { try { java.util.List<?> listA = (java.util.List<?>) XposedHelpers.getObjectField(param.thisObject, ObfuscationMap.FIELD_ADAPTER_LIST_A); if (listA != null) param.setResult(listA.size()); } catch (NoSuchFieldError e) { java.util.List<?> listA = (java.util.List<?>) XposedHelpers.getObjectField(param.thisObject, ObfuscationMap.FIELD_ADAPTER_LIST_F82688A); if (listA != null) param.setResult(listA.size()); } } }); } catch (Throwable t) {}
        try { XposedBridge.hookAllMethods(XposedHelpers.findClass(ObfuscationMap.CLASS_DB_QUERY, lpparam.classLoader), ObfuscationMap.METHOD_DB_QUERY_W, new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam param) { if (param.args.length == 3 && param.args[2] instanceof Integer) { if ((Integer) param.args[2] == 3) param.args[2] = 15; } } }); } catch (Throwable t) {}
        try { XposedBridge.hookAllMethods(XposedHelpers.findClass(ObfuscationMap.CLASS_CHAT_MANAGER_Q1, lpparam.classLoader), ObfuscationMap.METHOD_CHAT_MANAGER_C1, new XC_MethodHook() { @Override protected void afterHookedMethod(MethodHookParam param) { Object result = param.getResult(); if (result instanceof java.util.List) { java.util.List<?> list = (java.util.List<?>) result; for (int i = list.size() - 1; i >= 0; i--) { Object info = list.get(i); if (info != null && info.getClass().getSimpleName().equals("ConversationInfo")) { if ((Integer) XposedHelpers.callMethod(info, "getType") == 20) { list.remove(i); } } } } } }); } catch (Throwable t) {}
        try { XposedBridge.hookAllMethods(XposedHelpers.findClass(ObfuscationMap.CLASS_EM_CMD_MESSAGE_BODY, lpparam.classLoader), ObfuscationMap.METHOD_EM_CMD_ACTION, new XC_MethodHook() { @Override protected void afterHookedMethod(MethodHookParam param) { Object result = param.getResult(); if (result != null && "REVOKE_FLAG".equals(result.toString())) { param.setResult("BLOCK_REVOKE_FLAG"); } } }); } catch (Throwable t) {}

        // 2. WebView 核心请求拦截 (位置、名字拦截)
        try {
            Class<?> webViewClass = XposedHelpers.findClass("android.webkit.WebView", lpparam.classLoader);
            XposedBridge.hookAllMethods(webViewClass, "setWebViewClient", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object webViewClient = param.args[0];
                    if (webViewClient == null) return;
                    Class<?> clientClass = webViewClient.getClass();
                    if (!hookedWebViewClients.add(clientClass.getName())) return;

                    Class<?> targetClass = clientClass;
                    while (targetClass != null && !targetClass.getName().equals("java.lang.Object")) {
                        XposedBridge.hookAllMethods(targetClass, "shouldInterceptRequest", new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam innerParam) throws Throwable {
                                String url = null;
                                Object requestObj = null;

                                for (Object arg : innerParam.args) {
                                    if (arg instanceof String) { url = (String) arg; }
                                    else if (arg != null && arg.getClass().getName().endsWith("WebResourceRequest")) {
                                        requestObj = arg;
                                        Object uriObj = XposedHelpers.callMethod(arg, "getUrl");
                                        if (uriObj != null) url = uriObj.toString();
                                    }
                                }
                                if (url == null) return;

                                // 考试风控拦截
                                if (url.startsWith("https://mooc1-api.chaoxing.com/keeper/api/receiveExamLogs") || url.contains("/exam-ans/exam/phone/exit-count")) {
                                    try {
                                        Class<?> responseClass = XposedHelpers.findClassIfExists("android.webkit.WebResourceResponse", lpparam.classLoader);
                                        if (responseClass != null) {
                                            String fakeResponse = "{\"status\":1,\"result\":true,\"msg\":\"success\",\"data\":null}";
                                            innerParam.setResult(XposedHelpers.newInstance(responseClass, "application/json", "utf-8", new java.io.ByteArrayInputStream(fakeResponse.getBytes("UTF-8"))));
                                        }
                                    } catch (Exception e) {}
                                    return;
                                }

                                // 网页端签到定位修改
                                if (url.contains("stuSignajax")) {
                                    SignConfig config = getSignConfig();
                                    String newUrlString = url;
                                    boolean hasModified = false;

                                    if (config.modifyLocation && !config.latitude.isEmpty() && !config.longitude.isEmpty()) {
                                        newUrlString = newUrlString.replaceAll("latitude=[^&]*", "latitude=" + config.latitude).replaceAll("longitude=[^&]*", "longitude=" + config.longitude);
                                        hasModified = true;
                                    }
                                    if (config.modifyAddress && !config.address.isEmpty()) {
                                        newUrlString = newUrlString.replaceAll("address=[^&]*", "address=" + URLEncoder.encode(config.address, "UTF-8"));
                                        hasModified = true;
                                    }
                                    if (config.modifyName && !config.name.isEmpty()) {
                                        newUrlString = newUrlString.replaceAll("name=[^&]*", "name=" + URLEncoder.encode(config.name, "UTF-8"));
                                        hasModified = true;
                                    }

                                    if (!hasModified) return;

                                    try {
                                        URL newUrl = new URL(newUrlString);
                                        HttpURLConnection conn = (HttpURLConnection) newUrl.openConnection();
                                        if (requestObj != null) {
                                            conn.setRequestMethod((String) XposedHelpers.callMethod(requestObj, "getMethod"));
                                            @SuppressWarnings("unchecked")
                                            Map<String, String> headers = (Map<String, String>) XposedHelpers.callMethod(requestObj, "getRequestHeaders");
                                            if (headers != null) {
                                                for (Map.Entry<String, String> entry : headers.entrySet()) {
                                                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                                                }
                                            }
                                        } else {
                                            conn.setRequestMethod("GET");
                                        }

                                        // 直接放行原始 Cookie
                                        String cookie = android.webkit.CookieManager.getInstance().getCookie(newUrlString);
                                        if (cookie != null) {
                                            conn.setRequestProperty("Cookie", cookie);
                                        }

                                        String contentType = conn.getContentType();
                                        Class<?> responseClass = XposedHelpers.findClassIfExists("android.webkit.WebResourceResponse", lpparam.classLoader);
                                        if (responseClass != null) {
                                            innerParam.setResult(XposedHelpers.newInstance(responseClass, (contentType != null) ? contentType.split(";")[0].trim() : "application/json", conn.getContentEncoding() != null ? conn.getContentEncoding() : "utf-8", conn.getInputStream()));
                                        }
                                    } catch (Exception e) {}
                                }
                            }
                        });
                        targetClass = targetClass.getSuperclass();
                    }
                }
            });

            // 3. 终极一击：WebView 底层 JS 自动化动态指纹注入
            XC_MethodHook jsInterceptHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length > 0 && param.args[0] instanceof String) {
                        String jsCode = (String) param.args[0];

                        // 拦截设备指纹下发
                        if (jsCode.contains("CLIENT_DEVICE_FLAG")) {
                            SignConfig config = getSignConfig();
                            if (config.randomizeDeviceFlag) {
                                int start = jsCode.indexOf('{');
                                int end = jsCode.lastIndexOf('}');
                                if (start != -1 && end != -1 && start < end) {
                                    // 擦除本地记忆 + 动态生成随机指纹注入
                                    String wipeMemory = "window.localStorage.clear(); window.sessionStorage.clear(); ";
                                    String randomFlagJson = generateRandomDeviceFlag();
                                    String newJsCode = wipeMemory + jsCode.substring(0, start) + randomFlagJson + jsCode.substring(end + 1);
                                    param.args[0] = newJsCode;
                                    XposedBridge.log("Chaoxing AdSkip: 随机假指纹已自动生成并注入，草台班子被拿捏 -> " + randomFlagJson);
                                }
                            }
                        }
                    }
                }
            };

            XposedBridge.hookAllMethods(webViewClass, "evaluateJavascript", jsInterceptHook);
            XposedBridge.hookAllMethods(webViewClass, "loadUrl", jsInterceptHook);

        } catch (Throwable t) {
            XposedBridge.log("Chaoxing AdSkip Error (WebView Hook): " + t.getMessage());
        }
    }

    // 动态生成符合草台班子审美标准（43位随机字符 + =号）的 JSON
    private String generateRandomDeviceFlag() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"flagInfo\":\"");
        for (int i = 0; i < 43; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        sb.append("=\"}");
        return sb.toString();
    }

    // 带有节流缓存的配置读取方法
    private SignConfig getSignConfig() {
        if (cachedConfig != null && (System.currentTimeMillis() - lastReadTime < 3000)) {
            return cachedConfig;
        }

        SignConfig config = new SignConfig();
        File file = new File("/storage/emulated/0/Android/data/com.chaoxing.mobile/files/chaoxing_loc.txt");

        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                FileWriter fw = new FileWriter(file);
                fw.write("是否开启定位修改: false\n经度: \n纬度: \n是否开启地址名修改: false\n地址名: \n是否开启名字修改: false\n名字: \n是否开启随机指纹: false\n");
                fw.close();
            } catch (Exception e) {}
            cachedConfig = config;
            lastReadTime = System.currentTimeMillis();
            return config;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("是否开启定位修改:")) config.modifyLocation = parseBooleanValue(line);
                else if (line.startsWith("经度:")) config.longitude = parseStringValue(line);
                else if (line.startsWith("纬度:")) config.latitude = parseStringValue(line);
                else if (line.startsWith("是否开启地址名修改:")) config.modifyAddress = parseBooleanValue(line);
                else if (line.startsWith("地址名:")) config.address = parseStringValue(line);
                else if (line.startsWith("是否开启名字修改:")) config.modifyName = parseBooleanValue(line);
                else if (line.startsWith("名字:")) config.name = parseStringValue(line);
                    // 全新的一键开关解析
                else if (line.startsWith("是否开启随机指纹:")) config.randomizeDeviceFlag = parseBooleanValue(line);
            }
        } catch (Exception e) {}

        cachedConfig = config;
        lastReadTime = System.currentTimeMillis();
        return config;
    }

    private boolean parseBooleanValue(String line) {
        try { return "true".equalsIgnoreCase(line.substring(line.indexOf(":") + 1).trim()); } catch (Exception e) { return false; }
    }

    private String parseStringValue(String line) {
        try { return line.substring(line.indexOf(":") + 1).trim(); } catch (Exception e) { return ""; }
    }
}