package com.ylh.backend.Common;

public class NotFoundException extends BusinessException {
    public NotFoundException(String message) {
        super(404, message);
    }
}
