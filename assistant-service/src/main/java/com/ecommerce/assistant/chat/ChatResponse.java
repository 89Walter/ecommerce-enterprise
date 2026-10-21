package com.ecommerce.assistant.chat;

import java.util.List;

/**
 * @param conversationId da rimandare con la domanda successiva per continuare la conversazione
 * @param reply          risposta dell'assistente
 * @param toolsUsed      strumenti usati per rispondere (utile al frontend e per il debug)
 */
public record ChatResponse(String conversationId, String reply, List<String> toolsUsed) {}
