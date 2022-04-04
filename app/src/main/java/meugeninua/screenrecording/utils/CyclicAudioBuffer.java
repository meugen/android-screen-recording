package meugeninua.screenrecording.utils;

import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import meugeninua.screenrecording.recorder.audio.Source;
import meugeninua.screenrecording.recorder.audio.WavHeader;

public class CyclicAudioBuffer {

    private final Deque<Data> deque = new LinkedList<>();
    private final long timeLimitMs;
    private long prevTimeMs;
    private long totalTimeMs = 0L;

    public CyclicAudioBuffer(int secondsLimit) {
        this.timeLimitMs = TimeUnit.SECONDS.toMillis(secondsLimit);
        this.prevTimeMs = System.currentTimeMillis();
    }

    public void addBuffer(byte[] bytes, int count) {
        if (count == 0) return;
        Log.d("CyclicVideoBuffer", "prevTimeMs = " + prevTimeMs
            + ", currentTimeMs = " + System.currentTimeMillis());
        Data data = new Data(bytes, count, System.currentTimeMillis() - prevTimeMs);

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

    public State cloneState() {
        return new State(new ArrayList<>(deque));
    }

    private static class Data {
        final byte[] bytes;
        final int count;
        final long delayTimeMs;

        public Data(byte[] bytes, int count, long delayTimeMs) {
            this.bytes = bytes;
            this.count = count;
            this.delayTimeMs = delayTimeMs;
        }

        public void writeToStream(OutputStream stream) throws IOException {
            stream.write(bytes, 0, count);
        }
    }

    public static class State {

        private final List<Data> dataItems;

        public State(List<Data> dataItems) {
            this.dataItems = dataItems;
        }

        public void writeToWav(Source source, String fileName) {
            try (FileOutputStream outputStream = new FileOutputStream(fileName)) {
                for (Data item : dataItems) {
                    item.writeToStream(outputStream);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            try (RandomAccessFile randomAccessFile = new RandomAccessFile(fileName, "rw")) {
                randomAccessFile.write(new WavHeader(source, randomAccessFile.length()).toBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
