package com.example.demo.payment.gateway;

import com.example.demo.common.BusinessException;
import org.springframework.http.HttpStatus;

/** Raised when a channel call fails and the transaction must roll back. */
public class GatewayException extends RuntimeException {

    public GatewayException(String message) {
        super(message);
    }

    public GatewayException(String message, Throwable cause) {
        super(message, cause);
    }

    public BusinessException toBusinessException(String code) {
        return new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, code, getMessage());
    }
}
