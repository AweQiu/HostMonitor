package com.ylh.backend.Common;

public class UnauthorizedException extends BusinessException {
    public UnauthorizedException(String message) {
        super(401, message);
    }
}
