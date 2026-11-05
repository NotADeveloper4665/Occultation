package com.limelight.nvstream.quic;

import org.junit.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import static org.junit.Assert.*;

public class SyzygyQuicDatagramCodecTest {
    @Test public void singleFragmentRoundTripsAndPreservesPort() {
        SyzygyQuicDatagramCodec sender = new SyzygyQuicDatagramCodec();
        SyzygyQuicDatagramCodec receiver = new SyzygyQuicDatagramCodec();
        byte[] payload = new byte[] {0, 1, 2, (byte) 0xff};
        List<byte[]> frames = sender.fragment(48000, payload);
        assertEquals(1, frames.size());
        SyzygyQuicDatagramCodec.Packet packet = receiver.accept(frames.get(0), 10);
        assertNotNull(packet);
        assertEquals(48000, packet.port);
        assertArrayEquals(payload, packet.payload);
    }

    @Test public void multiFragmentRoundTripsOutOfOrder() {
        SyzygyQuicDatagramCodec sender = new SyzygyQuicDatagramCodec();
        SyzygyQuicDatagramCodec receiver = new SyzygyQuicDatagramCodec();
        byte[] payload = new byte[2_501];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i * 31);
        List<byte[]> frames = sender.fragment(47999, payload);
        assertEquals(3, frames.size());
        assertNull(receiver.accept(frames.get(2), 20));
        assertNull(receiver.accept(frames.get(0), 20));
        SyzygyQuicDatagramCodec.Packet packet = receiver.accept(frames.get(1), 20);
        assertNotNull(packet);
        assertEquals(47999, packet.port);
        assertArrayEquals(payload, packet.payload);
    }

    @Test public void expiresIncompletePackets() {
        SyzygyQuicDatagramCodec codec = new SyzygyQuicDatagramCodec();
        byte[] payload = new byte[1_500];
        List<byte[]> frames = codec.fragment(48000, payload);
        assertNull(codec.accept(frames.get(0), 100));
        assertEquals(1, codec.getPendingPacketCount());
        assertNull(codec.accept(new byte[0], 201));
        assertEquals(0, codec.getPendingPacketCount());
        assertEquals(0, codec.getPendingByteCount());
    }

    @Test public void rejectsMalformedHeaders() {
        SyzygyQuicDatagramCodec codec = new SyzygyQuicDatagramCodec();
        assertNull(codec.accept(new byte[9], 0));
        byte[] invalidCount = new byte[10];
        invalidCount[8] = 0;
        invalidCount[9] = 67;
        assertNull(codec.accept(invalidCount, 0));
    }


    @Test public void decodesUnsignedHeaderFieldsWithoutApi26Helpers() {
        SyzygyQuicDatagramCodec codec = new SyzygyQuicDatagramCodec();
        byte[] frame = ByteBuffer.allocate(11).order(ByteOrder.BIG_ENDIAN)
                .putShort((short) 0xffff)
                .putInt(0xffffffff)
                .putShort((short) 0)
                .putShort((short) 1)
                .put((byte) 0x5a)
                .array();

        SyzygyQuicDatagramCodec.Packet packet = codec.accept(frame, 0);
        assertNotNull(packet);
        assertEquals(65535, packet.port);
        assertArrayEquals(new byte[] {(byte) 0x5a}, packet.payload);
    }

    @Test public void emptyDatagramIsRepresentedByOneHeaderOnlyFragment() {
        SyzygyQuicDatagramCodec sender = new SyzygyQuicDatagramCodec();
        SyzygyQuicDatagramCodec receiver = new SyzygyQuicDatagramCodec();
        SyzygyQuicDatagramCodec.Packet packet = receiver.accept(sender.fragment(1, new byte[0]).get(0), 0);
        assertNotNull(packet);
        assertEquals(1, packet.port);
        assertEquals(0, packet.payload.length);
    }
}
