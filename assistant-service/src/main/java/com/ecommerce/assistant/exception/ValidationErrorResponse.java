package com.ecommerce.assistant.exception;

import java.util.Map;

public record ValidationErrorResponse(String message, Map<String, String> errors) {}
