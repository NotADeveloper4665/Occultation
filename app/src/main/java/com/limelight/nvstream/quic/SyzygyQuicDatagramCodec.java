package com.limelight.nvstream.quic;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SQ1 UDP datagram framing shared with Syzygy's transport/syzygy-quic.py.
 *
 * This class deliberately handles framing only. It is not a QUIC implementation
 * and must not be used as evidence that a QUIC connection is active.
 */
public final class SyzygyQuicDatagramCodec {
    public static final int HEADER_BYTES = 10;
    public static final int MAX_PACKET_BYTES = 65_507;
    public static final int MAX_FRAGMENT_BYTES = 1_000;
    public static final int MAX_FRAGMENTS = 66;
    public static final int MAX_PENDING_PACKETS = 128;
    public static final int MAX_PENDING_BYTES = 262_144;
    public static final long REASSEMBLY_TIMEOUT_MS = 100;

    private final AtomicInteger nextSequence = new AtomicInteger();
    private final Map<Long, Assembly> pending = new HashMap<>();
    private int pendingBytes;

    public static final class Packet {
        public final int port;
        public final byte[] payload;

        public Packet(int port, byte[] payload) {
            this.port = port;
            this.payload = payload;
        }
    }

    private static final class Assembly {
        final long createdAtMs;
        final int count;
        final byte[][] fragments;
        int received;
        int bytes;

        Assembly(long createdAtMs, int count) {
            this.createdAtMs = createdAtMs;
            this.count = count;
            this.fragments = new byte[count][];
        }
    }

    /** Returns wire-format QUIC DATAGRAM payloads for one UDP packet. */
    public List<byte[]> fragment(int port, byte[] payload) {
        if (port < 0 || port > 65_535) throw new IllegalArgumentException("UDP port out of range");
        if (payload == null || payload.length > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("UDP packet exceeds SQ1 limit");
        }

        int count = Math.max(1, (payload.length + MAX_FRAGMENT_BYTES - 1) / MAX_FRAGMENT_BYTES);
        if (count > MAX_FRAGMENTS) throw new IllegalArgumentException("Too many SQ1 fragments");
        int sequence = nextSequence.getAndIncrement();
        List<byte[]> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int offset = index * MAX_FRAGMENT_BYTES;
            int length = Math.min(MAX_FRAGMENT_BYTES, payload.length - offset);
            ByteBuffer frame = ByteBuffer.allocate(HEADER_BYTES + length).order(ByteOrder.BIG_ENDIAN);
            frame.putShort((short) port);
            frame.putInt(sequence);
            frame.putShort((short) index);
            frame.putShort((short) count);
            if (length > 0) frame.put(payload, offset, length);
            result.add(frame.array());
        }
        return result;
    }

    /**
     * Accepts one SQ1 fragment and returns a completed UDP packet, or null while
     * waiting for other fragments / when the fragment is invalid or over limits.
     * The caller should supply monotonic elapsed time in milliseconds.
     */
    public synchronized Packet accept(byte[] frame, long nowMs) {
        expire(nowMs);
        if (frame == null || frame.length < HEADER_BYTES) return null;

        ByteBuffer input = ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN);
        int port = Short.toUnsignedInt(input.getShort());
        long sequence = Integer.toUnsignedLong(input.getInt());
        int index = Short.toUnsignedInt(input.getShort());
        int count = Short.toUnsignedInt(input.getShort());
        int chunkLength = input.remaining();
        if (count < 1 || count > MAX_FRAGMENTS || index >= count ||
                chunkLength > MAX_FRAGMENT_BYTES) return null;

        long key = (((long) port) << 32) | sequence;
        Assembly assembly = pending.get(key);
        if (assembly == null) {
            if (pending.size() >= MAX_PENDING_PACKETS || pendingBytes + chunkLength > MAX_PENDING_BYTES) {
                return null;
            }
            assembly = new Assembly(nowMs, count);
            pending.put(key, assembly);
        }
        if (assembly.count != count || assembly.fragments[index] != null ||
                pendingBytes + chunkLength > MAX_PENDING_BYTES) {
            return null;
        }

        byte[] chunk = new byte[chunkLength];
        input.get(chunk);
        assembly.fragments[index] = chunk;
        assembly.received++;
        assembly.bytes += chunkLength;
        pendingBytes += chunkLength;
        if (assembly.received != assembly.count) return null;

        if (assembly.bytes > MAX_PACKET_BYTES) {
            remove(key, assembly);
            return null;
        }
        byte[] payload = new byte[assembly.bytes];
        int offset = 0;
        for (byte[] part : assembly.fragments) {
            if (part == null) {
                remove(key, assembly);
                return null;
            }
            System.arraycopy(part, 0, payload, offset, part.length);
            offset += part.length;
        }
        remove(key, assembly);
        return new Packet(port, payload);
    }

    private void expire(long nowMs) {
        Iterator<Map.Entry<Long, Assembly>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Assembly> entry = iterator.next();
            Assembly value = entry.getValue();
            if (nowMs - value.createdAtMs > REASSEMBLY_TIMEOUT_MS) {
                pendingBytes -= value.bytes;
                iterator.remove();
            }
        }
    }

    private void remove(long key, Assembly assembly) {
        pending.remove(key);
        pendingBytes -= assembly.bytes;
    }

    public synchronized int getPendingPacketCount() {
        return pending.size();
    }

    public synchronized int getPendingByteCount() {
        return pendingBytes;
    }
}
