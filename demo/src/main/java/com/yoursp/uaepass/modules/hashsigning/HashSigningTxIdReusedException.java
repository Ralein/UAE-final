package com.yoursp.uaepass.modules.hashsigning;

/**
 * Thrown when the Docker SDK returns HTTP 412 Precondition Failed,
 * meaning the txId has already been used. A fresh txId must be generated.
 */
public class HashSigningTxIdReusedException extends RuntimeException {

    public HashSigningTxIdReusedException(String txId) {
        super("Transaction ID already used: " + txId + ". Generate a fresh txId.");
    }
}
