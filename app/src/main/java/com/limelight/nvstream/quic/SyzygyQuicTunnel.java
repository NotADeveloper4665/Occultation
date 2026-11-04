package com.limelight.nvstream.quic;

import android.os.SystemClock;
import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.limelight.nvstream.http.LimelightCryptoProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SQ1 QUIC tunnel. It exposes the exact loopback TCP/UDP ports expected by
 * Moonlight-core and forwards them through Syzygy's authenticated QUIC relay.
 */
public final class SyzygyQuicTunnel implements AutoCloseable {
    private static final String TAG = "SyzygyQuic";
    public static final String LOOPBACK = "127.0.0.1";
    public static final int DEFAULT_BASE_PORT = 47989;
    private final int basePort;
    private final int[] tcpPorts;
    private final int[] udpPorts;
    private static final int MAX_PACKET = 65_507;

    private final long nativeHandle;
    private final int controlStream;
    private final ExecutorService workers = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "SyzygyQuicForward");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean closed = new AtomicBoolean();
    private final SyzygyQuicDatagramCodec datagrams = new SyzygyQuicDatagramCodec();
    private final Map<Integer, ServerSocket> tcpListeners = new ConcurrentHashMap<>();
    private final Map<Integer, DatagramSocket> udpSockets = new ConcurrentHashMap<>();
    private final Map<Integer, SocketAddress> udpPeers = new ConcurrentHashMap<>();

    private SyzygyQuicTunnel(long nativeHandle, int controlStream, int basePort) throws IOException {
        this.nativeHandle = nativeHandle;
        this.controlStream = controlStream;
        this.basePort = basePort;
        this.tcpPorts = new int[] { basePort - 5, basePort + 21 };
        this.udpPorts = new int[] { basePort + 9, basePort + 10, basePort + 11 };
        try {
            for (int port : tcpPorts) {
                ServerSocket listener = new ServerSocket();
                listener.setReuseAddress(false);
                listener.bind(new InetSocketAddress(InetAddress.getByName(LOOPBACK), port), 16);
                tcpListeners.put(port, listener);
            }
            for (int port : udpPorts) {
                DatagramSocket socket = new DatagramSocket(null);
                socket.setReuseAddress(false);
                socket.bind(new InetSocketAddress(InetAddress.getByName(LOOPBACK), port));
                socket.setSoTimeout(500);
                udpSockets.put(port, socket);
            }
        } catch (IOException error) {
            close();
            throw error;
        }
        startForwarders();
    }

    public static SyzygyQuicTunnel connect(String host, int basePort, X509Certificate hostCertificate,
                                           LimelightCryptoProvider cryptoProvider) throws Exception {
        if (host == null || hostCertificate == null || cryptoProvider == null || basePort < 6 || basePort > 65504) {
            throw new IllegalArgumentException("Missing Syzygy QUIC host or paired identity");
        }
        String fingerprint = sha256(hostCertificate.getEncoded());
        if (!SyzygyQuicAuthentication.verifyHostCertificate(hostCertificate, fingerprint)) {
            throw new SecurityException("Syzygy QUIC host certificate is invalid");
        }
        InetAddress resolved = InetAddress.getByName(host);
        String address = resolved.getHostAddress();
        if (address.indexOf(':') >= 0) address = "[" + address + "]";

        long handle = SyzygyQuicNative.nativeConnect(address, basePort + 31, fingerprint);
        if (handle == 0) throw new IOException("Syzygy QUIC connection could not be created");
        try {
            String challengeLine = SyzygyQuicNative.nativeReadAuthChallenge(handle);
            JsonObject challengeObject = JsonParser.parseString(challengeLine).getAsJsonObject();
            String challenge = challengeObject.get("challenge").getAsString();
            byte[] pemBytes = cryptoProvider.getPemEncodedClientCertificate();
            if (pemBytes == null || pemBytes.length == 0) {
                throw new SecurityException("Paired client certificate is unavailable");
            }
            String certificatePem = new String(pemBytes, StandardCharsets.US_ASCII);
            String proof = SyzygyQuicAuthentication.clientProofLine(
                    challenge, certificatePem, cryptoProvider.getClientPrivateKey());
            if (!SyzygyQuicNative.nativeAuthenticate(handle, proof)) {
                throw new SecurityException("Syzygy QUIC relay rejected the paired client identity");
            }
            int control = SyzygyQuicNative.nativeOpenBi(handle, "{\"kind\":\"control\"}\n");
            if (control < 0) throw new IOException("Could not open Syzygy QUIC control stream");
            return new SyzygyQuicTunnel(handle, control, basePort);
        } catch (Exception | LinkageError error) {
            SyzygyQuicNative.nativeClose(handle);
            throw error;
        }
    }

    private static String sha256(byte[] certificateDer) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificateDer);
        StringBuilder value = new StringBuilder(64);
        for (byte b : digest) value.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        return value.toString();
    }

    private void startForwarders() {
        for (Map.Entry<Integer, ServerSocket> entry : tcpListeners.entrySet()) {
            final int port = entry.getKey();
            final ServerSocket listener = entry.getValue();
            workers.execute(() -> acceptTcp(port, listener));
        }
        for (Map.Entry<Integer, DatagramSocket> entry : udpSockets.entrySet()) {
            final int port = entry.getKey();
            final DatagramSocket socket = entry.getValue();
            workers.execute(() -> receiveUdpFromCore(port, socket));
        }
        workers.execute(this::receiveQuicDatagrams);
        workers.execute(this::receiveQuicControl);
    }

    private void acceptTcp(int port, ServerSocket listener) {
        while (!closed.get()) {
            try {
                Socket socket = listener.accept();
                socket.setTcpNoDelay(true);
                workers.execute(() -> forwardTcp(socket, port));
            } catch (IOException error) {
                if (!closed.get()) Log.w(TAG, "TCP listener " + port + " stopped", error);
                return;
            }
        }
    }

    private void forwardTcp(Socket socket, int port) {
        int stream = -1;
        AtomicInteger directions = new AtomicInteger(2);
        try {
            stream = SyzygyQuicNative.nativeOpenBi(nativeHandle,
                    "{\"kind\":\"tcp\",\"port\":" + port + "}\n");
            if (stream < 0) throw new IOException("Could not open QUIC TCP stream for " + port);
            final int streamId = stream;
            workers.execute(() -> {
                try (InputStream input = socket.getInputStream()) {
                    byte[] buffer = new byte[16 * 1024];
                    int count;
                    while (!closed.get() && (count = input.read(buffer)) >= 0) {
                        if (count == 0) continue;
                        if (!SyzygyQuicNative.nativeStreamWrite(nativeHandle, streamId,
                                Arrays.copyOf(buffer, count))) {
                            throw new IOException("QUIC TCP stream write failed");
                        }
                    }
                    SyzygyQuicNative.nativeStreamFinish(nativeHandle, streamId);
                } catch (Exception error) {
                    closeQuietly(socket);
                } finally {
                    if (directions.decrementAndGet() == 0) {
                        SyzygyQuicNative.nativeForgetStream(nativeHandle, streamId);
                        closeQuietly(socket);
                    }
                }
            });
            workers.execute(() -> {
                try (OutputStream output = socket.getOutputStream()) {
                    while (!closed.get()) {
                        byte[] data = SyzygyQuicNative.nativeStreamRead(nativeHandle, streamId, 16 * 1024, 1000);
                        if (data == null) continue;
                        if (data.length == 0) break;
                        output.write(data);
                        output.flush();
                    }
                    try { socket.shutdownOutput(); } catch (IOException ignored) { }
                } catch (Exception error) {
                    failed.set(true);
                    closeQuietly(socket);
                } finally {
                    if (directions.decrementAndGet() == 0) closeQuietly(socket);
                }
            });
        } catch (Exception error) {
            Log.w(TAG, "TCP forwarding failed on " + port, error);
            closeQuietly(socket);
        }
    }

    private void receiveUdpFromCore(int port, DatagramSocket socket) {
        byte[] buffer = new byte[MAX_PACKET];
        while (!closed.get()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                udpPeers.put(port, packet.getSocketAddress());
                byte[] payload = Arrays.copyOfRange(packet.getData(), packet.getOffset(),
                        packet.getOffset() + packet.getLength());
                if (port == basePort + 10) {
                    writeControlPacket(payload);
                } else {
                    for (byte[] fragment : datagrams.fragment(port, payload)) {
                        if (!SyzygyQuicNative.nativeSendDatagram(nativeHandle, fragment)) {
                            throw new IOException("QUIC media datagram send failed");
                        }
                    }
                }
            } catch (java.net.SocketTimeoutException ignored) {
                // Periodic wake-up lets shutdown close the worker promptly.
            } catch (Exception error) {
                if (!closed.get()) Log.w(TAG, "UDP forwarding failed on " + port, error);
                return;
            }
        }
    }

    private void writeControlPacket(byte[] payload) throws IOException {
        if (payload.length > MAX_PACKET) throw new IOException("Oversized control datagram");
        ByteBuffer frame = ByteBuffer.allocate(4 + payload.length).order(ByteOrder.BIG_ENDIAN);
        frame.putInt(payload.length).put(payload);
        if (!SyzygyQuicNative.nativeStreamWrite(nativeHandle, controlStream, frame.array())) {
            throw new IOException("QUIC control stream write failed");
        }
    }

    private void receiveQuicDatagrams() {
        while (!closed.get()) {
            try {
                byte[] frame = SyzygyQuicNative.nativeReceiveDatagram(nativeHandle, 500);
                if (frame == null) continue;
                SyzygyQuicDatagramCodec.Packet packet = datagrams.accept(frame, SystemClock.elapsedRealtime());
                if (packet != null) sendUdpToCore(packet.port, packet.payload);
            } catch (Exception error) {
                if (!closed.get()) Log.w(TAG, "QUIC media receive failed", error);
                return;
            }
        }
    }

    private void receiveQuicControl() {
        ByteArrayOutputStream pending = new ByteArrayOutputStream();
        while (!closed.get()) {
            try {
                byte[] chunk = SyzygyQuicNative.nativeStreamRead(nativeHandle, controlStream, 16 * 1024, 500);
                if (chunk == null) continue;
                if (chunk.length == 0) throw new IOException("QUIC control stream closed");
                pending.write(chunk);
                byte[] all = pending.toByteArray();
                int offset = 0;
                while (all.length - offset >= 4) {
                    int length = ByteBuffer.wrap(all, offset, 4).order(ByteOrder.BIG_ENDIAN).getInt();
                    if (length < 0 || length > MAX_PACKET) throw new IOException("Invalid QUIC control frame length");
                    if (all.length - offset < 4 + length) break;
                    byte[] payload = Arrays.copyOfRange(all, offset + 4, offset + 4 + length);
                    sendUdpToCore(basePort + 10, payload);
                    offset += 4 + length;
                }
                pending.reset();
                if (all.length - offset > MAX_PACKET + 4) throw new IOException("QUIC control buffer limit exceeded");
                pending.write(all, offset, all.length - offset);
            } catch (Exception error) {
                if (!closed.get()) Log.w(TAG, "QUIC control receive failed", error);
                return;
            }
        }
    }

    private void sendUdpToCore(int port, byte[] payload) throws IOException {
        DatagramSocket socket = udpSockets.get(port);
        SocketAddress peer = udpPeers.get(port);
        if (socket == null || peer == null || socket.isClosed()) return;
        socket.send(new DatagramPacket(payload, payload.length, peer));
    }

    public String rewriteRtspUrl(String originalUrl) throws Exception {
        URI uri = new URI(originalUrl);
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IOException("Unexpected RTSP session URL");
        }
        String authority = uri.getRawAuthority();
        int schemeSeparator = originalUrl.indexOf("//");
        if (schemeSeparator < 0 || authority == null) throw new IOException("Invalid RTSP authority");
        int authorityStart = schemeSeparator + 2;
        int authorityEnd = originalUrl.indexOf('/', authorityStart);
        if (authorityEnd < 0) authorityEnd = originalUrl.length();
        String loopbackAuthority = LOOPBACK + (uri.getPort() >= 0 ? ":" + uri.getPort() : "");
        return originalUrl.substring(0, authorityStart) + loopbackAuthority + originalUrl.substring(authorityEnd);
    }

    public static String loopbackAddress() {
        return LOOPBACK;
    }

    private static void closeQuietly(Socket socket) {
        try { socket.close(); } catch (IOException ignored) { }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        for (ServerSocket server : tcpListeners.values()) {
            try { server.close(); } catch (IOException ignored) { }
        }
        for (DatagramSocket socket : udpSockets.values()) socket.close();
        workers.shutdownNow();
        try { workers.awaitTermination(2, TimeUnit.SECONDS); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
        SyzygyQuicNative.nativeClose(nativeHandle);
    }
}
