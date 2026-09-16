package com.billing.simple.billsoft.regression.security;

import com.billing.simple.billsoft.service.UpdateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("regression")
@Tag("unit")
@DisplayName("Update Service Deep Coverage Tests")
class UpdateServiceDeepCoverageTest {

    @Autowired
    private UpdateService updateService;

    @Test
    @DisplayName("Should test update progress tracking and SSE emitter lifecycle")
    void testProgressAndEmitter() {
        SseEmitter emitter = updateService.createProgressEmitter();
        assertThat(emitter).isNotNull();

        updateService.setProgressEmitter(emitter);

        Map<String, Object> progress = updateService.getProgress();
        assertThat(progress).containsKey("status");
        assertThat(progress).containsKey("percent");

        Map<String, Object> check = updateService.checkForUpdateComplete();
        assertThat(check).containsKey("justUpdated");
    }
}
