package meugeninua.screenrecording;

import android.media.MediaCodec;
import android.media.MediaMuxer;

import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

public class CyclicVideoBuffer {

    private final Deque<Data> deque = new LinkedList<>();
    private final long timeLimitUs;
    private long videoStartedAtUs = -1L;
    private long totalTimeUs = 0L;

    public CyclicVideoBuffer(int secondsLimit) {
        this.timeLimitUs = TimeUnit.SECONDS.toMicros(secondsLimit);
    }

    public void add(ByteBuffer buffer, MediaCodec.BufferInfo info) {
        if (info.size == 0) return;
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        deque.addLast(new Data(bytes, info));
        if (videoStartedAtUs < 0) {
            videoStartedAtUs = info.presentationTimeUs;
        }
        totalTimeUs += (info.presentationTimeUs - videoStartedAtUs);
        while (!deque.isEmpty() && totalTimeUs > timeLimitUs) {
            Data removed = deque.removeFirst();
            totalTimeUs -= (removed.info.presentationTimeUs - videoStartedAtUs);
        }
    }

    public void writeTo(MediaMuxer muxer, int trackIndex) {
        for (Data data : deque) {
            muxer.writeSampleData(trackIndex, ByteBuffer.wrap(data.buffer), data.info);
        }
    }

    private static class Data {

        final byte[] buffer;
        final MediaCodec.BufferInfo info;

        public Data(byte[] buffer, MediaCodec.BufferInfo info) {
            this.buffer = buffer;
            this.info = info;
        }
    }
}
