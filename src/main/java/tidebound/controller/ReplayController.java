package tidebound.controller;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import tidebound.service.ReplayJobService;
import tidebound.service.ReplayJobService.JobStatus;
import tidebound.service.ReplayJobService.JobType;
import tidebound.service.ReplayJobService.ReplayJobSnapshot;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(path = "/replay/jobs", produces = MediaType.APPLICATION_JSON_VALUE)
public class ReplayController {

    private final ReplayJobService replayJobService;

    public ReplayController(ReplayJobService replayJobService) {
        this.replayJobService = replayJobService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReplayJobResponse> enqueueReplay(@RequestBody ReplayJobRequest request) {
        boolean hasReplayUrl = StringUtils.hasText(request.replayUrl());
        boolean hasFilePath = StringUtils.hasText(request.filePath());

        if (hasReplayUrl == hasFilePath) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Exactly one of replay_url or file_path must be provided.");
        }

        ReplayJobSnapshot snapshot = hasReplayUrl
                ? replayJobService.submitRemoteJob(request.replayUrl())
                : replayJobService.submitLocalJob(request.filePath());

        return ResponseEntity
                .accepted()
                .body(ReplayJobResponse.from(snapshot));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ReplayJobResponse> getJob(@PathVariable("jobId") UUID jobId) {
        return replayJobService
                .findJob(jobId)
                .map(ReplayJobResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record ReplayJobRequest(
            @JsonProperty("replay_url") String replayUrl,
            @JsonProperty("file_path") String filePath) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReplayJobResponse(
            @JsonProperty("job_id") UUID jobId,
            @JsonProperty("type") JobType type,
            @JsonProperty("status") JobStatus status,
            @JsonProperty("source") String source,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("parser_status") Integer parserStatus,
            @JsonProperty("error") String error,
            @JsonProperty("result") String result) {

        public static ReplayJobResponse from(ReplayJobSnapshot snapshot) {
            String result = null;
            if (snapshot.status() == JobStatus.SUCCEEDED && snapshot.result() != null) {
                result = new String(snapshot.result(), StandardCharsets.UTF_8);
            }
            return new ReplayJobResponse(
                    snapshot.id(),
                    snapshot.type(),
                    snapshot.status(),
                    snapshot.source(),
                    snapshot.createdAt(),
                    snapshot.updatedAt(),
                    snapshot.parserStatus(),
                    snapshot.errorMessage(),
                    result);
        }
    }
}