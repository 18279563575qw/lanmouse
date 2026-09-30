package com.example.lanmouse;

import android.os.SystemClock;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

/**
 * 局域网 UDP 指令客户端。
 *
 * 所有 socket 操作（地址解析、发送、接收）都在后台线程 "LanMouse-Sender" 上执行。
 * Android 从 3.0 起禁止在主线程做网络 I/O：只要在主线程调用
 * {@link DatagramSocket#send}，系统就会抛出 NetworkOnMainThreadException，
 * 而这个异常的 getMessage() 是 null，界面便会显示“发送移动指令失败: null”。
 * 触控板的移动、点击事件都在主线程回调，所以之前每一次发送都会失败。
 */
public class UdpMouseClient {
    public static class DiscoveryResult {
        public final String name;
        public final String host;
        public final int port;

        public DiscoveryResult(String name, String host, int port) {
            this.name = name;
            this.host = host;
            this.port = port;
        }
    }

    public interface Listener {
        /** 已解析地址并收到服务端的 pong，连接确认成功，可以发送指令了。 */
        void onReady(String host, int port);

        /** 连接或发送失败。可能在后台线程回调，实现方需自行切回主线程。 */
        void onError(String message);
    }

    /** 待发送队列上限；触控板事件比网络快时丢弃最旧的移动指令，避免延迟累积。 */
    private static final int MAX_PENDING_PACKETS = 256;
    /** 连接确认（ping/pong）等待时间。 */
    private static final int CONFIRM_TIMEOUT_MS = 1500;
    /** 工作线程空闲时的等待时间，用于让线程在没有任务时休眠而不是空转。 */
    private static final long IDLE_WAIT_MS = 250L;

    private static final class ConfigRequest {
        final String host;
        final int port;
        final String token;

        ConfigRequest(String host, int port, String token) {
            this.host = host;
            this.port = port;
            this.token = token;
        }
    }

    private final Object stateLock = new Object();
    private final ArrayDeque<JSONObject> pendingPackets = new ArrayDeque<JSONObject>();
    private final Listener listener;
    private final Thread worker;

    private ConfigRequest pendingConfig;
    private DatagramSocket socket;
    private InetAddress address;
    private int port = 8765;
    private String token = "lanmouse";
    private boolean ready;
    private boolean closed;
    private long lastErrorAt;

