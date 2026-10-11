package com.ecommerce.payment.exception;

import java.util.Map;

public record ValidationErrorResponse(String message, Map<String, String> errors) {}
