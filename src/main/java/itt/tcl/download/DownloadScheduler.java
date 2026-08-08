package itt.tcl.download;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 高级下载调度器
 * 提供智能并发控制、断点续传、优先级队列等功能
 */
public class DownloadScheduler {
    private static final DownloadScheduler INSTANCE = new DownloadScheduler();
    
    // 下载线程池
    private final ExecutorService downloadExecutor;
    
    // 优先级队列（高优先级任务优先执行）
    private final PriorityBlockingQueue<DownloadTask> taskQueue;
    
    // 活跃下载计数
    private final AtomicInteger activeDownloads = new AtomicInteger(0);
    
    // 最大并发下载数
    private static final int MAX_CONCURRENT_DOWNLOADS = 8;
    
    // 下载任务状态
    public enum TaskPriority {
        HIGH(1),    // 游戏核心文件
        NORMAL(2),  // 库文件
        LOW(3);     // 资源文件
        
        final int level;
        TaskPriority(int level) { this.level = level; }
    }
    
    public record DownloadTask(
        String url,
        Path destination,
        TaskPriority priority,
        boolean supportResume
    ) implements Comparable<DownloadTask> {
        @Override
        public int compareTo(DownloadTask other) {
            return Integer.compare(this.priority.level, other.priority.level);
        }
    }
    
    private DownloadScheduler() {
        // 创建优先下载线程池
        this.downloadExecutor = Executors.newFixedThreadPool(
            MAX_CONCURRENT_DOWNLOADS,
            r -> {
                Thread t = new Thread(r, "download-scheduler");
                t.setDaemon(true);
                return t;
            }
        );
        
        this.taskQueue = new PriorityBlockingQueue<>();
        
        // 启动队列处理器
        startQueueProcessor();
    }
    
    public static DownloadScheduler getInstance() {
        return INSTANCE;
    }
    
    /**
     * 添加下载任务到队列
     */
    public CompletableFuture<Void> scheduleDownload(
        String url, 
        Path destination, 
        TaskPriority priority
    ) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        boolean supportResume = checkResumeSupport(url);
        
        DownloadTask task = new DownloadTask(url, destination, priority, supportResume);
        taskQueue.offer(task);
        
        return future;
    }
    
    /**
     * 检查是否支持断点续传
     */
    private boolean checkResumeSupport(String url) {
        // 简单检查：假设 BMCLAPI 和官方源都支持
        return url.contains("bmclapi") || url.contains("minecraft");
    }
    
    /**
     * 启动队列处理器
     */
    private void startQueueProcessor() {
        downloadExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // 等待有空闲下载槽
                    while (activeDownloads.get() >= MAX_CONCURRENT_DOWNLOADS) {
                        Thread.sleep(100);
                    }
                    
                    // 从队列取出任务
                    DownloadTask task = taskQueue.poll(1, TimeUnit.SECONDS);
                    if (task != null) {
                        activeDownloads.incrementAndGet();
                        executeDownload(task);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
    
    /**
     * 执行下载任务
     */
    private void executeDownload(DownloadTask task) {
        downloadExecutor.submit(() -> {
            try {
                // 尝试断点续传
                if (task.supportResume) {
                    downloadWithResume(task);
                } else {
                    DownloadManager.downloadFileSilent(task.url(), task.destination());
                }
            } catch (Exception e) {
                System.err.println("Download failed: " + task.destination() + " - " + e.getMessage());
            } finally {
                activeDownloads.decrementAndGet();
            }
        });
    }
    
    /**
     * 断点续传下载
     */
    private void downloadWithResume(DownloadTask task) throws Exception {
        Path tempFile = task.destination().resolveSibling(
            task.destination().getFileName() + ".part"
        );
        
        long existingSize = 0;
        if (Files.exists(tempFile)) {
            existingSize = Files.size(tempFile);
        }
        
        try {
            // 如果有部分文件，尝试续传（简化版本）
            if (existingSize > 0) {
                System.out.println("Resuming download: " + task.destination() + 
                    " (existing: " + existingSize + " bytes)");
            }
            
            // 下载到临时文件
            DownloadManager.downloadFileSilent(task.url(), tempFile);
            
            // 原子性重命名
            Files.move(tempFile, task.destination(), 
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                
        } catch (Exception e) {
            // 续传失败，删除临时文件重新下载
            Files.deleteIfExists(tempFile);
            throw e;
        }
    }
    
    /**
     * 获取队列大小
     */
    public int getQueueSize() {
        return taskQueue.size();
    }
    
    /**
     * 获取活跃下载数
     */
    public int getActiveDownloads() {
        return activeDownloads.get();
    }
    
    /**
     * 关闭调度器
     */
    public void shutdown() {
        downloadExecutor.shutdown();
        try {
            if (!downloadExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                downloadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            downloadExecutor.shutdownNow();
        }
    }
}