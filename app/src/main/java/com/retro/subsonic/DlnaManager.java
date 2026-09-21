package com.retro.subsonic;

import android.content.Context;
import android.net.wifi.WifiManager;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DlnaManager {

    public static class Device {
        public String friendlyName;
        public String location;
        public String controlUrl;

        public Device(String friendlyName, String location, String controlUrl) {
            this.friendlyName = friendlyName;
            this.location = location;
            this.controlUrl = controlUrl;
        }

        @Override
        public String toString() {
            return friendlyName;
        }
    }

    public interface DiscoveryCallback {
        void onDeviceFound(Device device);
        void onDiscoveryFinished(List<Device> devices);
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(String error);
    }

    private static DlnaManager instance;
    private Context appContext;
    private WifiManager.MulticastLock multicastLock;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Device currentCastDevice = null;

    public static synchronized DlnaManager getInstance(Context context) {
        if (instance == null) {
            instance = new DlnaManager(context.getApplicationContext());
        }
        return instance;
    }

    private DlnaManager(Context context) {
        this.appContext = context;
    }

    public Device getCurrentCastDevice() {
        return currentCastDevice;
    }

    public void clearCurrentCastDevice() {
        this.currentCastDevice = null;
    }

    public void startDiscovery(final DiscoveryCallback callback) {
        final List<Device> resultList = new ArrayList<Device>();
        final Map<String, Boolean> seenLocations = new HashMap<String, Boolean>();

        new Thread(new Runnable() {
            @Override
            public void run() {
                acquireMulticastLock();
                DatagramSocket socket = null;
                try {
                    socket = new DatagramSocket();
                    socket.setSoTimeout(3500);

                    String ssdpQuery = "M-SEARCH * HTTP/1.1\r\n" +
                            "HOST: 239.255.255.250:1900\r\n" +
                            "MAN: \"ssdp:discover\"\r\n" +
                            "MX: 3\r\n" +
                            "ST: urn:schemas-upnp-org:service:AVTransport:1\r\n\r\n";

                    byte[] sendData = ssdpQuery.getBytes("UTF-8");
                    DatagramPacket sendPacket = new DatagramPacket(
                            sendData,
                            sendData.length,
                            InetAddress.getByName("239.255.255.250"),
                            1900
                    );
                    socket.send(sendPacket);

                    long endTime = System.currentTimeMillis() + 3500;
                    byte[] buf = new byte[4096];

                    while (System.currentTimeMillis() < endTime) {
                        try {
                            DatagramPacket receivePacket = new DatagramPacket(buf, buf.length);
                            socket.receive(receivePacket);

                            String response = new String(receivePacket.getData(), 0, receivePacket.getLength(), "UTF-8");
                            String location = parseHeader(response, "LOCATION");

                            if (location != null && !seenLocations.containsKey(location)) {
                                seenLocations.put(location, true);
                                parseDeviceDescription(location, resultList, callback);
                            }
                        } catch (Exception timeout) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                    releaseMulticastLock();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) callback.onDiscoveryFinished(resultList);
                        }
                    });
                }
            }
        }).start();
    }

    private String parseHeader(String response, String headerName) {
        String[] lines = response.split("\r\n");
        for (String line : lines) {
            if (line.toUpperCase().startsWith(headerName.toUpperCase() + ":")) {
                return line.substring(headerName.length() + 1).trim();
            }
        }
        return null;
    }

    private void parseDeviceDescription(final String locationUrl, final List<Device> resultList, final DiscoveryCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(locationUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);

                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    reader.close();
                    conn.disconnect();

                    String xml = sb.toString();
                    String friendlyName = extractTag(xml, "friendlyName");
                    if (friendlyName == null || friendlyName.length() == 0) {
                        friendlyName = "DLNA 音频渲染器";
                    }

                    String controlUrl = findAvTransportControlUrl(xml, locationUrl);
                    if (controlUrl != null) {
                        final Device dev = new Device(friendlyName, locationUrl, controlUrl);
                        synchronized (resultList) {
                            resultList.add(dev);
                        }
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) callback.onDeviceFound(dev);
                            }
                        });
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private String extractTag(String xml, String tag) {
        int start = xml.indexOf("<" + tag + ">");
        int end = xml.indexOf("</" + tag + ">");
        if (start != -1 && end != -1 && end > start) {
            return xml.substring(start + tag.length() + 2, end).trim();
        }
        return null;
    }

    private String findAvTransportControlUrl(String xml, String locationUrl) {
        int avIndex = xml.indexOf("urn:schemas-upnp-org:service:AVTransport:1");
        if (avIndex == -1) {
            avIndex = xml.indexOf(":AVTransport:");
        }
        if (avIndex != -1) {
            int serviceBlockStart = xml.lastIndexOf("<service>", avIndex);
            int serviceBlockEnd = xml.indexOf("</service>", avIndex);
            if (serviceBlockStart != -1 && serviceBlockEnd != -1) {
                String block = xml.substring(serviceBlockStart, serviceBlockEnd);
                String relControl = extractTag(block, "controlURL");
                if (relControl != null) {
                    if (relControl.startsWith("http://") || relControl.startsWith("https://")) {
                        return relControl;
                    }
                    try {
                        URL base = new URL(locationUrl);
                        return new URL(base, relControl).toString();
                    } catch (Exception e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    public void playMedia(final Device device, final String mediaUrl, final String title, final String artist, final ActionCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    stopMediaSync(device);

                    String setUriSoap = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                            "<s:Body>" +
                            "<u:SetAVTransportURI xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                            "<InstanceID>0</InstanceID>" +
                            "<CurrentURI>" + escapeXml(mediaUrl) + "</CurrentURI>" +
                            "<CurrentURIMetaData>" + escapeXml(buildDidlLite(mediaUrl, title, artist)) + "</CurrentURIMetaData>" +
                            "</u:SetAVTransportURI>" +
                            "</s:Body>" +
                            "</s:Envelope>";

                    sendSoapAction(device.controlUrl, "urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI", setUriSoap);

                    Thread.sleep(300);

                    String playSoap = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                            "<s:Body>" +
                            "<u:Play xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                            "<InstanceID>0</InstanceID>" +
                            "<Speed>1</Speed>" +
                            "</u:Play>" +
                            "</s:Body>" +
                            "</s:Envelope>";

                    sendSoapAction(device.controlUrl, "urn:schemas-upnp-org:service:AVTransport:1#Play", playSoap);

                    currentCastDevice = device;

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) callback.onSuccess();
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) callback.onError(e.getMessage() != null ? e.getMessage() : "投播连接失败");
                        }
                    });
                }
            }
        }).start();
    }

    public void stopMedia(final Device device, final ActionCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    stopMediaSync(device);
                    currentCastDevice = null;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) callback.onSuccess();
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) callback.onError(e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void stopMediaSync(Device device) throws Exception {
        if (device == null || device.controlUrl == null) return;
        String stopSoap = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                "<s:Body>" +
                "<u:Stop xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                "<InstanceID>0</InstanceID>" +
                "</u:Stop>" +
                "</s:Body>" +
                "</s:Envelope>";
        sendSoapAction(device.controlUrl, "urn:schemas-upnp-org:service:AVTransport:1#Stop", stopSoap);
    }

    private void sendSoapAction(String controlUrl, String soapAction, String soapXml) throws Exception {
        URL url = new URL(controlUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
        conn.setRequestProperty("SOAPAction", "\"" + soapAction + "\"");

        byte[] payload = soapXml.getBytes("UTF-8");
        conn.setRequestProperty("Content-Length", String.valueOf(payload.length));

        OutputStream os = conn.getOutputStream();
        os.write(payload);
        os.flush();
        os.close();

        int code = conn.getResponseCode();
        if (code >= 200 && code < 300) {
            conn.disconnect();
        } else {
            conn.disconnect();
            throw new Exception("DLNA 响应异常 (HTTP " + code + ")");
        }
    }

    private String buildDidlLite(String uri, String title, String artist) {
        return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
                "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
                "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
                "<item id=\"0\" parentID=\"-1\" restricted=\"1\">" +
                "<dc:title>" + (title != null ? title : "Audio") + "</dc:title>" +
                "<dc:creator>" + (artist != null ? artist : "Artist") + "</dc:creator>" +
                "<upnp:class>object.item.audioItem.musicTrack</upnp:class>" +
                "<res protocolInfo=\"http-get:*:audio/mpeg:*\">" + uri + "</res>" +
                "</item>" +
                "</DIDL-Lite>";
    }

    private String escapeXml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private void acquireMulticastLock() {
        try {
            if (multicastLock == null) {
                WifiManager wifi = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
                if (wifi != null) {
                    multicastLock = wifi.createMulticastLock("RetroSubsonicDlnaLock");
                    multicastLock.setReferenceCounted(true);
                }
            }
            if (multicastLock != null && !multicastLock.isHeld()) {
                multicastLock.acquire();
            }
        } catch (Exception ignored) {}
    }

    private void releaseMulticastLock() {
        try {
            if (multicastLock != null && multicastLock.isHeld()) {
                multicastLock.release();
            }
        } catch (Exception ignored) {}
    }
}
