package com.ecommerce.assistant.conversation;

import com.ecommerce.assistant.config.AssistantProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Conversazioni salvate su Redis, lato server.
 * <p>
 * Il frontend manda solo il nuovo messaggio e l'id della conversazione: la cronologia non arriva
 * dal browser, quindi un utente non può inserire finte risposte dell'assistente. La chiave contiene
 * l'id dell'utente (claim "sub"), così nessuno può riprendere la conversazione di un altro.
 * Si salvano solo i testi di domande e risposte (non il ragionamento interno né i risultati degli
 * strumenti): la cronologia resta compatta e si ripete identica a ogni richiesta, a favore della cache.
 */
@Component
public class ConversationStore {

    private static final String KEY_PREFIX = "assistant:conversation:";
    private static final TypeReference<List<ChatTurn>> TURNS = new TypeReference<>() {};

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final AssistantProperties.Conversation settings;

    public ConversationStore(StringRedisTemplate redis, JsonMapper jsonMapper, AssistantProperties properties) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.settings = properties.conversation();
    }

    public Optional<List<ChatTurn>> find(String userId, String conversationId) {
        String json = redis.opsForValue().get(key(userId, conversationId));
        return json == null ? Optional.empty() : Optional.of(jsonMapper.readValue(json, TURNS));
    }

    /** Aggiunge domanda e risposta, tiene solo gli ultimi messaggi e rinnova la scadenza. */
    public void append(String userId, String conversationId, List<ChatTurn> history, ChatTurn question, ChatTurn answer) {
        List<ChatTurn> turns = new ArrayList<>(history);
        turns.add(question);
        turns.add(answer);
        // Si tolgono sempre coppie domanda/risposta intere dall'inizio, così la cronologia parte da una domanda.
        while (turns.size() > settings.maxMessages()) {
            turns.remove(0);
            turns.remove(0);
        }
        redis.opsForValue().set(key(userId, conversationId), jsonMapper.writeValueAsString(turns), settings.ttl());
    }

    public boolean delete(String userId, String conversationId) {
        return Boolean.TRUE.equals(redis.delete(key(userId, conversationId)));
    }

    private static String key(String userId, String conversationId) {
        return KEY_PREFIX + userId + ":" + conversationId;
    }
}
