package com.retro.subsonic;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class LocalStreamProxy {

    public interface ProxyListener {
        void onProgress(int percent);
        void onCached(File cachedFile);
        void onError(String reason);
    }

    private Context context;
    private String songId;
    private String originalStreamUrl;
    private ProxyListener listener;

    private ServerSocket serverSocket;
    private int proxyPort = 0;
    private Thread serverThread;
    private Thread downloadThread;

    private volatile boolean isStopped = false;
    private volatile boolean downloadFinished = false;
    private volatile boolean downloadFailed = false;
    private volatile String failReason = "";

    private volatile long downloadedBytes = 0;
    private volatile long totalBytes = -1;
    private volatile String audioContentType = "audio/mpeg";

    private File tmpFile;
    private File targetFile;

    public LocalStreamProxy(Context context, String songId, String originalStreamUrl, ProxyListener listener) {
        this.context = context;
        this.songId = songId;
        this.originalStreamUrl = originalStreamUrl;
        this.listener = listener;

        this.tmpFile = CacheManager.getTempFile(context, songId);
        this.targetFile = CacheManager.getSongFile(context, songId);
    }

    public synchronized String start() throws Exception {
        if (tmpFile.exists()) {
            tmpFile.delete();
        }

        serverSocket = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
        proxyPort = serverSocket.getLocalPort();

        // 1. 启动全速后台下载流线程
        startDownloader();

        // 2. 启动本地代理 HTTP 服务端线程
        startServer();

        return "http://127.0.0.1:" + proxyPort + "/stream";
    }

    private void startDownloader() {
        downloadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                String reason = runDownloadPipeline(originalStreamUrl, 0);
                if (isStopped) return;

                if ("OK".equals(reason) && CacheManager.isValidAudioFile(tmpFile)) {
                    if (tmpFile.renameTo(targetFile)) {
                        targetFile.setLastModified(System.currentTimeMillis());
                        downloadFinished = true;

                        SharedPreferences sp = context.getSharedPreferences("subsonic_cfg", Context.MODE_PRIVATE);
                        int maxMb = 500;
                        try {
                            maxMb = Integer.parseInt(sp.getString("cache_size_mb", "500"));
                        } catch (Exception ignored) {}
                        CacheManager.trimCache(context, maxMb * 1024L * 1024L, songId);

                        if (listener != null) {
                            listener.onCached(targetFile);
                        }
                        return;
                    }
                }

                downloadFailed = true;
                failReason = (reason != null && !"OK".equals(reason)) ? reason : "下载音频流失败";
                if (listener != null && !isStopped) {
                    listener.onError(failReason);
                }
            }
        });
        downloadThread.start();
    }

    private String runDownloadPipeline(String targetUrl, int depth) {
        if (depth > 6 || isStopped) return "重定向过多或已取消";

        HttpURLConnection conn = null;
        InputStream is = null;
        FileOutputStream fos = null;

        try {
            URL url = new URL(targetUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; U; Android 4.2.2; zh-cn) AppleWebKit/534.30");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.connect();

            int code = conn.getResponseCode();

            // 拦截 301/302 重定向
            if (code == 301 || code == 302 || code == 303 || code == 307) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location != null && location.length() > 0) {
                    URL redirectUrl = new URL(url, location);
                    return runDownloadPipeline(redirectUrl.toString(), depth + 1);
                }
                return "重定向目标为空 (HTTP " + code + ")";
            }

            if (code != 200 && code != 206) {
                return "服务器响应异常 (HTTP " + code + ")";
            }

            totalBytes = conn.getContentLength();
            String cType = conn.getContentType();
            if (cType != null && cType.contains("audio/")) {
                audioContentType = cType;
            }

            is = conn.getInputStream();

            // 嗅探前 2KB 数据
            byte[] previewBuf = new byte[2048];
            int previewRead = is.read(previewBuf);
            if (previewRead <= 0) return "返回数据为空";

            String previewStr = new String(previewBuf, 0, previewRead, "UTF-8").trim();

            // 如果服务端返回了 JSON，递归解析真实音频直链
            if (previewStr.startsWith("{") || previewStr.startsWith("[")) {
                StringBuilder sb = new StringBuilder(previewStr);
                byte[] temp = new byte[4096];
                int l;
                while ((l = is.read(temp)) != -1) {
                    sb.append(new String(temp, 0, l, "UTF-8"));
                }
                String jsonText = sb.toString();

                try {
                    JSONObject root = new JSONObject(jsonText);
                    JSONObject sub = root.optJSONObject("subsonic-response");
                    if (sub != null && "failed".equals(sub.optString("status"))) {
                        JSONObject err = sub.optJSONObject("error");
                        return "服务端拒绝: " + (err != null ? err.optString("message") : "认证失败");
                    }
                    String directUrl = findAudioUrlInJson(root);
                    if (directUrl != null) {
                        conn.disconnect();
                        return runDownloadPipeline(directUrl, depth + 1);
                    }
                    return "JSON 中未包含播放直链";
                } catch (Exception e) {
                    return "JSON 格式解析错误";
                }
            }

            // 如果服务端返回了 XML 报错
            if (previewStr.startsWith("<?xml") || previewStr.contains("<subsonic-response")) {
                Matcher m = Pattern.compile("message=\"([^\"]+)\"").matcher(previewStr);
                if (m.find()) return "服务端报错: " + m.group(1);
                return "服务端返回了 XML 错误";
            }

            // 写入本地临时文件
            fos = new FileOutputStream(tmpFile);
            fos.write(previewBuf, 0, previewRead);
            fos.flush();
            downloadedBytes = previewRead;

            byte[] buf = new byte[16384];
            int r;
            long lastBroadcastTime = 0;

            while ((r = is.read(buf)) != -1) {
                if (isStopped) return "已取消";
                fos.write(buf, 0, r);
                fos.flush(); // 立即刷入磁盘缓存，供本地代理线程同步读取！
                downloadedBytes += r;

                if (totalBytes > 0) {
                    long now = System.currentTimeMillis();
                    if (now - lastBroadcastTime > 500) {
                        lastBroadcastTime = now;
                        int percent = (int) ((downloadedBytes * 100) / totalBytes);
                        if (listener != null) listener.onProgress(percent);
                    }
                }
            }
            fos.flush();
            return "OK";

        } catch (Exception e) {
            return "网络异常: " + e.getMessage();
        } finally {
            try { if (fos != null) fos.close(); } catch (Exception ignored) {}
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private void startServer() {
        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!isStopped) {
                    try {
                        Socket client = serverSocket.accept();
                        handleClient(client);
                    } catch (Exception e) {
                        break;
                    }
                }
            }
        });
        serverThread.start();
    }

    // 处理 MediaPlayer 的边下边播请求
    private void handleClient(final Socket client) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                RandomAccessFile raf = null;
                OutputStream os = null;
                try {
                    client.setSoTimeout(15000);
                    InputStream cis = client.getInputStream();
                    os = client.getOutputStream();

                    // 读取 MediaPlayer 的 HTTP 请求头
                    byte[] reqBuf = new byte[2048];
                    int reqLen = cis.read(reqBuf);
                    if (reqLen <= 0) return;
                    String reqStr = new String(reqBuf, 0, reqLen);

                    long rangeStart = 0;
                    Matcher m = Pattern.compile("Range:\\s*bytes=(\\d+)-").matcher(reqStr);
                    if (m.find()) {
                        rangeStart = Long.parseLong(m.group(1));
                    }

                    // 核心优化：只要下载了前 48KB 数据，立刻给 MediaPlayer 返回 HTTP 头并吐出流！
                    long waitStart = System.currentTimeMillis();
                    while (downloadedBytes < 48 * 1024 && !downloadFinished && !downloadFailed && !isStopped) {
                        if (System.currentTimeMillis() - waitStart > 12000) break;
                        Thread.sleep(25);
                    }

                    if (downloadFailed || isStopped) {
                        os.write("HTTP/1.1 500 Internal Error\r\n\r\n".getBytes());
                        os.flush();
                        return;
                    }

                    // 响应 200 或 206
                    StringBuilder resp = new StringBuilder();
                    if (rangeStart > 0 && totalBytes > 0) {
                        resp.append("HTTP/1.1 206 Partial Content\r\n");
                        resp.append("Content-Range: bytes ").append(rangeStart).append("-").append(totalBytes - 1).append("/").append(totalBytes).append("\r\n");
                        resp.append("Content-Length: ").append(totalBytes - rangeStart).append("\r\n");
                    } else {
                        resp.append("HTTP/1.1 200 OK\r\n");
                        if (totalBytes > 0) {
                            resp.append("Content-Length: ").append(totalBytes).append("\r\n");
                        }
                    }
                    resp.append("Content-Type: ").append(audioContentType).append("\r\n");
                    resp.append("Accept-Ranges: bytes\r\n");
                    resp.append("Connection: close\r\n\r\n");

                    os.write(resp.toString().getBytes());
                    os.flush();

                    // 打开本地已下载部分的文件，向 MediaPlayer 实时推送
                    File readTarget = targetFile.exists() ? targetFile : tmpFile;
                    raf = new RandomAccessFile(readTarget, "r");
                    raf.seek(rangeStart);

                    byte[] sendBuf = new byte[8192];
                    long readPos = rangeStart;

                    while (!isStopped) {
                        long available = downloadedBytes - readPos;
                        if (available > 0) {
                            int toRead = (int) Math.min(sendBuf.length, available);
                            int actualRead = raf.read(sendBuf, 0, toRead);
                            if (actualRead > 0) {
                                os.write(sendBuf, 0, actualRead);
                                os.flush();
                                readPos += actualRead;
                            }
                            if (totalBytes > 0 && readPos >= totalBytes) {
                                break;
                            }
                        } else {
                            if (downloadFinished || downloadFailed) {
                                break;
                            }
                            Thread.sleep(20); // 追平下载进度时，等待下游写入
                        }
                    }

                } catch (Exception ignored) {
                } finally {
                    try { if (raf != null) raf.close(); } catch (Exception ignored) {}
                    try { if (os != null) os.close(); } catch (Exception ignored) {}
                    try { client.close(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    private String findAudioUrlInJson(Object json) {
        if (json instanceof JSONObject) {
            JSONObject obj = (JSONObject) json;
            String[] targetKeys = new String[]{"url", "streamUrl", "playUrl", "link", "src", "audioUrl", "musicUrl", "data"};
            for (String k : targetKeys) {
                Object val = obj.opt(k);
                if (val instanceof String) {
                    String strVal = (String) val;
                    if (strVal.startsWith("http://") || strVal.startsWith("https://")) {
                        return strVal;
                    }
                }
            }
            Iterator<?> it = obj.keys();
            while (it.hasNext()) {
                String k = (String) it.next();
                String found = findAudioUrlInJson(obj.opt(k));
                if (found != null) return found;
            }
        } else if (json instanceof JSONArray) {
            JSONArray arr = (JSONArray) json;
            for (int i = 0; i < arr.length(); i++) {
                String found = findAudioUrlInJson(arr.opt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    public synchronized void stop() {
        isStopped = true;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        if (downloadThread != null) downloadThread.interrupt();
        if (serverThread != null) serverThread.interrupt();

        // 若中断时未下载完，删除损坏的临时文件
        if (!downloadFinished && tmpFile.exists()) {
            tmpFile.delete();
        }
    }
}
