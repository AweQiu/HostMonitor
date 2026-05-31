package com.ylh.backend.Common;

public class ForbiddenException extends BusinessException {
    public ForbiddenException(String message) {
        super(403, message);
    }
}