    public UdpMouseClient(Listener listener) {
        this.listener = listener;
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                runWorker();
            }
        }, "LanMouse-Sender");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 请求连接目标。地址解析和连通性探测都在后台线程完成，
     * 结果通过 {@link Listener#onReady} 或 {@link Listener#onError} 回调。
     */
    public void configure(String host, int port, String token) {
        synchronized (stateLock) {
            if (closed) {
                return;
            }
            ready = false;
            pendingPackets.clear();
            pendingConfig = new ConfigRequest(host, port, token);
            stateLock.notifyAll();
        }
    }

    /** 是否已连上服务端（收到过 pong）。 */
    public boolean isReady() {
        synchronized (stateLock) {
            return ready && !closed;
        }
    }

    public boolean isConfigured() {
        return isReady();
    }

    public void move(float dx, float dy) {
        if (Math.abs(dx) < 0.01f && Math.abs(dy) < 0.01f) {
            return;
        }

        try {
            JSONObject packet = base("move");
            packet.put("dx", round3(dx));
            packet.put("dy", round3(dy));
            enqueue(packet);
        } catch (Exception ex) {
            report("发送移动指令失败: " + describe(ex));
        }
    }

    public void button(String button, String action) {
        try {
            JSONObject packet = base("button");
            packet.put("button", button);
            packet.put("action", action);
            enqueue(packet);
        } catch (Exception ex) {
            report("发送按键指令失败: " + describe(ex));
        }
    }

    public void scroll(int delta) {
        try {
            JSONObject packet = base("scroll");
            packet.put("delta", delta);
            enqueue(packet);
        } catch (Exception ex) {
            report("发送滚轮指令失败: " + describe(ex));
        }
    }

    public void ping() {
        try {
            enqueue(base("ping"));
        } catch (Exception ex) {
            report("发送测试请求失败: " + describe(ex));
        }
    }

    /**
     * 广播搜索局域网内的服务端。会阻塞当前线程，必须在非主线程调用。
     */
    public DiscoveryResult discover(int timeoutMs) {
        DatagramSocket probe = null;
        try {
            synchronized (stateLock) {
                if (closed) {
                    return null;
                }
            }

            probe = new DatagramSocket();
            probe.setBroadcast(true);
            int timeout = Math.max(250, timeoutMs);
            byte[] requestBytes = "{\"v\":1,\"type\":\"discover\"}".getBytes(StandardCharsets.UTF_8);
            DatagramPacket request = new DatagramPacket(
                    requestBytes,
                    requestBytes.length,
                    InetAddress.getByName("255.255.255.255"),
                    8765);

            long deadline = SystemClock.uptimeMillis() + timeout;
            probe.send(request);

            byte[] buffer = new byte[2048];
            while (SystemClock.uptimeMillis() < deadline) {
                int remaining = (int) Math.max(1L, deadline - SystemClock.uptimeMillis());
                probe.setSoTimeout(remaining);
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                try {
                    probe.receive(response);
                } catch (SocketTimeoutException ex) {
                    return null;
                }

                String jsonText = new String(
                        response.getData(),
                        response.getOffset(),
                        response.getLength(),
                        StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(jsonText);
                if ("discovery".equals(json.optString("type"))) {
                    String name = json.optString("name", "Windows PC");
                    int port = json.optInt("port", 8765);
                    return new DiscoveryResult(name, response.getAddress().getHostAddress(), port);
                }
            }
        } catch (Exception ex) {
            report("搜索失败: " + describe(ex));
        } finally {
            if (probe != null) {
                probe.close();
            }
        }
        return null;
    }

    public void close() {
        DatagramSocket localSocket;
        synchronized (stateLock) {
            closed = true;
            ready = false;
            pendingPackets.clear();
            pendingConfig = null;
            localSocket = socket;
            stateLock.notifyAll();
        }
        if (localSocket != null) {
            localSocket.close();
        }
    }

    private void runWorker() {
        DatagramSocket localSocket;
        try {
            localSocket = new DatagramSocket();
        } catch (Exception ex) {
            reportCritical("无法创建 UDP Socket: " + describe(ex));
            return;
        }

        synchronized (stateLock) {
            if (closed) {
                localSocket.close();
                return;
            }
            socket = localSocket;
        }

        try {
            while (true) {
                synchronized (stateLock) {
                    if (closed) {
                        return;
                    }
                }

                ConfigRequest config = takeConfig();
                if (config != null) {
                    applyConfig(localSocket, config);
                    continue;
                }

                JSONObject packet = takePacket();
                if (packet == null) {
                    waitForWork();
                    continue;
                }

                send(localSocket, packet);
            }
        } finally {
            synchronized (stateLock) {
                if (socket == localSocket) {
                    socket = null;
                }
            }
            localSocket.close();
        }
    }

    private ConfigRequest takeConfig() {
        synchronized (stateLock) {
            ConfigRequest config = pendingConfig;
            pendingConfig = null;
            return config;
        }
    }

    private JSONObject takePacket() {
        synchronized (stateLock) {
            return pendingPackets.pollFirst();
        }
    }

    private void waitForWork() {
        synchronized (stateLock) {
            if (closed || pendingConfig != null || !pendingPackets.isEmpty()) {
                return;
            }
            try {
                stateLock.wait(IDLE_WAIT_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void applyConfig(DatagramSocket localSocket, ConfigRequest config) {
        InetAddress resolved;
        try {
            resolved = InetAddress.getByName(config.host);
        } catch (Exception ex) {
            markNotReady();
            reportCritical("无法解析地址 " + config.host + ": " + describe(ex));
            return;
        }

        synchronized (stateLock) {
            if (closed) {
                return;
            }
            address = resolved;
            port = config.port;
            token = config.token;
        }

        try {
            JSONObject ping = new JSONObject();
            ping.put("v", 1);
            ping.put("type", "ping");
            ping.put("token", config.token);

            byte[] bytes = ping.toString().getBytes(StandardCharsets.UTF_8);
            localSocket.send(new DatagramPacket(bytes, bytes.length, resolved, config.port));

            if (waitForPong(localSocket, config)) {
                synchronized (stateLock) {
                    if (closed) {
                        return;
                    }
                    ready = true;
                }
                if (listener != null) {
                    listener.onReady(config.host, config.port);
                }
                return;
            }

            markNotReady();
            reportCritical("Windows 端没有响应 " + config.host + ":" + config.port
                    + "。请确认服务端仍在运行、端口和令牌正确，并允许防火墙通过 UDP。");
        } catch (Exception ex) {
            markNotReady();
            reportCritical("连接 " + config.host + ":" + config.port + " 失败: " + describe(ex));
        }
    }

    /**
     * 等待服务端的 pong。只有真正收到目标地址回包才算连接成功，
     * 这样令牌错误、端口写错、防火墙拦截都会给出明确提示，而不是静默失败。
     */
    private boolean waitForPong(DatagramSocket localSocket, ConfigRequest config) {
        long deadline = SystemClock.uptimeMillis() + CONFIRM_TIMEOUT_MS;
        byte[] buffer = new byte[512];

        while (SystemClock.uptimeMillis() < deadline) {
            synchronized (stateLock) {
                if (closed) {
                    return false;
                }
            }

            int remaining = (int) Math.max(1L, deadline - SystemClock.uptimeMillis());
            try {
                localSocket.setSoTimeout(remaining);
            } catch (Exception ex) {
                return false;
            }

            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            try {
                localSocket.receive(response);
            } catch (Exception ex) {
                return false;
            }

            if (response.getPort() != config.port) {
                continue;
            }

            String text = new String(
                    response.getData(),
                    response.getOffset(),
                    response.getLength(),
                    StandardCharsets.UTF_8);
            try {
                if ("pong".equals(new JSONObject(text).optString("type"))) {
                    return true;
                }
            } catch (Exception ignored) {
                // 不是 JSON，忽略后继续等待 pong。
            }
        }
        return false;
    }

    private void send(DatagramSocket localSocket, JSONObject packet) {
        InetAddress target;
        int targetPort;
        synchronized (stateLock) {
            if (closed || !ready || address == null) {
                return;
            }
            target = address;
            targetPort = port;
        }

        try {
            byte[] bytes = packet.toString().getBytes(StandardCharsets.UTF_8);
            localSocket.send(new DatagramPacket(bytes, bytes.length, target, targetPort));
        } catch (Exception ex) {
            report("发送指令失败: " + describe(ex));
        }
    }

    private void enqueue(JSONObject packet) {
        synchronized (stateLock) {
            if (closed) {
                return;
            }
            while (pendingPackets.size() >= MAX_PENDING_PACKETS) {
                pendingPackets.pollFirst();
            }
            pendingPackets.addLast(packet);
            stateLock.notifyAll();
        }
    }

    private void markNotReady() {
        synchronized (stateLock) {
            ready = false;
        }
    }

    private JSONObject base(String type) throws Exception {
        String currentToken;
        synchronized (stateLock) {
            currentToken = token;
        }

        JSONObject packet = new JSONObject();
        packet.put("v", 1);
        packet.put("type", type);
        packet.put("token", currentToken);
        return packet;
    }

    private float round3(float value) {
        return Math.round(value * 1000f) / 1000f;
    }

    /**
     * 异常信息兜底：NetworkOnMainThreadException 等异常的 getMessage() 是 null，
     * 直接拼接就会在界面上显示“null”，所以这里回退到异常类名。
     */
    private String describe(Exception ex) {
        String message = ex.getMessage();
        if (message != null && message.trim().length() > 0) {
            return message;
        }

        String name = ex.getClass().getSimpleName();
        if (name == null || name.length() == 0) {
            name = ex.getClass().getName();
        }
        return name + "（系统未提供详细信息）";
    }

    /** 高频错误（例如丢包）限流上报，避免刷屏。 */
    private void report(String message) {
        long now = SystemClock.uptimeMillis();
        if (now - lastErrorAt < 1000) {
            return;
        }
        lastErrorAt = now;
        if (listener != null) {
            listener.onError(message);
        }
    }

    /** 连接结果必须上报，不受限流影响。 */
    private void reportCritical(String message) {
        lastErrorAt = SystemClock.uptimeMillis();
        if (listener != null) {
            listener.onError(message);
        }
    }
}
