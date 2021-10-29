package meugeninua.screenrecording;

import android.media.MediaCodec;
import android.media.MediaMuxer;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

public class CyclicVideoBuffer {

    private final Deque<Data> deque = new LinkedList<>();
    private final long timeLimitMs;
    private long prevTimeMs = -1L;
    private long totalTimeMs = 0L;
    private Data firstItem = null;

    public CyclicVideoBuffer(int secondsLimit) {
        this.timeLimitMs = TimeUnit.SECONDS.toMillis(secondsLimit);
        prevTimeMs = System.currentTimeMillis();
    }

    public void add(ByteBuffer buffer, MediaCodec.BufferInfo info) {
        if (info.size == 0) return;
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        Log.d("CyclicVideoBuffer", "prevTimeMs = " + prevTimeMs
            + ", currentTimeMs = " + System.currentTimeMillis());
        Data data = new Data(bytes, info, System.currentTimeMillis() - prevTimeMs);
        if (firstItem == null) {
            firstItem = data;
            return;
        }
        prevTimeMs = System.currentTimeMillis();
        deque.addLast(data);
        totalTimeMs += data.delayTimeMs;
        Log.d("CyclicVideoBuffer", "totalTimeMs = " + totalTimeMs
            + ", delayTimeMs = " + data.delayTimeMs
            + ", timeLimitMs = " + timeLimitMs);
        while (!deque.isEmpty() && totalTimeMs > timeLimitMs) {
            Data removed = deque.removeFirst();
            totalTimeMs -= removed.delayTimeMs;
        }
    }

    public void writeTo(MediaMuxer muxer, int trackIndex) {
        Log.d("CyclicVideoBuffer", "deque.size() = " + deque.size());
        if (firstItem != null) {
            firstItem.writeTo(muxer, trackIndex);
        }
        for (Data data : deque) {
            data.writeTo(muxer, trackIndex);
        }
    }

    private static class Data {

        final byte[] buffer;
        final MediaCodec.BufferInfo info;
        final long delayTimeMs;

        public Data(byte[] buffer, MediaCodec.BufferInfo info, long delayTimeMs) {
            this.buffer = buffer;
            this.info = info;
            this.delayTimeMs = delayTimeMs;
        }

        public void writeTo(MediaMuxer muxer, int trackIndex) {
            muxer.writeSampleData(trackIndex, ByteBuffer.wrap(buffer), info);
        }
    }
}
