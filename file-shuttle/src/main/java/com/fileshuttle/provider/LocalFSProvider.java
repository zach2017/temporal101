package com.fileshuttle.provider;

import com.fileshuttle.model.TransferRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Provider for local and NFS-mounted filesystems.
 *
 * STREAMING STRATEGY:
 *   BufferedInputStream/BufferedOutputStream wrapping NIO Files streams.
 *   Buffer size configurable via TransferRequest.bufferSize() (default 64 KB).
 *
 * MEMORY PROFILE:
 *   Heap cost: exactly 1x buffer-size per open stream direction.
 *   No file-size limit — works on multi-GB files.
 *   NFS mounts appear as regular paths; the kernel handles network I/O.
 *
 * WHY NOT FileChannel.transferTo?
 *   transferTo is optimal for LOCAL→LOCAL (kernel zero-copy via sendfile(2)),
 *   but requires both endpoints to be file-backed channels. Our orchestrator
 *   pipes InputStream→OutputStream across heterogeneous backends, so buffered
 *   streams are universally composable.
 */
public class LocalFSProvider implements StorageProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalFSProvider.class);

    @Override
    public InputStream openInputStream(TransferRequest request) throws IOException {
        Path path = Path.of(request.inputLocation());
        if (!Files.exists(path))
            throw new FileNotFoundException("Source file not found: " + path);
        log.info("Opening local read: {} ({}bytes)", path, Files.size(path));
        return new BufferedInputStream(
                Files.newInputStream(path, StandardOpenOption.READ),
                request.bufferSize());
    }

    @Override
    public OutputStream openOutputStream(TransferRequest request) throws IOException {
        Path path = Path.of(request.outputLocation());
        Files.createDirectories(path.getParent());
        log.info("Opening local write: {}", path);
        return new BufferedOutputStream(
                Files.newOutputStream(path,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE),
                request.bufferSize());
    }

    @Override
    public long contentLength(TransferRequest request) throws IOException {
        Path path = Path.of(request.inputLocation());
        return Files.exists(path) ? Files.size(path) : -1L;
    }

    @Override
    public String name() { return "LocalFS"; }
}
