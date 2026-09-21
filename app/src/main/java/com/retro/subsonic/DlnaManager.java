package com.retro.subsonic;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class DlnaManager {

    public static class Device {
        public String name;
        public String location;
        public String avTransportUrl;

        public Device(String name, String location, String avTransportUrl) {
            this.name = name;
            this.location = location;
            this.avTransportUrl = avTransportUrl;
        }

        @Override
        public String toString() {
            return name != null ? name : "未命名 DLNA 设备";
        }
    }

    public interface DiscoveryCallback {
        void onDeviceFound(Device device);
    }

    public interface PositionCallback {
        void onPositionInfo(int positionMs, int durationMs);
    }

    private static Device currentActiveDevice = null;
    private static boolean isDlnaCasting = false;
    private static Handler pollHandler = new Handler(Looper.getMainLooper());
    private static Runnable pollRunnable;

    public static boolean isCasting() {
        return isDlnaCasting && currentActiveDevice != null;
    }

    public static Device getCurrentDevice() {
        return currentActiveDevice;
    }

    public static void disconnect() {
        stopPositionPolling();
        if (currentActiveDevice != null) {
            final Device dev = currentActiveDevice;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    executeSoapAction(dev.avTransportUrl, "Stop", "<InstanceID>0</InstanceID>");
                }
            }).start();
        }
        currentActiveDevice = null;
        isDlnaCasting = false;
    }

    private static void startPositionPolling(final String controlUrl, final PositionCallback callback) {
        stopPositionPolling();
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isCasting() || currentActiveDevice == null) return;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String res = executeSoapAction(controlUrl, "GetPositionInfo", "<InstanceID>0</InstanceID>");
                        if (res != null) {
                            String relTime = extractTag(res, "RelTime");
                            String trackDur = extractTag(res, "TrackDuration");
                            final int posMs = parseTimeToMs(relTime);
                            final int durMs = parseTimeToMs(trackDur);
                            if (posMs >= 0 && callback != null) {
                                pollHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onPositionInfo(posMs, durMs);
                                    }
                                });
                            }
                        }
                    }
                }).start();
                pollHandler.postDelayed(this, 1000);
            }
        };
        pollHandler.postDelayed(pollRunnable, 1000);
    }

    private static void stopPositionPolling() {
        if (pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
            pollRunnable = null;
        }
    }

    private static int parseTimeToMs(String timeStr) {
        if (timeStr == null || timeStr.length() < 8) return -1;
        try {
            String[] parts = timeStr.split(":");
            if (parts.length >= 3) {
                int hr = Integer.parseInt(parts[0]);
                int min = Integer.parseInt(parts[1]);
                float sec = Float.parseFloat(parts[2]);
                return (int) ((hr * 3600 + min * 60 + sec) * 1000);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    public static void searchDevices(final DiscoveryCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                DatagramSocket socket = null;
                try {
                    String ssdpQuery = "M-SEARCH * HTTP/1.1\r\n" +
                            "HOST: 239.255.255.250:1900\r\n" +
                            "MAN: \"ssdp:discover\"\r\n" +
                            "MX: 3\r\n" +
                            "ST: urn:schemas-upnp-org:service:AVTransport:1\r\n\r\n";

                    byte[] sendData = ssdpQuery.getBytes("UTF-8");
                    socket = new DatagramSocket();
                    socket.setSoTimeout(3500);

                    DatagramPacket sendPacket = new DatagramPacket(
                            sendData,
                            sendData.length,
                            InetAddress.getByName("239.255.255.250"),
                            1900
                    );
                    socket.send(sendPacket);

                    byte[] receiveBuffer = new byte[8192];
                    long startTime = System.currentTimeMillis();
                    final Map<String, Boolean> foundLocations = new HashMap<String, Boolean>();

                    while (System.currentTimeMillis() - startTime < 3500) {
                        try {
                            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                            socket.receive(receivePacket);
                            String res = new String(receivePacket.getData(), 0, receivePacket.getLength(), "UTF-8");

                            String location = parseHeader(res, "LOCATION");
                            if (location != null && !foundLocations.containsKey(location)) {
                                foundLocations.put(location, true);
                                parseDeviceDescription(location, callback);
                            }
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {
                } finally {
                    if (socket != null) {
                        socket.close();
                    }
                }
            }
        }).start();
    }

    private static void parseDeviceDescription(final String locationUrl, final DiscoveryCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(locationUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);
                    InputStream is = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    StringBuilder xml = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        xml.append(line).append("\n");
                    }
                    reader.close();

                    String fullXml = xml.toString();
                    String friendlyName = extractTag(fullXml, "friendlyName");
                    if (friendlyName == null || friendlyName.length() == 0) {
                        friendlyName = "DLNA 投播设备 (" + url.getHost() + ")";
                    }

                    String controlUrl = extractAvTransportControlUrl(fullXml);
                    if (controlUrl != null) {
                        String finalControlUrl;
                        if (controlUrl.startsWith("http://") || controlUrl.startsWith("https://")) {
                            finalControlUrl = controlUrl;
                        } else {
                            if (!controlUrl.startsWith("/")) controlUrl = "/" + controlUrl;
                            finalControlUrl = "http://" + url.getHost() + ":" + url.getPort() + controlUrl;
                        }

                        final Device device = new Device(friendlyName, locationUrl, finalControlUrl);
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onDeviceFound(device);
                            }
                        });
                    }
                } catch (Exception ignored) {
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }

    public static void playUrl(final Device device, final String mediaUrl, final String title, final String artist, final int positionMs, final PositionCallback callback) {
        currentActiveDevice = device;
        isDlnaCasting = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String didl = "&lt;DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
                            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
                            "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"&gt;" +
                            "&lt;item id=\"1\" parentID=\"0\" restricted=\"1\"&gt;" +
                            "&lt;dc:title&gt;" + escapeXml(title) + "&lt;/dc:title&gt;" +
                            "&lt;dc:creator&gt;" + escapeXml(artist) + "&lt;/dc:creator&gt;" +
                            "&lt;upnp:class&gt;object.item.audioItem.musicTrack&lt;/upnp:class&gt;" +
                            "&lt;res protocolInfo=\"http-get:*:audio/mpeg:*\"&gt;" + escapeXml(mediaUrl) + "&lt;/res&gt;" +
                            "&lt;/item&gt;&lt;/DIDL-Lite&gt;";

                    String setUriArgs = "<InstanceID>0</InstanceID>" +
                            "<CurrentURI>" + escapeXml(mediaUrl) + "</CurrentURI>" +
                            "<CurrentURIMetaData>" + didl + "</CurrentURIMetaData>";

                    executeSoapAction(device.avTransportUrl, "SetAVTransportURI", setUriArgs);
                    Thread.sleep(150);

                    if (positionMs > 1000) {
                        int sec = (positionMs / 1000) % 60;
                        int min = (positionMs / (1000 * 60)) % 60;
                        int hr = positionMs / (1000 * 60 * 60);
                        String timeStr = String.format("%02d:%02d:%02d", hr, min, sec);
                        String seekArgs = "<InstanceID>0</InstanceID><Unit>REL_TIME</Unit><Target>" + timeStr + "</Target>";
                        executeSoapAction(device.avTransportUrl, "Seek", seekArgs);
                    }

                    executeSoapAction(device.avTransportUrl, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>");

                    // 启动位置轮询以实时同步进度与歌词
                    startPositionPolling(device.avTransportUrl, callback);
                } catch (Exception ignored) {}
            }
        }).start();
    }

    public static void seek(final int positionMs) {
        if (!isCasting() || currentActiveDevice == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int sec = (positionMs / 1000) % 60;
                    int min = (positionMs / (1000 * 60)) % 60;
                    int hr = positionMs / (1000 * 60 * 60);
                    String timeStr = String.format("%02d:%02d:%02d", hr, min, sec);
                    String seekArgs = "<InstanceID>0</InstanceID><Unit>REL_TIME</Unit><Target>" + timeStr + "</Target>";
                    executeSoapAction(currentActiveDevice.avTransportUrl, "Seek", seekArgs);
                } catch (Exception ignored) {}
            }
        }).start();
    }

    public static void pause() {
        if (!isCasting() || currentActiveDevice == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                executeSoapAction(currentActiveDevice.avTransportUrl, "Pause", "<InstanceID>0</InstanceID>");
            }
        }).start();
    }

    public static void resume() {
        if (!isCasting() || currentActiveDevice == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                executeSoapAction(currentActiveDevice.avTransportUrl, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>");
            }
        }).start();
    }

    private static String executeSoapAction(String controlUrl, String action, String argsXml) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(controlUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setDoOutput(true);

            String soapBody = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                    "<s:Envelope s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\" xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
                    "<s:Body>" +
                    "<u:" + action + " xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                    argsXml +
                    "</u:" + action + ">" +
                    "</s:Body>" +
                    "</s:Envelope>";

            byte[] bodyBytes = soapBody.getBytes("UTF-8");
            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
            conn.setRequestProperty("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#" + action + "\"");
            conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));

            OutputStream os = conn.getOutputStream();
            os.write(bodyBytes);
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            InputStream is = (code == 200 || code == 206) ? conn.getInputStream() : conn.getErrorStream();
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                return sb.toString();
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static String parseHeader(String text, String headerName) {
        String[] lines = text.split("\r\n");
        for (String line : lines) {
            int idx = line.indexOf(':');
            if (idx > 0) {
                String name = line.substring(0, idx).trim();
                if (name.equalsIgnoreCase(headerName)) {
                    return line.substring(idx + 1).trim();
                }
            }
        }
        return null;
    }

    private static String extractTag(String xml, String tag) {
        if (xml == null) return null;
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int s = xml.indexOf(open);
        int e = xml.indexOf(close);
        if (s != -1 && e > s) {
            return xml.substring(s + open.length(), e).trim();
        }
        return null;
    }

    private static String extractAvTransportControlUrl(String xml) {
        int idx = xml.indexOf("urn:schemas-upnp-org:service:AVTransport:1");
        if (idx != -1) {
            int cStart = xml.indexOf("<controlURL>", idx);
            int cEnd = xml.indexOf("</controlURL>", cStart);
            if (cStart != -1 && cEnd > cStart) {
                return xml.substring(cStart + "<controlURL>".length(), cEnd).trim();
            }
        }
        return null;
    }

    private static String escapeXml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
