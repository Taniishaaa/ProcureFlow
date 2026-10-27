package com.procureflow.common;

import java.time.Instant;
import java.util.Map;

/** Consistent error body returned by every failing request. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {
}
