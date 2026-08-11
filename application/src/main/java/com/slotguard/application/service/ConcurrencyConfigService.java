package com.slotguard.application.service;

import com.slotguard.application.model.ConcurrencyMode;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class ConcurrencyConfigService {

    private final AtomicReference<ConcurrencyMode> activeMode = new AtomicReference<>(ConcurrencyMode.VULNERABLE);

    public ConcurrencyMode getActiveMode() {
        return activeMode.get();
    }

    public void setActiveMode(ConcurrencyMode mode) {
        this.activeMode.set(mode);
    }
}
