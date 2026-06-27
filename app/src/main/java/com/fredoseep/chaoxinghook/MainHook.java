package com.fredoseep.chaoxinghook;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MainHook implements IXposedHookLoadPackage {

    private static final Set<String> hookedWebViewClients = new HashSet<>();
    private static final String FAKE_UPLOAD_FILE_PATH = "/storage/emulated/0/Download/fake_exam_image.png";

    // 核心修复：添加防重入标志，防止读取配置文件时触发无限递归死循环
    private static final ThreadLocal<Boolean> READING_CONFIG = ThreadLocal.withInitial(() -> false);

    static class LocationPoint {
        double lat;
        double lon;
        double distance;
        LocationPoint(double lat, double lon, double distance) {
            this.lat = lat; this.lon = lon; this.distance = distance;
        }
    }
    private static final List<LocationPoint> historyPoints = new ArrayList<>();
    private static double[] calculatedTarget = null;

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
        public static final String CLASS_DB_QUERY = "zo.b0";
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
        boolean randomizeDeviceFlag = false;
        boolean autoCalculateLocation = false;
        boolean bypassExamCheat = true;
        boolean enableCopyRestriction = true;
        boolean replaceExamScreenshot = false;
        String fakeImagePath = "";
    }

    private static SignConfig cachedConfig = null;
    private static long lastReadTime = 0;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.chaoxing.mobile")) return;

        try { XposedBridge.hookAllMethods(XposedHelpers.findClass(ObfuscationMap.CLASS_SPLASH_VIEW_MODEL, lpparam.classLoader), ObfuscationMap.METHOD_SPLASH_A, new XC_MethodHook() { @Override protected void afterHookedMethod(MethodHookParam param) { param.setResult(null); } }); } catch (Throwable t) {}
        try { XposedBridge.hookAllMethods(XposedHelpers.findClass(ObfuscationMap.CLASS_HOME_PAGE_HEADER, lpparam.classLoader), ObfuscationMap.METHOD_HOME_HEADER_G, new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam param) { if (param.args.length > 0) param.args[0] = null; } }); } catch (Throwable t) {}
        try { XposedBridge.hookAllMethods(XposedHelpers.findClass(ObfuscationMap.CLASS_CATEGORY_HOLDER, lpparam.classLoader), ObfuscationMap.METHOD_CATEGORY_HOLDER_O, new XC_MethodHook() { @Override protected void afterHookedMethod(MethodHookParam param) { Object viewHolder = param.thisObject; android.widget.TextView tvLeft = (android.widget.TextView) XposedHelpers.getObjectField(viewHolder, ObfuscationMap.FIELD_CATEGORY_HOLDER_TV_LEFT); if (tvLeft != null && tvLeft.getText() != null) { String title = tvLeft.getText().toString(); if (title.contains("推荐") || title.contains("Recommend")) { android.view.View itemView = (android.view.View) XposedHelpers.getObjectField(viewHolder, "itemView"); if (itemView != null) { itemView.setVisibility(android.view.View.GONE); android.view.ViewGroup.LayoutParams layoutParams = itemView.getLayoutParams(); layoutParams.height = 0; layoutParams.width = 0; itemView.setLayoutParams(layoutParams); } } } } }); } catch (Throwable t) {}
        try { XposedBridge.hookAllMethods(XposedHelpers.findClass(ObfuscationMap.CLASS_MAIN_PAGE_RECORD_ADAPTER, lpparam.classLoader), ObfuscationMap.METHOD_ADAPTER_GET_ITEM_COUNT, new XC_MethodHook() { @Override protected void afterHookedMethod(MethodHookParam param) { try { java.util.List<?> listA = (java.util.List<?>) XposedHelpers.getObjectField(param.thisObject, ObfuscationMap.FIELD_ADAPTER_LIST_A); if (listA != null) param.setResult(listA.size()); } catch (NoSuchFieldError e) { java.util.List<?> listA = (java.util.List<?>) XposedHelpers.getObjectField(param.thisObject, ObfuscationMap.FIELD_ADAPTER_LIST_F82688A); if (listA != null) param.setResult(listA.size()); } } }); } catch (Throwable t) {}
        try { XposedBridge.hookAllMethods(XposedHelpers.findClass(ObfuscationMap.CLASS_DB_QUERY, lpparam.classLoader), ObfuscationMap.METHOD_DB_QUERY_W, new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam param) { if (param.args.length == 3 && param.args[2] instanceof Integer) { if ((Integer) param.args[2] == 3) param.args[2] = 15; } } }); } catch (Throwable t) {}
        try { XposedBridge.hookAllMethods(XposedHelpers.findClass(ObfuscationMap.CLASS_CHAT_MANAGER_Q1, lpparam.classLoader), ObfuscationMap.METHOD_CHAT_MANAGER_C1, new XC_MethodHook() { @Override protected void afterHookedMethod(MethodHookParam param) { Object result = param.getResult(); if (result instanceof java.util.List) { java.util.List<?> list = (java.util.List<?>) result; for (int i = list.size() - 1; i >= 0; i--) { Object info = list.get(i); if (info != null && info.getClass().getSimpleName().equals("ConversationInfo")) { if ((Integer) XposedHelpers.callMethod(info, "getType") == 20) { list.remove(i); } } } } } }); } catch (Throwable t) {}
        try { XposedBridge.hookAllMethods(XposedHelpers.findClass(ObfuscationMap.CLASS_EM_CMD_MESSAGE_BODY, lpparam.classLoader), ObfuscationMap.METHOD_EM_CMD_ACTION, new XC_MethodHook() { @Override protected void afterHookedMethod(MethodHookParam param) { Object result = param.getResult(); if (result != null && "REVOKE_FLAG".equals(result.toString())) { param.setResult("BLOCK_REVOKE_FLAG"); } } }); } catch (Throwable t) {}


