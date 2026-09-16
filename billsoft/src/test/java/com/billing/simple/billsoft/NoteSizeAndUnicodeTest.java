package com.billing.simple.billsoft;

import com.billing.simple.billsoft.entities.Note;
import com.billing.simple.billsoft.repo.NoteRepository;
import com.billing.simple.billsoft.service.NoteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class NoteSizeAndUnicodeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NoteService noteService;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        noteRepository.deleteAll();
    }

    private String generateString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('a' + (i % 26)));
        }
        return sb.toString();
    }

    @Test
    void testContentLimitsSuccessCases() throws Exception {
        int[] sizes = {100, 1000, 4000, 4001, 10000, 20183, 49999, 50000};
        for (int size : sizes) {
            String content = generateString(size);
            assertEquals(size, content.length());

            Note note = Note.builder()
                    .title("Test Note " + size)
                    .content(content)
                    .tags("test,boundary")
                    .firmId(1L)
                    .build();

            // 1. Service Level persistence test
            Note saved = noteService.create(note);
            assertNotNull(saved.getId());
            assertEquals(size, saved.getContent().length());

            // 2. Direct Repository retrieval to ensure H2 CLOB roundtrip
            Note retrieved = noteRepository.findById(saved.getId()).orElseThrow();
            assertEquals(content, retrieved.getContent(), "Content for size " + size + " must match exactly");

            // 3. Controller API test
            Note apiNote = Note.builder()
                    .title("API Note " + size)
                    .content(content)
                    .tags("api,test")
                    .firmId(1L)
                    .build();

            mockMvc.perform(post("/api/notes")
                            .header("X-Firm-Id", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(apiNote)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.title").value("API Note " + size));
        }
    }

    @Test
    void testContentLimitExceeded50001ThrowsBadRequest() throws Exception {
        String content50001 = generateString(50001);
        assertEquals(50001, content50001.length());

        Note note = Note.builder()
                .title("Oversized Note")
                .content(content50001)
                .firmId(1L)
                .build();

        // 1. Service level validation
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> noteService.create(note));
        assertEquals("Note content cannot exceed 50,000 characters.", ex.getMessage());

        // 2. API level HTTP 400 with clean error JSON
        mockMvc.perform(post("/api/notes")
                        .header("X-Firm-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(note)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Note content cannot exceed 50,000 characters."));
    }

    @Test
    void testTitleLimits() throws Exception {
        // 200 chars -> Success
        String title200 = generateString(200);
        Note note200 = Note.builder().title(title200).content("Valid content").firmId(1L).build();
        Note saved200 = noteService.create(note200);
        assertNotNull(saved200.getId());
        assertEquals(title200, saved200.getTitle());

        // 201 chars -> HTTP 400
        String title201 = generateString(201);
        Note note201 = Note.builder().title(title201).content("Valid content").firmId(1L).build();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> noteService.create(note201));
        assertEquals("Note title cannot exceed 200 characters.", ex.getMessage());

        mockMvc.perform(post("/api/notes")
                        .header("X-Firm-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(note201)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Note title cannot exceed 200 characters."));

        // Blank title -> HTTP 400
        Note blankTitleNote = Note.builder().title("   ").content("Valid content").firmId(1L).build();
        mockMvc.perform(post("/api/notes")
                        .header("X-Firm-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankTitleNote)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Note title is required."));
    }

    @Test
    void testTagsLimits() throws Exception {
        // 1000 chars -> Success
        String tags1000 = generateString(1000);
        Note note1000 = Note.builder().title("Tags 1000").content("Valid content").tags(tags1000).firmId(1L).build();
        Note saved1000 = noteService.create(note1000);
        assertNotNull(saved1000.getId());
        assertEquals(tags1000, saved1000.getTags());

        // 1001 chars -> HTTP 400
        String tags1001 = generateString(1001);
        Note note1001 = Note.builder().title("Tags 1001").content("Valid content").tags(tags1001).firmId(1L).build();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> noteService.create(note1001));
        assertEquals("Note tags cannot exceed 1,000 characters.", ex.getMessage());

        mockMvc.perform(post("/api/notes")
                        .header("X-Firm-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(note1001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Note tags cannot exceed 1,000 characters."));
    }

    @Test
    void testUnicodeHindiEmojiMultilineAndRoundtrip() throws Exception {
        String unicodeContent = "नमस्ते दुनिया! यह एक विस्तृत व्यक्तिगत नोट है।\n"
                + "Line 2: 💰 🚀 📝 ✨ 🎯 💸\n"
                + "Line 3: RupeeCRM supports long Hindi text & emojis flawlessly.\n"
                + "Special Chars: <script>alert('test')</script> & \"quotes\" and 'single' and \t tabs.\n"
                + "Paragraph 2:\n"
                + "व्यापार प्रबंधन और लेखांकन प्रणाली में नोट्स का महत्व।\n".repeat(20);

        Note note = Note.builder()
                .title("हिंदी और इमोजी नोट्स 🚀")
                .content(unicodeContent)
                .tags("हिंदी,emoji,multiline")
                .firmId(1L)
                .build();

        Note saved = noteService.create(note);
        assertNotNull(saved.getId());

        Note retrieved = noteRepository.findById(saved.getId()).orElseThrow();
        assertEquals(unicodeContent, retrieved.getContent());
        assertEquals("हिंदी और इमोजी नोट्स 🚀", retrieved.getTitle());
        assertEquals("हिंदी,emoji,multiline", retrieved.getTags());
    }

    @Test
    void testUpdateNoteBoundariesAndIntegrity() throws Exception {
        // Create initial note
        Note initial = Note.builder()
                .title("Original Note")
                .content("Original content")
                .tags("init")
                .firmId(1L)
                .build();
        Note created = noteService.create(initial);
        Long id = created.getId();

        // Update to 20,183 characters -> Success
        String content20183 = generateString(20183);
        Note update20183 = Note.builder().title("Updated 20183").content(content20183).tags("updated").build();
        Note updatedRes = noteService.update(id, update20183);
        assertEquals(20183, updatedRes.getContent().length());

        // Update to 50,000 characters -> Success
        String content50000 = generateString(50000);
        Note update50000 = Note.builder().title("Updated 50000").content(content50000).tags("updated50k").build();
        Note updatedRes50k = noteService.update(id, update50000);
        assertEquals(50000, updatedRes50k.getContent().length());

        // Update to 50,001 characters -> HTTP 400 and existing note remains intact
        String content50001 = generateString(50001);
        Note update50001 = Note.builder().title("Updated 50001").content(content50001).build();

        mockMvc.perform(put("/api/notes/" + id)
                        .header("X-Firm-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update50001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Note content cannot exceed 50,000 characters."));

        // Verify note in DB is still the 50,000 char version
        Note inDb = noteRepository.findById(id).orElseThrow();
        assertEquals(50000, inDb.getContent().length());
        assertEquals("Updated 50000", inDb.getTitle());
    }
}
