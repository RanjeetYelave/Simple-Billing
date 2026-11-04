package com.billing.simple.billsoft.bounce.controllers;

import com.billing.simple.billsoft.bounce.dto.*;
import com.billing.simple.billsoft.bounce.entities.BounceLevel;
import com.billing.simple.billsoft.bounce.entities.BounceLevelRecord;
import com.billing.simple.billsoft.bounce.entities.BounceProgress;
import com.billing.simple.billsoft.bounce.entities.BounceSettings;
import com.billing.simple.billsoft.bounce.services.BouncePersistenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bounce")
public class BounceController {

    private final BouncePersistenceService bounceService;

    public BounceController(BouncePersistenceService bounceService) {
        this.bounceService = bounceService;
    }

    @GetMapping("/state")
    public ResponseEntity<BounceStateResponse> getState() {
        return ResponseEntity.ok(bounceService.loadGameState());
    }

    @PostMapping("/campaign/new")
    public ResponseEntity<BounceStateResponse> newCampaign(@RequestBody(required = false) NewCampaignRequest req) {
        return ResponseEntity.ok(bounceService.startNewCampaign(req));
    }

    @PostMapping("/campaign/reset")
    public ResponseEntity<BounceStateResponse> resetCampaign() {
        return ResponseEntity.ok(bounceService.resetCampaign());
    }

    @PostMapping({"/progress/save", "/progress"})
    public ResponseEntity<BounceProgress> saveProgress(@RequestBody SaveProgressRequest req) {
        return ResponseEntity.ok(bounceService.saveProgress(req));
    }

    @PostMapping({"/level/complete", "/levels/complete"})
    public ResponseEntity<BounceStateResponse> completeLevel(@RequestBody CompleteLevelRequest req) {
        return ResponseEntity.ok(bounceService.completeLevel(req));
    }

    @GetMapping({"/level/{levelNumber}", "/levels/{levelNumber}"})
    public ResponseEntity<BounceLevel> getLevel(@PathVariable int levelNumber) {
        return bounceService.getStoredLevel(levelNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/records")
    public ResponseEntity<List<BounceLevelRecord>> getRecords() {
        return ResponseEntity.ok(bounceService.getRecords());
    }

    @PutMapping("/settings")
    public ResponseEntity<BounceSettings> updateSettings(@RequestBody UpdateSettingsRequest req) {
        return ResponseEntity.ok(bounceService.updateSettings(req));
    }
}
