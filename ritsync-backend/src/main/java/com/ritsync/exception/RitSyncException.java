package com.ritsync.exception;

import org.springframework.http.HttpStatus;

public class RitSyncException extends RuntimeException {
    private final HttpStatus status;
    public RitSyncException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
    public HttpStatus getStatus() { return status; }

    public static RitSyncException notFound(String msg)    { return new RitSyncException(msg, HttpStatus.NOT_FOUND); }
    public static RitSyncException badRequest(String msg)  { return new RitSyncException(msg, HttpStatus.BAD_REQUEST); }
    public static RitSyncException forbidden(String msg)   { return new RitSyncException(msg, HttpStatus.FORBIDDEN); }
    public static RitSyncException conflict(String msg)    { return new RitSyncException(msg, HttpStatus.CONFLICT); }
    public static RitSyncException internal(String msg)     { return new RitSyncException(msg, HttpStatus.INTERNAL_SERVER_ERROR); }
}
