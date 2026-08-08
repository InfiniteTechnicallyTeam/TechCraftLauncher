package itt.tcl.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能监控器
 * 提供详细的性能分析和统计功能
 */
public final class PerformanceMonitor {
    private static final PerformanceMonitor INSTANCE = new PerformanceMonitor();
    
    // 操作计时统计
    private final Map<String, OperationStats> operationStats = new ConcurrentHashMap<>();
    
    // 内存使用统计
    private final AtomicLong totalMemoryUsed = new AtomicLong(0);
    private final AtomicLong peakMemoryUsed = new AtomicLong(0);
    
    private PerformanceMonitor() {}
    
    public static PerformanceMonitor getInstance() {
        return INSTANCE;
    }
    
    /**
     * 记录操作开始时间
     */
    public long startOperation(String operationName) {
        return System.nanoTime();
    }
    
    /**
     * 记录操作结束时间
     */
    public void endOperation(String operationName, long startTime) {
        long duration = System.nanoTime() - startTime;
        operationStats.computeIfAbsent(operationName, k -> new OperationStats())
            .record(duration);
    }
    
    /**
     * 记录内存使用
     */
    public void recordMemoryUsage(long bytes) {
        totalMemoryUsed.addAndGet(bytes);
        long current = totalMemoryUsed.get();
        // 更新峰值
        peakMemoryUsed.updateAndGet(peak -> Math.max(peak, current));
    }
    
    /**
     * 获取操作统计
     */
    public OperationStats getOperationStats(String operationName) {
        return operationStats.get(operationName);
    }
    
    /**
     * 获取所有操作统计
     */
    public Map<String, OperationStats> getAllOperationStats() {
        return Map.copyOf(operationStats);
    }
    
    /**
     * 获取内存使用情况
     */
    public MemoryUsage getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        return new MemoryUsage(
            usedMemory,
            totalMemory,
            maxMemory,
            peakMemoryUsed.get()
        );
    }
    
    /**
     * 清空所有统计
     */
    public void reset() {
        operationStats.clear();
        totalMemoryUsed.set(0);
        peakMemoryUsed.set(0);
    }
    
    /**
     * 打印性能报告
     */
    public void printReport() {
        System.out.println("\n=== Performance Monitor Report ===");
        System.out.println("Memory Usage: " + getMemoryUsage());
        System.out.println("\nOperation Statistics:");
        
        operationStats.entrySet().stream()
            .sorted(Map.Entry.<String, OperationStats>comparingByValue(
                (a, b) -> Long.compare(b.getTotalTime(), a.getTotalTime())
            ))
            .forEach(entry -> {
                System.out.printf("  %s: %s%n", entry.getKey(), entry.getValue());
            });
    }
    
    // 操作统计数据类
    public static class OperationStats {
        private final AtomicLong totalTime = new AtomicLong(0);
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong minTime = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxTime = new AtomicLong(0);
        
        public void record(long durationNanos) {
            totalTime.addAndGet(durationNanos);
            count.incrementAndGet();
            
            // 更新最小值
            minTime.updateAndGet(current -> Math.min(current, durationNanos));
            // 更新最大值
            maxTime.updateAndGet(current -> Math.max(current, durationNanos));
        }
        
        public long getTotalTime() {
            return totalTime.get();
        }
        
        public long getCount() {
            return count.get();
        }
        
        public double getAverageTime() {
            long c = count.get();
            return c == 0 ? 0 : (double) totalTime.get() / c;
        }
        
        public long getMinTime() {
            return minTime.get();
        }
        
        public long getMaxTime() {
            return maxTime.get();
        }
        
        @Override
        public String toString() {
            return String.format(
                "count=%d, avg=%.2fms, min=%.2fms, max=%.2fms, total=%.2fs",
                getCount(),
                getAverageTime() / 1_000_000.0,
                getMinTime() / 1_000_000.0,
                getMaxTime() / 1_000_000.0,
                getTotalTime() / 1_000_000_000.0
            );
        }
    }
    
    // 内存使用数据类
    public record MemoryUsage(
        long usedMemory,
        long totalMemory,
        long maxMemory,
        long peakMemoryUsed
    ) {
        @Override
        public String toString() {
            return String.format(
                "Used: %d MB, Total: %d MB, Max: %d MB, Peak: %d MB",
                usedMemory / 1024 / 1024,
                totalMemory / 1024 / 1024,
                maxMemory / 1024 / 1024,
                peakMemoryUsed / 1024 / 1024
            );
        }
    }
}