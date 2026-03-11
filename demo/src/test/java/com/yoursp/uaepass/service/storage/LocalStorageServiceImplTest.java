package com.yoursp.uaepass.service.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalStorageServiceImplTest {

    @TempDir
    Path tempDir;

    private LocalStorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        storageService = new LocalStorageServiceImpl(tempDir);
    }

    @Test
    void testUploadAndDownload() {
        byte[] data = "Hello, UAE PASS!".getBytes();
        String key = "test.txt";
        String contentType = "text/plain";

        String uploadedKey = storageService.upload(data, key, contentType);
        assertEquals(key, uploadedKey);

        byte[] downloadedData = storageService.download(key);
        assertArrayEquals(data, downloadedData);
    }

    @Test
    void testUploadWithSubdirectory() {
        byte[] data = "Nested content".getBytes();
        String key = "nested/dir/test.txt";
        String contentType = "text/plain";

        storageService.upload(data, key, contentType);

        byte[] downloadedData = storageService.download(key);
        assertArrayEquals(data, downloadedData);
    }

    @Test
    void testDownloadFileNotFound() {
        String key = "nonexistent.txt";
        RuntimeException exception = assertThrows(RuntimeException.class, () -> storageService.download(key));
        assertTrue(exception.getMessage().contains("File not found"));
    }

    @Test
    void testDelete() {
        byte[] data = "To be deleted".getBytes();
        String key = "delete-me.txt";
        storageService.upload(data, key, "text/plain");

        storageService.delete(key);

        assertThrows(RuntimeException.class, () -> storageService.download(key));
    }

    @Test
    void testDeleteNonExistent() {
        // Should not throw exception
        storageService.delete("never-existed.txt");
    }

    @Test
    void testPathTraversalAttempt() {
        String key = "../outside.txt";
        assertThrows(SecurityException.class, () -> storageService.upload(new byte[0], key, "text/plain"));
        assertThrows(SecurityException.class, () -> storageService.download(key));
        assertThrows(SecurityException.class, () -> storageService.delete(key));
    }
}
