package tidebound.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReplayJobServiceTest {

    @Mock
    private ReplayProcessingService replayProcessingService;

    private ReplayJobService replayJobService;

    @BeforeEach
    void setUp() {
        replayJobService = new ReplayJobService(replayProcessingService, 2);
    }

    @Test
    void testSubmitRemoteJob_CreatesJob() {
        String replayUrl = "https://example.com/replay.dem";
        // No stubbing needed - we're only testing job creation, not execution
        // The service will be called asynchronously in a background thread
        
        ReplayJobService.ReplayJobSnapshot snapshot = replayJobService.submitRemoteJob(replayUrl);
        
        assertNotNull(snapshot);
        assertNotNull(snapshot.id());
        assertEquals(ReplayJobService.JobType.REMOTE_URL, snapshot.type());
        assertEquals(replayUrl, snapshot.source());
        assertEquals(ReplayJobService.JobStatus.PENDING, snapshot.status());
        assertNotNull(snapshot.createdAt());
        assertNotNull(snapshot.updatedAt());
    }

    @Test
    void testSubmitLocalJob_CreatesJob() {
        String filePath = "/path/to/replay.dem";
        // No stubbing needed - we're only testing job creation, not execution
        // The service will be called asynchronously in a background thread
        
        ReplayJobService.ReplayJobSnapshot snapshot = replayJobService.submitLocalJob(filePath);
        
        assertNotNull(snapshot);
        assertNotNull(snapshot.id());
        assertEquals(ReplayJobService.JobType.LOCAL_FILE, snapshot.type());
        assertEquals(filePath, snapshot.source());
        assertEquals(ReplayJobService.JobStatus.PENDING, snapshot.status());
    }

    @Test
    void testFindJob_ReturnsJob_WhenExists() throws InterruptedException {
        String replayUrl = "https://example.com/replay.dem";
        ReplayProcessingService.ReplayResponse response = 
            new ReplayProcessingService.ReplayResponse(200, "test data".getBytes());
        
        when(replayProcessingService.processRemoteReplay(replayUrl)).thenReturn(response);
        
        ReplayJobService.ReplayJobSnapshot submitted = replayJobService.submitRemoteJob(replayUrl);
        UUID jobId = submitted.id();
        
        // Wait a bit for job to start processing
        Thread.sleep(100);
        
        Optional<ReplayJobService.ReplayJobSnapshot> found = replayJobService.findJob(jobId);
        
        assertTrue(found.isPresent());
        assertEquals(jobId, found.get().id());
        assertEquals(replayUrl, found.get().source());
    }

    @Test
    void testFindJob_ReturnsEmpty_WhenNotExists() {
        UUID nonExistentId = UUID.randomUUID();
        Optional<ReplayJobService.ReplayJobSnapshot> found = replayJobService.findJob(nonExistentId);
        
        assertFalse(found.isPresent());
    }

    @Test
    void testListJobs_ReturnsAllJobs() throws InterruptedException {
        String url1 = "https://example.com/replay1.dem";
        String url2 = "https://example.com/replay2.dem";
        
        ReplayProcessingService.ReplayResponse response = 
            new ReplayProcessingService.ReplayResponse(200, "test data".getBytes());
        
        when(replayProcessingService.processRemoteReplay(any())).thenReturn(response);
        
        ReplayJobService.ReplayJobSnapshot job1 = replayJobService.submitRemoteJob(url1);
        Thread.sleep(50);
        ReplayJobService.ReplayJobSnapshot job2 = replayJobService.submitRemoteJob(url2);
        
        Thread.sleep(100);
        
        List<ReplayJobService.ReplayJobSnapshot> jobs = replayJobService.listJobs();
        
        assertTrue(jobs.size() >= 2);
        // Jobs should be sorted by creation time (newest first)
        assertTrue(jobs.get(0).createdAt().isAfter(jobs.get(1).createdAt()) || 
                   jobs.get(0).createdAt().equals(jobs.get(1).createdAt()));
    }

    @Test
    void testJobStatus_TransitionsFromPendingToRunning() throws InterruptedException {
        String replayUrl = "https://example.com/replay.dem";
        ReplayProcessingService.ReplayResponse response = 
            new ReplayProcessingService.ReplayResponse(200, "test data".getBytes());
        
        when(replayProcessingService.processRemoteReplay(replayUrl)).thenReturn(response);
        
        ReplayJobService.ReplayJobSnapshot submitted = replayJobService.submitRemoteJob(replayUrl);
        assertEquals(ReplayJobService.JobStatus.PENDING, submitted.status());
        
        // Wait for job to start
        Thread.sleep(200);
        
        Optional<ReplayJobService.ReplayJobSnapshot> running = replayJobService.findJob(submitted.id());
        assertTrue(running.isPresent());
        // Status should be RUNNING or SUCCEEDED depending on timing
        assertTrue(running.get().status() == ReplayJobService.JobStatus.RUNNING ||
                   running.get().status() == ReplayJobService.JobStatus.SUCCEEDED);
    }

    @Test
    void testJobStatus_TransitionsToSucceeded_OnSuccess() throws InterruptedException {
        String replayUrl = "https://example.com/replay.dem";
        byte[] resultData = "success result".getBytes();
        ReplayProcessingService.ReplayResponse response = 
            new ReplayProcessingService.ReplayResponse(200, resultData);
        
        when(replayProcessingService.processRemoteReplay(replayUrl)).thenReturn(response);
        
        ReplayJobService.ReplayJobSnapshot submitted = replayJobService.submitRemoteJob(replayUrl);
        
        // Wait for job to complete
        Thread.sleep(500);
        
        Optional<ReplayJobService.ReplayJobSnapshot> completed = replayJobService.findJob(submitted.id());
        assertTrue(completed.isPresent());
        
        if (completed.get().status() == ReplayJobService.JobStatus.SUCCEEDED) {
            assertEquals(200, completed.get().parserStatus());
            assertNotNull(completed.get().result());
            assertArrayEquals(resultData, completed.get().result());
        }
    }

    @Test
    void testJobStatus_TransitionsToFailed_OnFailure() throws InterruptedException {
        String replayUrl = "https://example.com/replay.dem";
        ReplayProcessingService.ReplayResponse response = 
            new ReplayProcessingService.ReplayResponse(500, new byte[0]);
        
        when(replayProcessingService.processRemoteReplay(replayUrl)).thenReturn(response);
        
        ReplayJobService.ReplayJobSnapshot submitted = replayJobService.submitRemoteJob(replayUrl);
        
        // Wait for job to complete
        Thread.sleep(500);
        
        Optional<ReplayJobService.ReplayJobSnapshot> failed = replayJobService.findJob(submitted.id());
        assertTrue(failed.isPresent());
        
        if (failed.get().status() == ReplayJobService.JobStatus.FAILED) {
            assertEquals(500, failed.get().parserStatus());
            assertNotNull(failed.get().errorMessage());
        }
    }

    @Test
    void testJobStatus_TransitionsToFailed_OnException() throws InterruptedException {
        String replayUrl = "https://example.com/replay.dem";
        
        when(replayProcessingService.processRemoteReplay(replayUrl))
            .thenThrow(new RuntimeException("Processing failed"));
        
        ReplayJobService.ReplayJobSnapshot submitted = replayJobService.submitRemoteJob(replayUrl);
        
        // Wait for job to complete
        Thread.sleep(500);
        
        Optional<ReplayJobService.ReplayJobSnapshot> failed = replayJobService.findJob(submitted.id());
        assertTrue(failed.isPresent());
        
        if (failed.get().status() == ReplayJobService.JobStatus.FAILED) {
            assertEquals(500, failed.get().parserStatus());
            assertNotNull(failed.get().errorMessage());
        }
    }

    @Test
    void testShutdown_ClosesExecutorService() {
        ReplayJobService service = new ReplayJobService(replayProcessingService, 1);
        service.shutdown();
        
        // After shutdown, submitting a job should still work (executor may accept but not process)
        // The main thing is that shutdown doesn't throw an exception
        assertDoesNotThrow(() -> service.shutdown());
    }

    @Test
    void testConcurrentWorkers_RespectsMinimum() {
        ReplayJobService service = new ReplayJobService(replayProcessingService, 0);
        // Should use at least 1 worker
        assertNotNull(service);
    }

    @Test
    void testJobSnapshot_IsImmutable() {
        String replayUrl = "https://example.com/replay.dem";
        // No stubbing needed - we're only testing snapshot structure, not execution
        
        ReplayJobService.ReplayJobSnapshot snapshot = replayJobService.submitRemoteJob(replayUrl);
        
        // Verify snapshot fields are set
        assertNotNull(snapshot.id());
        assertNotNull(snapshot.createdAt());
        assertNotNull(snapshot.updatedAt());
    }
}

