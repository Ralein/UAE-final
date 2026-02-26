package com.yoursp.uaepass.service.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Local filesystem implementation of {@link StorageService}.
 * Active for dev (default) and staging profiles only.
 * Saves files to /tmp/uaepass-storage/.
 */
@Slf4j
@Service
@Profile({ "default", "staging", "mock" })
public class LocalStorageServiceImpl implements StorageService {

    private final Path storageRoot;

    public LocalStorageServiceImpl() {
        this(Paths.get("/tmp/uaepass-storage"));
    }

    public LocalStorageServiceImpl(Path storageRoot) {
        this.storageRoot = storageRoot;
        try {
            Files.createDirectories(this.storageRoot);
            log.info("LocalStorageService initialized at {}", this.storageRoot);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create local storage directory: " + this.storageRoot, e);
        }
    }

    @Override
    public String upload(byte[] data, String key, String contentType) {
        try {
            Path filePath = resolve(key);
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, data);
            log.debug("Uploaded {} ({} bytes, type={})", key, data.length, contentType);
            return key;
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file: " + key, e);
        }
    }

    @Override
    public byte[] download(String key) {
        try {
            Path filePath = resolve(key);
            if (!Files.exists(filePath)) {
                throw new RuntimeException("File not found: " + key);
            }
            log.debug("Downloaded {}", key);
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to download file: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Path filePath = resolve(key);
            boolean deleted = Files.deleteIfExists(filePath);
            log.debug("Deleted {} (existed={})", key, deleted);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + key, e);
        }
    }

    private Path resolve(String key) {
        // Prevent path-traversal attacks
        Path resolved = storageRoot.resolve(key).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new SecurityException("Path traversal attempt detected: " + key);
        }
        return resolved;
    }
}
