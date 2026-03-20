package com.fileshuttle.provider;

import com.fileshuttle.model.TransferRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Provider for HTTP/HTTPS endpoints (download and upload).
 *
 * INPUT (Download):
 *   HttpClient + BodyHandlers.ofInputStream() → lazy InputStream backed by
 *   the HTTP response body. Full response NEVER buffered in heap.
 *
 * OUTPUT (Upload) — PipedOutputStream architecture:
 *   ┌──────────────┐    ┌─────────────┐    ┌──────────────┐    ┌──────────┐
 *   │ Orchestrator  │───→│ PipedOutput │───→│ PipedInput   │───→│ HTTP PUT │
 *   │ .write(buf)   │    │ Stream      │    │ Stream       │    │ to URL   │
 *   └──────────────┘    └─────────────┘    └──────────────┘    └──────────┘
 *        writes              pipe buffer             HttpClient reads
 *        (caller thread)     (64KB)                  (async/virtual thread)
 *
 * MEMORY PROFILE:
 *   Download: 64 KB (read buffer)
 *   Upload:   64 KB pipe buffer + HTTP internals (~128 KB total)
 */
public class UrlProvider implements StorageProvider {

    private static final Logger log = LoggerFactory.getLogger(UrlProvider.class);
    private final HttpClient httpClient;

    public UrlProvider() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public InputStream openInputStream(TransferRequest request) throws IOException {
        URI uri = URI.create(request.inputLocation());
        log.info("HTTP GET {}", uri);
        try {
            HttpResponse<InputStream> response = httpClient.send(
                    HttpRequest.newBuilder(uri).GET().timeout(Duration.ofMinutes(30)).build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status < 200 || status >= 300)
                throw new IOException("HTTP GET failed: status=" + status + " url=" + uri);
            return new BufferedInputStream(response.body(), request.bufferSize());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP download interrupted", e);
        }
    }

    @Override
    public OutputStream openOutputStream(TransferRequest request) throws IOException {
        URI uri = URI.create(request.outputLocation());
        String method = request.httpMethod();
        log.info("HTTP {} upload → {}", method, uri);

        PipedInputStream pipedIn = new PipedInputStream(request.bufferSize());
        PipedOutputStream pipedOut = new PipedOutputStream(pipedIn);

        HttpRequest.BodyPublisher bodyPublisher =
                HttpRequest.BodyPublishers.ofInputStream(() -> pipedIn);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(60))
                .header("Content-Type", "application/octet-stream");

        HttpRequest httpRequest = switch (method.toUpperCase()) {
            case "POST" -> reqBuilder.POST(bodyPublisher).build();
            default     -> reqBuilder.PUT(bodyPublisher).build();
        };

        // Fire async — reads from pipedIn as orchestrator writes to pipedOut
        var future = httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString());

        // Wrap to check upload result on close()
        return new FilterOutputStream(pipedOut) {
            @Override
            public void close() throws IOException {
                super.close();
                try {
                    HttpResponse<String> resp = future.join();
                    if (resp.statusCode() < 200 || resp.statusCode() >= 300)
                        throw new IOException("HTTP upload failed: status=" +
                                resp.statusCode() + " body=" + resp.body());
                    log.info("HTTP upload complete: status={}", resp.statusCode());
                } catch (java.util.concurrent.CompletionException e) {
                    throw new IOException("HTTP upload failed", e.getCause());
                }
            }
        };
    }

    @Override
    public long contentLength(TransferRequest request) throws IOException {
        try {
            HttpResponse<Void> resp = httpClient.send(
                    HttpRequest.newBuilder(URI.create(request.inputLocation()))
                            .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.discarding());
            return resp.headers().firstValueAsLong("Content-Length").orElse(-1L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1L;
        }
    }

    @Override
    public String name() { return "URL/HTTP"; }
}
