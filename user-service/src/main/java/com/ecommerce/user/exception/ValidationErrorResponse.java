package com.ecommerce.user.exception;

import java.util.Map;

public record ValidationErrorResponse(String message, Map<String, String> errors) {}
