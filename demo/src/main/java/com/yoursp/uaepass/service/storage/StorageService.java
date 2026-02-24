package com.yoursp.uaepass.service.storage;

/**
 * Abstraction for file/object storage.
 * Implementations can target local FS, S3, Azure Blob, etc.
 */
public interface StorageService {

    /**
     * Upload data and return the storage key/URL.
     *
     * @param data        raw bytes to store
     * @param key         identifier / path for the object
     * @param contentType MIME type (e.g. "application/pdf")
     * @return the key or URL where the object was stored
     */
    String upload(byte[] data, String key, String contentType);

    /**
     * Download data by key.
     *
     * @param key storage key / path
     * @return raw bytes
     */
    byte[] download(String key);

    /**
     * Delete an object by key.
     *
     * @param key storage key / path
     */
    void delete(String key);
}