// ==========================================
        // 【第一道防线】：精准阉割原生播放器的异常行为上报
        // ==========================================
        try {
            Class<?> fragmentClass = XposedHelpers.findClassIfExists("com.chaoxing.mobile.player.course.CoursePlayerFragment", lpparam.classLoader);
            if (fragmentClass != null) {

                // 1. 物理斩杀“拖拽汇报” (Pa)
                XposedHelpers.findAndHookMethod(fragmentClass, "Pa", int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(null); // 强制返回，阻断拖拽记录发往服务器
                        XposedBridge.log("Chaoxing [物理超度]: 成功拦截 拖拽行为 上报 (Pa)");
                    }
                });

                // 2. 物理斩杀“暂停汇报” (Wa)
                XposedHelpers.findAndHookMethod(fragmentClass, "Wa", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(null); // 强制返回
                        XposedBridge.log("Chaoxing [物理超度]: 成功拦截 暂停/切后台行为 上报 (Wa)");
                    }
                });

                // 注意：彻底移除了之前错误拦截 Ra (导致无法提交完成) 的代码！
            }
        } catch (Throwable t) {
            XposedBridge.log("Chaoxing Error (Fragment Hook): " + t.getMessage());
        }

        // ==========================================
        // 【第二道防线】：绝对防御，无视服务器的进度回退指令
        // ==========================================
        try {
            Class<?> dotResClass = XposedHelpers.findClassIfExists("com.chaoxing.mobile.player.course.model.CourseDotRes", lpparam.classLoader);
            if (dotResClass != null) {
                XposedHelpers.findAndHookMethod(dotResClass, "isRollbackStatus", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // 如果服务器发现了进度跳跃要求回退，到了手机内存里统统变成 false (不回退)！
                        if ((Boolean) param.getResult()) {
                            param.setResult(false);
                            XposedBridge.log("Chaoxing [防回退]: 成功没收服务器的进度回退指令！");
                        }
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log("Chaoxing Error (Rollback Hook): " + t.getMessage());
        }
        try {
            Class<?> webViewClass = XposedHelpers.findClass("android.webkit.WebView", lpparam.classLoader);
            XposedBridge.hookAllMethods(webViewClass, "setWebViewClient", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object webViewObj = param.thisObject;

                    // ==========================================
                    // 【精确狙击】：强行接管 WebView 长按事件，注入极客专属下载菜单！
                    // ==========================================
                    if (webViewObj instanceof android.webkit.WebView) {
                        final android.webkit.WebView webView = (android.webkit.WebView) webViewObj;

                        // 覆盖可能存在的原有长按逻辑
                        webView.setOnLongClickListener(new android.view.View.OnLongClickListener() {
                            @Override
                            public boolean onLongClick(android.view.View v) {
                                // 探针：获取手指按压位置的内容类型
                                final android.webkit.WebView.HitTestResult result = webView.getHitTestResult();
                                if (result != null) {
                                    int type = result.getType();

                                    // 如果长按的是 纯图片 或 带有链接的图片
                                    if (type == android.webkit.WebView.HitTestResult.IMAGE_TYPE ||
                                            type == android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {

                                        final String imageUrl = result.getExtra();
                                        if (imageUrl != null && imageUrl.startsWith("http")) {

                                            // 呼叫 Android 原生系统弹窗
                                            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(webView.getContext());
                                            builder.setTitle("chaoxingHook: ");
                                            builder.setMessage("锁定目标图片，是否执行下载？\n\n" + imageUrl);
                                            builder.setPositiveButton("立即下载", new android.content.DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(android.content.DialogInterface dialog, int which) {
                                                    // 用户点击下载，开启后台线程拉取文件
                                                    new Thread(() -> {
                                                        try {
                                                            File dir = new File("/storage/emulated/0/Download/ChaoxingExam");
                                                            if (!dir.exists()) dir.mkdirs();

                                                            String fileName = "IMG_" + System.currentTimeMillis() + ".png";
                                                            String[] parts = imageUrl.split("/");
                                                            if (parts.length > 5) {
                                                                fileName = parts[parts.length - 2] + "_" + parts[parts.length - 1];
                                                                if (fileName.contains("?")) fileName = fileName.substring(0, fileName.indexOf("?"));
                                                            }

                                                            File saveFile = new File(dir, fileName);

                                                            URL targetUrl = new URL(imageUrl);
                                                            HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();
                                                            conn.setRequestMethod("GET");
                                                            InputStream is = conn.getInputStream();
                                                            java.io.FileOutputStream fos = new java.io.FileOutputStream(saveFile);
                                                            byte[] buf = new byte[4096];
                                                            int len;
                                                            while ((len = is.read(buf)) != -1) fos.write(buf, 0, len);
                                                            fos.close();
                                                            is.close();

                                                            // 可选：在这里可以用 Looper 抛出一个 Toast 提示下载完成
                                                            XposedBridge.log("Chaoxing [精准打击]: 图片已成功降维至本地 -> " + saveFile.getAbsolutePath());
                                                        } catch (Exception e) {
                                                            XposedBridge.log("Chaoxing Error: " + e.getMessage());
                                                        }
                                                    }).start();
                                                }
                                            });
                                            builder.setNegativeButton("取消", null);
                                            builder.show(); // 弹出对话框

                                            return true; // 返回 true 代表我们消费了这个长按动作，拦截原本的事件传递
                                        }
                                    }
                                }
                                return false; // 如果长按的不是图片，原样放行，不影响网页滑动或系统功能
                            }
                        });
                    }
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

                                // ==========================================
                                // 【明文调参测试】：劫持 H5 小报告，重组明文时间参数
                                // ==========================================
                                if (url.contains("/multimedia/log/a/")) {
                                    try {
                                        String newUrl = url;

                                        // 1. 提取视频总长度 duration
                                        Pattern durationPattern = Pattern.compile("duration=(\\d+)");
                                        Matcher durationMatcher = durationPattern.matcher(url);
                                        if (durationMatcher.find()) {
                                            String durationVal = durationMatcher.group(1);
                                            long durationSeconds = Long.parseLong(durationVal);

                                            // 2. 强行将当前进度 playingTime 填满，伪装成已看完
                                            newUrl = newUrl.replaceAll("playingTime=\\d+", "playingTime=" + durationVal);
                                            newUrl = newUrl.replaceAll("clipTime=[^&]*", "clipTime=0_" + durationVal);

                                            // 3. 关键：将客户端时间戳 cxtime 往未来平移一个视频总长度（毫秒），模拟真实的物理观看耗时
                                            Pattern cxTimePattern = Pattern.compile("cxtime=(\\d+)");
                                            Matcher cxTimeMatcher = cxTimePattern.matcher(url);
                                            if (cxTimeMatcher.find()) {
                                                long originalCxTime = Long.parseLong(cxTimeMatcher.group(1));
                                                long fakeCxTime = originalCxTime + (durationSeconds * 1000);
                                                newUrl = newUrl.replaceAll("cxtime=\\d+", "cxtime=" + fakeCxTime);
                                            }
                                        }

                                        // 4. 调整行为标记 isdrag
                                        // 可以测试改为 0 (伪装成最安全的常规播放心跳)
                                        // 如果不行，也可以手动改成 4 (完播标记) 看看后端更认哪一个
                                        newUrl = newUrl.replaceAll("isdrag=\\d+", "isdrag=0");

                                        XposedBridge.log("Chaoxing [调参测试] 原始 URL: " + url);
                                        XposedBridge.log("Chaoxing [调参测试] 篡改 URL: " + newUrl);

                                        // 5. 拿着调参后的新 URL 代替前端向服务器发包
                                        URL targetUrl = new URL(newUrl);
                                        HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();
                                        conn.setRequestMethod("GET");

                                        String cookie = android.webkit.CookieManager.getInstance().getCookie(url);
                                        if (cookie != null) conn.setRequestProperty("Cookie", cookie);
                                        conn.setRequestProperty("Accept-Encoding", "gzip");

                                        InputStream is = conn.getInputStream();
                                        String encoding = conn.getContentEncoding();
                                        if (encoding != null && encoding.toLowerCase().contains("gzip")) {
                                            is = new java.util.zip.GZIPInputStream(is);
                                        }

                                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                        byte[] buffer = new byte[4096];
                                        int len;
                                        while ((len = is.read(buffer)) != -1) baos.write(buffer, 0, len);
                                        String jsonStr = new String(baos.toByteArray(), "UTF-8");

                                        XposedBridge.log("Chaoxing [调参测试] 服务器返回: " + jsonStr);

                                        // 6. 将服务器的原始回应直接丢回给前端 WebView
                                        Class<?> responseClass = XposedHelpers.findClassIfExists("android.webkit.WebResourceResponse", lpparam.classLoader);
                                        if (responseClass != null) {
                                            innerParam.setResult(XposedHelpers.newInstance(
                                                    responseClass,
                                                    "application/json",
                                                    "utf-8",
                                                    new ByteArrayInputStream(jsonStr.getBytes("UTF-8"))
                                            ));
                                        }
                                        return; // 阻断原请求
                                    } catch (Exception e) {
                                        XposedBridge.log("Chaoxing Error (Param Tweak): " + e.getMessage());
                                    }
                                }

                                if (getSignConfig().bypassExamCheat && (url.startsWith("https://mooc1-api.chaoxing.com/keeper/api/receiveExamLogs") || url.contains("/exam-ans/exam/phone/exit-count")||url.contains("https://data-xxt.aichoxing.com/analysis/ac_event")||url.contains("pan-yz.chaoxing.com/upload"))) {
                                    try {
                                        Class<?> responseClass = XposedHelpers.findClassIfExists("android.webkit.WebResourceResponse", lpparam.classLoader);
                                        if (responseClass != null) {
                                            String fakeResponse = "{\"status\":1,\"result\":true,\"msg\":\"success\",\"data\":null}";
                                            innerParam.setResult(XposedHelpers.newInstance(
                                                    responseClass,
                                                    "application/json",
                                                    "utf-8",
                                                    new ByteArrayInputStream(fakeResponse.getBytes("UTF-8"))
                                            ));
                                        }
                                    } catch (Exception e) {}
                                    return;
                                }
                                // ==========================================
                                // 【终极网课解锁】：拦截课程源头，暴力篡改视频限制参数 (兼容实体转义)
                                // ==========================================
                                // 扩大匹配范围，涵盖 JSON 源头和 HTML 框架
                                if (url.contains("knowledge/cards") || url.contains("richvideo/initdatawithviewer") || url.contains("studentstudy")) {
                                    try {
                                        URL targetUrl = new URL(url);
                                        HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();

                                        // 复制原请求的方法与 Header
                                        if (requestObj != null) {
                                            conn.setRequestMethod((String) XposedHelpers.callMethod(requestObj, "getMethod"));
                                            @SuppressWarnings("unchecked")
                                            Map<String, String> headers = (Map<String, String>) XposedHelpers.callMethod(requestObj, "getRequestHeaders");
                                            if (headers != null) {
                                                for (Map.Entry<String, String> entry : headers.entrySet()) {
                                                    if (!entry.getKey().equalsIgnoreCase("Accept-Encoding")) {
                                                        conn.setRequestProperty(entry.getKey(), entry.getValue());
                                                    }
                                                }
                                            }
                                        } else {
                                            conn.setRequestMethod("GET");
                                        }

                                        // 同步 Cookie 证明合法登录，并请求 GZIP 压缩
                                        String cookie = android.webkit.CookieManager.getInstance().getCookie(url);
                                        if (cookie != null) {
                                            conn.setRequestProperty("Cookie", cookie);
                                        }
                                        conn.setRequestProperty("Accept-Encoding", "gzip");

                                        // 解除 GZIP 封印，获取明文流
                                        InputStream is = conn.getInputStream();
                                        String encoding = conn.getContentEncoding();
                                        if (encoding != null && encoding.toLowerCase().contains("gzip")) {
                                            is = new java.util.zip.GZIPInputStream(is);
                                        }

                                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                        byte[] buffer = new byte[4096];
                                        int len;
                                        while ((len = is.read(buffer)) != -1) {
                                            baos.write(buffer, 0, len);
                                        }
                                        String htmlStr = new String(baos.toByteArray(), "UTF-8");

                                        // ================= 实施终极基因改造 =================
                                        if (htmlStr.contains("fastforward") || htmlStr.contains("doublespeed")) {
                                            htmlStr = htmlStr
                                                    // 1. 彻底解除快进限制
                                                    .replace("\"fastforward\":\"true\"", "\"fastforward\":\"false\"")
                                                    .replace("\"fastforward\":true", "\"fastforward\":false")
                                                    .replace("&quot;fastforward&quot;:&quot;true&quot;", "&quot;fastforward&quot;:&quot;false&quot;")
                                                    .replace("&quot;fastforward&quot;:true", "&quot;fastforward&quot;:false")

                                                    // 2. 修复倍速误伤：超星中1是开启，0是关闭，我们要强制改为1！
                                                    .replace("\"doublespeed\":0", "\"doublespeed\":1")
                                                    .replace("\"doublespeed\":\"0\"", "\"doublespeed\":\"1\"")
                                                    .replace("&quot;doublespeed&quot;:0", "&quot;doublespeed&quot;:1")
                                                    .replace("&quot;doublespeed&quot;:&quot;0&quot;", "&quot;doublespeed&quot;:&quot;1&quot;")

                                                    // 3. 解除切屏暂停
                                                    .replace("\"switchwindow\":\"true\"", "\"switchwindow\":\"false\"")
                                                    .replace("\"switchwindow\":true", "\"switchwindow\":false")
                                                    .replace("&quot;switchwindow&quot;:&quot;true&quot;", "&quot;switchwindow&quot;:&quot;false&quot;")
                                                    .replace("&quot;switchwindow&quot;:true", "&quot;switchwindow&quot;:false");

                                            htmlStr = htmlStr.replace("var forbidImgClick = \"true\";", "var forbidImgClick = \"false\";");

                                            XposedBridge.log("Chaoxing [网课解锁]: 成功篡改最底层视频限制参数，快进、倍速均已完全自由！");
                                        }

                                        // ================= 重新打包返回 =================
                                        Class<?> responseClass = XposedHelpers.findClassIfExists("android.webkit.WebResourceResponse", lpparam.classLoader);
                                        if (responseClass != null) {
                                            String contentType = conn.getContentType();
                                            String mimeType = "text/html";
                                            String charset = "utf-8";
                                            if (contentType != null) {
                                                String[] parts = contentType.split(";");
                                                mimeType = parts[0].trim();
                                                if (parts.length > 1 && parts[1].toLowerCase().contains("charset=")) {
                                                    charset = parts[1].split("=")[1].trim();
                                                }
                                            }
                                            innerParam.setResult(XposedHelpers.newInstance(
                                                    responseClass,
                                                    mimeType,
                                                    charset,
                                                    new ByteArrayInputStream(htmlStr.getBytes(charset))
                                            ));
                                        }
                                        return; // 结束，原请求不再发出
                                    } catch (Exception e) {
                                        XposedBridge.log("Chaoxing Error (Video Unlock): " + e.getMessage());
                                    }
                                }

                                if (getSignConfig().enableCopyRestriction && url.contains("notAllowCopy.css")) {
                                    try {
                                        Class<?> responseClass = XposedHelpers.findClassIfExists("android.webkit.WebResourceResponse", lpparam.classLoader);
                                        if (responseClass != null) {
                                            String emptyCss = "";
                                            innerParam.setResult(XposedHelpers.newInstance(
                                                    responseClass,
                                                    "text/css",
                                                    "utf-8",
                                                    new java.io.ByteArrayInputStream(emptyCss.getBytes("UTF-8"))
                                            ));
                                            XposedBridge.log("Chaoxing AdSkip: 成功拦截 notAllowCopy.css，复制限制已解除！");
                                        }
                                    } catch (Exception e) {}
                                    return;
                                }

                                if (url.contains("stuSignajax")) {
                                    SignConfig config = getSignConfig();
                                    String newUrlString = url;
                                    boolean hasModified = false;
                                    double sendLat = 0, sendLon = 0;

                                    if (config.autoCalculateLocation) {
                                        if (calculatedTarget != null) {
                                            sendLat = calculatedTarget[0];
                                            sendLon = calculatedTarget[1];
                                        } else {
                                            double baseLat = 0, baseLon = 0;
                                            try {
                                                Matcher latMatcher = Pattern.compile("latitude=([^&]+)").matcher(url);
                                                Matcher lonMatcher = Pattern.compile("longitude=([^&]+)").matcher(url);
                                                if (latMatcher.find()) baseLat = Double.parseDouble(latMatcher.group(1));
                                                if (lonMatcher.find()) baseLon = Double.parseDouble(lonMatcher.group(1));
                                            } catch (Exception e) {}
                                            try {
                                                if (!config.latitude.isEmpty()) baseLat = Double.parseDouble(config.latitude);
                                                if (!config.longitude.isEmpty()) baseLon = Double.parseDouble(config.longitude);
                                            } catch (Exception e){}

                                            if (historyPoints.isEmpty()) {
                                                sendLat = baseLat;
                                                sendLon = baseLon;
                                            } else {
                                                LocationPoint lastP = historyPoints.get(historyPoints.size() - 1);
                                                double offset = Math.max(0.0001, lastP.distance / 200000.0);
                                                int attempt = historyPoints.size();

                                                if (attempt % 3 == 1) {
                                                    sendLat = lastP.lat + offset;
                                                    sendLon = lastP.lon;
                                                } else if (attempt % 3 == 2) {
                                                    sendLat = lastP.lat;
                                                    sendLon = lastP.lon + offset;
                                                } else {
                                                    sendLat = lastP.lat - offset;
                                                    sendLon = lastP.lon - offset;
                                                }
                                            }
                                        }
                                        newUrlString = newUrlString.replaceAll("latitude=[^&]*", "latitude=" + sendLat)
                                                .replaceAll("longitude=[^&]*", "longitude=" + sendLon);
                                        hasModified = true;

                                    } else if (config.modifyLocation && !config.latitude.isEmpty() && !config.longitude.isEmpty()) {
                                        newUrlString = newUrlString.replaceAll("latitude=[^&]*", "latitude=" + config.latitude)
                                                .replaceAll("longitude=[^&]*", "longitude=" + config.longitude);
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

                                        String cookie = android.webkit.CookieManager.getInstance().getCookie(newUrlString);
                                        if (cookie != null) { conn.setRequestProperty("Cookie", cookie); }

                                        InputStream is = conn.getInputStream();
                                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                        byte[] buffer = new byte[1024];
                                        int len;
                                        while ((len = is.read(buffer)) != -1) baos.write(buffer, 0, len);
                                        String jsonResp = new String(baos.toByteArray(), "UTF-8");

                                        if (config.autoCalculateLocation) {
                                            Matcher m = Pattern.compile("距.*?([0-9.]+)\\s*米").matcher(jsonResp);
                                            if (m.find()) {
                                                String originalMatch = m.group(0);
                                                double dist = Double.parseDouble(m.group(1));

                                                if (calculatedTarget != null) {
                                                    calculatedTarget = null;
                                                }

                                                historyPoints.add(new LocationPoint(sendLat, sendLon, dist));
                                                if (historyPoints.size() > 3) {
                                                    historyPoints.remove(0);
                                                }

                                                String customMsg = "";
                                                if (historyPoints.size() == 3) {
                                                    calculatedTarget = calculateTriangulation(historyPoints);
                                                    if (calculatedTarget != null) {
                                                        customMsg = "Xposed提示: 目标坐标已锁定，误差已过滤，请点击执行最终打卡。";
                                                    } else {
                                                        historyPoints.clear();
                                                        customMsg = "Xposed提示: 三点共线计算失败，正在重新采集，请再次点击。";
                                                    }
                                                } else {
                                                    customMsg = "Xposed提示: 距靶心 " + dist + " 米，采集进度(" + historyPoints.size() + "/3)。请再次点击。";
                                                }

                                                jsonResp = jsonResp.replace(originalMatch, customMsg);
                                                XposedBridge.log("Chaoxing AdSkip: " + customMsg);

                                            } else if (jsonResp.contains("success") || jsonResp.contains("成功")) {
                                                historyPoints.clear();
                                                calculatedTarget = null;
                                                XposedBridge.log("Chaoxing AdSkip: 定位爆破签到大成功！");
                                            }
                                        }

                                        String contentType = conn.getContentType();
                                        Class<?> responseClass = XposedHelpers.findClassIfExists("android.webkit.WebResourceResponse", lpparam.classLoader);
                                        if (responseClass != null) {
                                            innerParam.setResult(XposedHelpers.newInstance(
                                                    responseClass,
                                                    (contentType != null) ? contentType.split(";")[0].trim() : "application/json",
                                                    conn.getContentEncoding() != null ? conn.getContentEncoding() : "utf-8",
                                                    new ByteArrayInputStream(jsonResp.getBytes("UTF-8"))
                                            ));
                                        }
                                    } catch (Exception e) {}
                                }
                            }
                        });
                        targetClass = targetClass.getSuperclass();
                    }
                }
            });

            XC_MethodHook jsInterceptHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length > 0 && param.args[0] instanceof String) {
                        String jsCode = (String) param.args[0];
                        if (jsCode.contains("CLIENT_DEVICE_FLAG")) {
                            SignConfig config = getSignConfig();
                            if (config.randomizeDeviceFlag) {
                                int start = jsCode.indexOf('{');
                                int end = jsCode.lastIndexOf('}');
                                if (start != -1 && end != -1 && start < end) {
                                    String wipeMemory = "window.localStorage.clear(); window.sessionStorage.clear(); ";
                                    String newJsCode = wipeMemory + jsCode.substring(0, start) + generateRandomDeviceFlag() + jsCode.substring(end + 1);
                                    param.args[0] = newJsCode;
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

        try {
            XC_MethodHook fileReadHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length == 0 || param.args[0] == null) return;

                    String path = "";
                    if (param.args[0] instanceof java.io.File) {
                        path = ((java.io.File) param.args[0]).getAbsolutePath();
                    } else if (param.args[0] instanceof String) {
                        path = (String) param.args[0];
                    }
                    if (path.isEmpty()) return;

                    // 核心修复：前置过滤。如果不是目标文件，绝对不调用 getSignConfig()，从根源斩断递归
                    if (!path.contains("/Android/data/com.chaoxing.mobile/cache/image/") ||
                            (!path.endsWith(".png") && !path.endsWith(".jpg"))) {
                        return;
                    }

                    SignConfig config = getSignConfig();
                    if (!config.replaceExamScreenshot) return;

                    String imagePath = config.fakeImagePath.isEmpty() ? FAKE_UPLOAD_FILE_PATH : config.fakeImagePath;
                    File fakeFile = new File(imagePath);
                    if (fakeFile.exists()) {
                        if (param.args[0] instanceof java.io.File) {
                            param.args[0] = fakeFile;
                        } else {
                            param.args[0] = imagePath;
                        }
                        XposedBridge.log("Chaoxing [绝杀]: 抓到监考截图上传！已成功替换为自定义图片 -> 拦截原图: " + path);
                    } else {
                        XposedBridge.log("Chaoxing [警告]: 找不到自定义伪装图片，未执行替换！请检查路径: " + imagePath);
                    }
                }
            };

            XposedBridge.hookAllConstructors(java.io.FileInputStream.class, fileReadHook);

        } catch (Throwable t) {
            XposedBridge.log("Chaoxing Error (File Replace Hook): " + t.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "com.chaoxing.mobile.webapp.jsprotocal.common.f1",
                    lpparam.classLoader,
                    "q0",
                    File.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            XposedBridge.log("Chaoxing [终极斩杀]: 成功拦截 f1.q0，ExamKeeper 截图上传已被物理蒸发！");
                            Object f1Instance = param.thisObject;
                            String fakeSuccessJson = (String) XposedHelpers.callMethod(f1Instance, "f0", 1, null);
                            XposedHelpers.callMethod(f1Instance, "r", "CLIENT_SNAPSHOT", fakeSuccessJson);
                            param.setResult(null);
                        }
                    }
            );
        } catch (Throwable t) {
            XposedBridge.log("Chaoxing Error (f1.q0 Hook): " + t.getMessage());
        }
    }

    private double[] calculateTriangulation(List<LocationPoint> points) {
        if (points.size() < 3) return null;

        LocationPoint p1 = points.get(0);
        LocationPoint p2 = points.get(1);
        LocationPoint p3 = points.get(2);

        double R = 6378137.0;
        double lat0 = p1.lat * Math.PI / 180.0;

        double x1 = 0, y1 = 0;
        double r1 = p1.distance;

        double x2 = (p2.lon - p1.lon) * (Math.PI / 180.0) * R * Math.cos(lat0);
        double y2 = (p2.lat - p1.lat) * (Math.PI / 180.0) * R;
        double r2 = p2.distance;

        double x3 = (p3.lon - p1.lon) * (Math.PI / 180.0) * R * Math.cos(lat0);
        double y3 = (p3.lat - p1.lat) * (Math.PI / 180.0) * R;
        double r3 = p3.distance;

        double A = 2 * x2;
        double B = 2 * y2;
        double C = r1 * r1 - r2 * r2 + x2 * x2 + y2 * y2;

        double D = 2 * x3;
        double E = 2 * y3;
        double F = r1 * r1 - r3 * r3 + x3 * x3 + y3 * y3;

        double det = A * E - B * D;
        if (Math.abs(det) < 1.0) return null;

        double targetX = (C * E - B * F) / det;
        double targetY = (A * F - C * D) / det;

        double targetLon = p1.lon + (targetX / (R * Math.cos(lat0) * (Math.PI / 180.0)));
        double targetLat = p1.lat + (targetY / (R * (Math.PI / 180.0)));

        return new double[]{targetLat, targetLon};
    }

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

    private SignConfig getSignConfig() {
        // 核心修复：防止底层死循环读取
        if (Boolean.TRUE.equals(READING_CONFIG.get())) {
            return cachedConfig != null ? cachedConfig : new SignConfig();
        }

        if (cachedConfig != null && (System.currentTimeMillis() - lastReadTime < 3000)) return cachedConfig;

        READING_CONFIG.set(true);
        try {
            SignConfig config = new SignConfig();
            File file = new File("/storage/emulated/0/Android/data/com.chaoxing.mobile/files/chaoxing_loc.txt");

            if (!file.exists()) {
                try {
                    file.getParentFile().mkdirs();
                    FileWriter fw = new FileWriter(file);
                    fw.write("是否开启定位修改: false\n经度: \n纬度: \n是否开启地址名修改: false\n地址名: \n是否开启名字修改: false\n名字: \n是否开启随机指纹: true\n是否开启经纬度爆破: false\n是否开启考试风控拦截: true\n是否开启复制限制解除: true\n是否开启考试截图替换: false\n截图替换路径: " + FAKE_UPLOAD_FILE_PATH + "\n");
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
                    else if (line.startsWith("是否开启随机指纹:")) config.randomizeDeviceFlag = parseBooleanValue(line);
                    else if (line.startsWith("是否开启经纬度爆破:")) config.autoCalculateLocation = parseBooleanValue(line);
                    else if (line.startsWith("是否开启考试风控拦截:")) config.bypassExamCheat = parseBooleanValue(line);
                    else if (line.startsWith("是否开启复制限制解除:")) config.enableCopyRestriction = parseBooleanValue(line);
                    else if (line.startsWith("是否开启考试截图替换:")) config.replaceExamScreenshot = parseBooleanValue(line);
                    else if (line.startsWith("截图替换路径:")) config.fakeImagePath = parseStringValue(line);
                }
            } catch (Exception e) {}

            cachedConfig = config;
            lastReadTime = System.currentTimeMillis();
            return config;
        } finally {
            READING_CONFIG.set(false);
        }
    }

    private boolean parseBooleanValue(String line) {
        try { return "true".equalsIgnoreCase(line.substring(line.indexOf(":") + 1).trim()); } catch (Exception e) { return false; }
    }

    private String parseStringValue(String line) {
        try { return line.substring(line.indexOf(":") + 1).trim(); } catch (Exception e) { return ""; }
    }
}