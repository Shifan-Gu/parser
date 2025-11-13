package tidebound.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tidebound.service.ReplayProcessingService.ReplayResponse;

@Service
public class ReplayJobService {

    private static final Logger log = LoggerFactory.getLogger(ReplayJobService.class);

    private final ReplayProcessingService replayProcessingService;
    private final ExecutorService executorService;
    private final ConcurrentMap<UUID, ReplayJob> jobs = new ConcurrentHashMap<>();

    public ReplayJobService(
            ReplayProcessingService replayProcessingService,
            @Value("${replay.jobs.concurrent-workers:2}") int concurrentWorkers) {
        this.replayProcessingService = replayProcessingService;
        int workerCount = Math.max(1, concurrentWorkers);
        this.executorService = Executors.newFixedThreadPool(workerCount, new ReplayJobThreadFactory());
    }

    public ReplayJobSnapshot submitRemoteJob(String replayUrl) {
        return submitJob(JobType.REMOTE_URL, replayUrl, () -> replayProcessingService.processRemoteReplay(replayUrl));
    }

    public ReplayJobSnapshot submitLocalJob(String filePath) {
        return submitJob(JobType.LOCAL_FILE, filePath, () -> replayProcessingService.processLocalReplay(filePath));
    }

    public Optional<ReplayJobSnapshot> findJob(UUID jobId) {
        return Optional.ofNullable(jobs.get(jobId)).map(ReplayJob::snapshot);
    }

    private ReplayJobSnapshot submitJob(JobType type, String source, Supplier<ReplayResponse> taskSupplier) {
        ReplayJob job = new ReplayJob(type, source);
        jobs.put(job.getId(), job);

        Callable<Void> task = () -> {
            job.markRunning();
            try {
                ReplayResponse response = taskSupplier.get();
                if (response.status() == 200) {
                    job.markSucceeded(response.status(), response.body());
                } else {
                    job.markFailed(response.status(), "Replay processing failed with status %d".formatted(response.status()));
                }
            } catch (Exception ex) {
                log.error("Replay job {} failed", job.getId(), ex);
                job.markFailed(500, ex.getMessage());
            }
            return null;
        };

        executorService.submit(task);

        return job.snapshot();
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    public enum JobStatus {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    public enum JobType {
        REMOTE_URL,
        LOCAL_FILE
    }

    public record ReplayJobSnapshot(
            UUID id,
            JobType type,
            String source,
            JobStatus status,
            Instant createdAt,
            Instant updatedAt,
            Integer parserStatus,
            byte[] result,
            String errorMessage) {
    }

    private static final class ReplayJob {

        private final UUID id = UUID.randomUUID();
        private final JobType type;
        private final String source;
        private final Instant createdAt = Instant.now();

        private volatile Instant updatedAt = createdAt;
        private volatile JobStatus status = JobStatus.PENDING;
        private volatile Integer parserStatus;
        private volatile byte[] result;
        private volatile String errorMessage;

        private ReplayJob(JobType type, String source) {
            this.type = type;
            this.source = source;
        }

        private UUID getId() {
            return id;
        }

        private synchronized void markRunning() {
            status = JobStatus.RUNNING;
            updatedAt = Instant.now();
        }

        private synchronized void markSucceeded(int parserStatus, byte[] result) {
            this.status = JobStatus.SUCCEEDED;
            this.parserStatus = parserStatus;
            this.result = result != null ? result.clone() : null;
            this.errorMessage = null;
            this.updatedAt = Instant.now();
        }

        private synchronized void markFailed(int parserStatus, String errorMessage) {
            this.status = JobStatus.FAILED;
            this.parserStatus = parserStatus;
            this.result = null;
            this.errorMessage = errorMessage;
            this.updatedAt = Instant.now();
        }

        private synchronized ReplayJobSnapshot snapshot() {
            byte[] resultCopy = result != null ? result.clone() : null;
            return new ReplayJobSnapshot(
                    id,
                    type,
                    source,
                    status,
                    createdAt,
                    updatedAt,
                    parserStatus,
                    resultCopy,
                    errorMessage);
        }
    }

    private static final class ReplayJobThreadFactory implements ThreadFactory {

        private static final String THREAD_NAME_FORMAT = "replay-job-%d";
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName(THREAD_NAME_FORMAT.formatted(counter.incrementAndGet()));
            thread.setDaemon(true);
            return thread;
        }
    }
}

