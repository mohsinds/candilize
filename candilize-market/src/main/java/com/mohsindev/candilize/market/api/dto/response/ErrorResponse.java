package com.mohsindev.candilize.market.api.dto.response;

import lombok.Builder;
import lombok.Data;

/** Standard JSON body returned by GlobalExceptionHandler for all API errors. */
@Data
@Builder
public class ErrorResponse {
    private int status;
    private String error;
    private String message;
    private String path;
    private String timestamp;
}
