package itt.tcl.download;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class DownloadProgressTracker {
    private static final long PUBLISH_INTERVAL_NANOS = 80_000_000L;
    private static final long SPEED_INTERVAL_NANOS = 250_000_000L;
    private static final DownloadProgressTracker INSTANCE =
            new DownloadProgressTracker();

    public enum State {
        IDLE,
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    public record Snapshot(
            State state,
            String status,
            String fileName,
            long downloadedBytes,
            long totalBytes,
            double bytesPerSecond
    ) {
        public boolean active() {
            return state == State.RUNNING;
        }

        public double progress() {
            if (totalBytes <= 0L) {
                return -1.0;
            }
            return Math.max(
                    0.0,
                    Math.min(1.0, (double) downloadedBytes / totalBytes)
            );
        }
    }

    private final CopyOnWriteArrayList<Consumer<Snapshot>> listeners =
            new CopyOnWriteArrayList<>();
    private final AtomicLong transferSequence = new AtomicLong();
    private final Map<Long, Transfer> transfers = new HashMap<>();

    private volatile Snapshot snapshot = new Snapshot(
            State.IDLE,
            "",
            "",
            0L,
            -1L,
            0.0
    );

    private State state = State.IDLE;
    private String status = "";
    private String currentFile = "";
    private long completedBytes;
    private long completedTotal;
    private long networkBytes;
    private long speedSampleBytes;
    private long speedSampleNanos;
    private long lastPublishNanos;
    private double bytesPerSecond;

    private DownloadProgressTracker() {}

    public static DownloadProgressTracker getInstance() {
        return INSTANCE;
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public void addListener(Consumer<Snapshot> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<Snapshot> listener) {
        listeners.remove(listener);
    }

    public synchronized boolean beginOperation(String initialStatus) {
        if (state == State.RUNNING) {
            return false;
        }
        state = State.RUNNING;
        status = safe(initialStatus);
        currentFile = "";
        completedBytes = 0L;
        completedTotal = 0L;
        networkBytes = 0L;
        speedSampleBytes = 0L;
        speedSampleNanos = System.nanoTime();
        lastPublishNanos = 0L;
        bytesPerSecond = 0.0;
        transfers.clear();
        publish(true);
        return true;
    }

    public synchronized void updateStatus(String value) {
        if (state != State.RUNNING) {
            return;
        }
        status = safe(value);
        publish(true);
    }

    public synchronized long beginTransfer(String fileName, long totalBytes) {
        if (state != State.RUNNING) {
            return -1L;
        }
        long id = transferSequence.incrementAndGet();
        transfers.put(
                id,
                new Transfer(safe(fileName), Math.max(-1L, totalBytes))
        );
        currentFile = safe(fileName);
        publish(true);
        return id;
    }

    public synchronized void updateTransfer(long id, long downloadedBytes) {
        Transfer transfer = transfers.get(id);
        if (state != State.RUNNING || transfer == null) {
            return;
        }
        long safeBytes = Math.max(0L, downloadedBytes);
        long delta = Math.max(0L, safeBytes - transfer.downloadedBytes);
        transfer.downloadedBytes = safeBytes;
        networkBytes += delta;
        updateSpeed();
        publish(false);
    }

    public synchronized void finishTransfer(long id) {
        Transfer transfer = transfers.remove(id);
        if (transfer == null) {
            return;
        }
        completedBytes += transfer.downloadedBytes;
        completedTotal += transfer.totalBytes > 0L
                ? transfer.totalBytes
                : transfer.downloadedBytes;
        selectCurrentFile();
        if (transfers.isEmpty()) {
            bytesPerSecond = 0.0;
        }
        publish(true);
    }

    public synchronized void discardTransfer(long id) {
        if (transfers.remove(id) == null) {
            return;
        }
        selectCurrentFile();
        if (transfers.isEmpty()) {
            bytesPerSecond = 0.0;
        }
        publish(true);
    }

    public synchronized void completeOperation(String finalStatus) {
        state = State.SUCCEEDED;
        status = safe(finalStatus);
        currentFile = "";
        bytesPerSecond = 0.0;
        transfers.clear();
        publish(true);
    }

    public synchronized void failOperation(String finalStatus) {
        state = State.FAILED;
        status = safe(finalStatus);
        currentFile = "";
        bytesPerSecond = 0.0;
        transfers.clear();
        publish(true);
    }

    private void updateSpeed() {
        long now = System.nanoTime();
        long elapsed = now - speedSampleNanos;
        if (elapsed < SPEED_INTERVAL_NANOS) {
            return;
        }
        long byteDelta = networkBytes - speedSampleBytes;
        bytesPerSecond = elapsed <= 0L
                ? 0.0
                : byteDelta / (elapsed / 1_000_000_000.0);
        speedSampleBytes = networkBytes;
        speedSampleNanos = now;
    }

    private void selectCurrentFile() {
        currentFile = transfers.values().stream()
                .map(transfer -> transfer.fileName)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse("");
    }

    private void publish(boolean force) {
        long now = System.nanoTime();
        if (!force && now - lastPublishNanos < PUBLISH_INTERVAL_NANOS) {
            return;
        }
        lastPublishNanos = now;

        long downloaded = completedBytes;
        long total = completedTotal;
        boolean totalKnown = true;
        for (Transfer transfer : transfers.values()) {
            downloaded += transfer.downloadedBytes;
            if (transfer.totalBytes <= 0L) {
                totalKnown = false;
            } else {
                total += transfer.totalBytes;
            }
        }
        long publishedTotal = totalKnown && total > 0L ? total : -1L;
        snapshot = new Snapshot(
                state,
                status,
                currentFile,
                downloaded,
                publishedTotal,
                bytesPerSecond
        );
        for (Consumer<Snapshot> listener : listeners) {
            listener.accept(snapshot);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class Transfer {
        private final String fileName;
        private final long totalBytes;
        private long downloadedBytes;

        private Transfer(String fileName, long totalBytes) {
            this.fileName = fileName;
            this.totalBytes = totalBytes;
        }
    }
}
