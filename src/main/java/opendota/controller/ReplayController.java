package opendota.controller;

import opendota.service.ReplayProcessingService;
import opendota.service.ReplayProcessingService.ReplayResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class ReplayController {

    private final ReplayProcessingService replayProcessingService;

    public ReplayController(ReplayProcessingService replayProcessingService) {
        this.replayProcessingService = replayProcessingService;
    }

    @GetMapping("/blob")
    public ResponseEntity<byte[]> processRemoteReplay(@RequestParam("replay_url") String replayUrl) {
        if (!StringUtils.hasText(replayUrl)) {
            return ResponseEntity.badRequest().build();
        }
        ReplayResponse response = replayProcessingService.processRemoteReplay(replayUrl);
        return ResponseEntity
                .status(response.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.body());
    }

    @GetMapping("/local")
    public ResponseEntity<byte[]> processLocalReplay(@RequestParam("file_path") String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return ResponseEntity.badRequest().build();
        }
        ReplayResponse response = replayProcessingService.processLocalReplay(filePath);
        return ResponseEntity
                .status(response.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.body());
    }
}

