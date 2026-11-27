package com.billing.simple.billsoft.telegram.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Component
public class TelegramApiClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramApiClient.class);
    private static final String BASE_URL = "https://api.telegram.org/bot";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TelegramApiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public JsonNode getMe(String token) throws IOException, InterruptedException {
        String url = BASE_URL + token + "/getMe";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("Telegram getMe failed with HTTP " + response.statusCode() + ": " + response.body());
        }
        return objectMapper.readTree(response.body());
    }

    public JsonNode deleteWebhook(String token) throws IOException, InterruptedException {
        String url = BASE_URL + token + "/deleteWebhook?drop_pending_updates=false";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        log.info("Telegram deleteWebhook response status: {}, body: {}", response.statusCode(), response.body());
        return objectMapper.readTree(response.body());
    }

    public JsonNode getWebhookInfo(String token) throws IOException, InterruptedException {
        String url = BASE_URL + token + "/getWebhookInfo";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return objectMapper.readTree(response.body());
    }

    public JsonNode getUpdates(String token, Long offset, int timeoutSeconds) throws IOException, InterruptedException {
        StringBuilder urlBuilder = new StringBuilder(BASE_URL).append(token).append("/getUpdates?timeout=").append(timeoutSeconds);
        if (offset != null && offset > 0) {
            urlBuilder.append("&offset=").append(offset);
        }
        urlBuilder.append("&limit=100");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlBuilder.toString()))
                .timeout(Duration.ofSeconds(timeoutSeconds + 15))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 409) {
            String body = response.body();
            if (body != null && body.toLowerCase().contains("webhook")) {
                log.warn("Telegram 409 Conflict indicates active webhook. Attempting automatic deleteWebhook and retry...");
                try {
                    deleteWebhook(token);
                    // Retry once
                    HttpResponse<String> retryResp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (retryResp.statusCode() == 200) {
                        return objectMapper.readTree(retryResp.body());
                    }
                } catch (Exception ex) {
                    log.warn("Failed automatic webhook removal on 409: {}", ex.getMessage());
                }
            }
            throw new TelegramConflictException("Telegram 409 Conflict: another instance is polling getUpdates or webhook is active.");
        }
        if (response.statusCode() != 200) {
            throw new IOException("Telegram getUpdates failed with HTTP " + response.statusCode() + ": " + response.body());
        }
        return objectMapper.readTree(response.body());
    }

    public JsonNode sendMessage(String token, Long chatId, String text, String parseMode, Object replyMarkup) throws IOException, InterruptedException {
        String url = BASE_URL + token + "/sendMessage";
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        if (parseMode != null) {
            body.put("parse_mode", parseMode);
        }
        if (replyMarkup != null) {
            body.put("reply_markup", replyMarkup);
        }

        String jsonBody = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode respNode = objectMapper.readTree(response.body());

        if (response.statusCode() == 400 && parseMode != null && !respNode.path("ok").asBoolean(true)) {
            String desc = respNode.path("description").asText("");
            if (desc.toLowerCase().contains("parse") || desc.toLowerCase().contains("entity")) {
                log.warn("Telegram sendMessage failed with parse_mode '{}' for chat {}: {}. Retrying in plain text.", parseMode, chatId, desc);
                return sendMessage(token, chatId, text, null, replyMarkup);
            }
        }

        if (response.statusCode() != 200) {
            log.warn("Telegram sendMessage returned HTTP {} for chat {}: {}", response.statusCode(), chatId, response.body());
        }
        return respNode;
    }

    public JsonNode editMessageText(String token, Long chatId, Integer messageId, String text, String parseMode, Object replyMarkup) throws IOException, InterruptedException {
        String url = BASE_URL + token + "/editMessageText";
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("text", text);
        if (parseMode != null) {
            body.put("parse_mode", parseMode);
        }
        if (replyMarkup != null) {
            body.put("reply_markup", replyMarkup);
        }

        String jsonBody = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return objectMapper.readTree(response.body());
    }

    public JsonNode answerCallbackQuery(String token, String callbackQueryId, String text) throws IOException, InterruptedException {
        String url = BASE_URL + token + "/answerCallbackQuery";
        Map<String, Object> body = new HashMap<>();
        body.put("callback_query_id", callbackQueryId);
        if (text != null) {
            body.put("text", text);
        }

        String jsonBody = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return objectMapper.readTree(response.body());
    }

    public JsonNode sendDocument(String token, Long chatId, byte[] fileBytes, String filename, String caption) throws IOException, InterruptedException {
        String boundary = "----RupeeCRMTelegramBoundary" + System.currentTimeMillis();
        String url = BASE_URL + token + "/sendDocument";

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // chat_id field
        baos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        baos.write("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        baos.write((chatId.toString() + "\r\n").getBytes(StandardCharsets.UTF_8));

        // caption field
        if (caption != null && !caption.isBlank()) {
            baos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write("Content-Disposition: form-data; name=\"caption\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            baos.write((caption + "\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write("Content-Disposition: form-data; name=\"parse_mode\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            baos.write("Markdown\r\n".getBytes(StandardCharsets.UTF_8));
        }

        // document file field
        baos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Disposition: form-data; name=\"document\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        baos.write("Content-Type: application/pdf\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        baos.write(fileBytes);
        baos.write("\r\n".getBytes(StandardCharsets.UTF_8));

        baos.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        byte[] multipartBody = baos.toByteArray();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("Telegram sendDocument failed with HTTP " + response.statusCode() + ": " + response.body());
        }
        return objectMapper.readTree(response.body());
    }

    public void setMyCommands(String token, List<Map<String, String>> commands) throws IOException, InterruptedException {
        String url = BASE_URL + token + "/setMyCommands";
        Map<String, Object> body = Map.of("commands", commands);
        String jsonBody = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }

    public static class TelegramConflictException extends IOException {
        public TelegramConflictException(String message) {
            super(message);
        }
    }
}
