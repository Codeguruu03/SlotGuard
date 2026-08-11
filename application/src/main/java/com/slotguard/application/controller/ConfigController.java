package com.slotguard.application.controller;

import com.slotguard.application.dto.ApiResponse;
import com.slotguard.application.dto.ConcurrencyModeRequest;
import com.slotguard.application.model.ConcurrencyMode;
import com.slotguard.application.service.ConcurrencyConfigService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config")
@CrossOrigin(origins = "*")
public class ConfigController {

    private final ConcurrencyConfigService configService;

    public ConfigController(ConcurrencyConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/concurrency-mode")
    public ResponseEntity<ApiResponse<ConcurrencyMode>> getConcurrencyMode() {
        ConcurrencyMode mode = configService.getActiveMode();
        return ResponseEntity.ok(ApiResponse.success("Current concurrency mode", mode));
    }

    @PostMapping("/concurrency-mode")
    public ResponseEntity<ApiResponse<ConcurrencyMode>> setConcurrencyMode(@Valid @RequestBody ConcurrencyModeRequest request) {
        configService.setActiveMode(request.getMode());
        return ResponseEntity.ok(ApiResponse.success("Concurrency mode updated to " + request.getMode(), request.getMode()));
    }
}
