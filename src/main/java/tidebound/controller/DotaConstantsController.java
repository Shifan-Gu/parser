package tidebound.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tidebound.service.DotaConstantsIngestionService;

@RestController
@RequestMapping("/dota-constants")
public class DotaConstantsController {
    private final DotaConstantsIngestionService ingestionService;

    public DotaConstantsController(DotaConstantsIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @GetMapping("/sync")
    public ResponseEntity<Map<String, Integer>> syncConstants() {
        Map<String, Integer> results = ingestionService.syncConstants();
        return ResponseEntity.ok(results);
    }
}


