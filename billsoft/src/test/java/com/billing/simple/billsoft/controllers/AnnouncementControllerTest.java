package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.service.AnnouncementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class AnnouncementControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AnnouncementService announcementService;

    @BeforeEach
    void setUp() {
        AnnouncementController controller = new AnnouncementController(announcementService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testGetAnnouncementsSuccess() throws Exception {
        when(announcementService.getAnnouncements(false)).thenReturn(List.of(
                "Welcome to RupeeCRM 2.4",
                "New offline features are now active"
        ));

        mockMvc.perform(get("/api/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("Welcome to RupeeCRM 2.4"))
                .andExpect(jsonPath("$[1]").value("New offline features are now active"));
    }

    @Test
    void testGetAnnouncementsWithForce() throws Exception {
        when(announcementService.getAnnouncements(true)).thenReturn(List.of(
                "Forced fresh announcement"
        ));

        mockMvc.perform(get("/api/announcements?force=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]").value("Forced fresh announcement"));
    }

    @Test
    void testGetAnnouncementsEmpty() throws Exception {
        when(announcementService.getAnnouncements(false)).thenReturn(List.of());

        mockMvc.perform(get("/api/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
