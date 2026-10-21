package com.ecommerce.assistant.chat;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.ecommerce.assistant.config.AssistantProperties;
import com.ecommerce.assistant.conversation.ChatTurn;
import com.ecommerce.assistant.conversation.ConversationStore;
import com.ecommerce.assistant.exception.ConversationNotFoundException;
import com.ecommerce.assistant.tools.ShopTools;
import com.ecommerce.assistant.tools.ShopTools.ToolOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Il ciclo dell'agente:
 * <pre>
 * domanda → Claude → vuole uno strumento? → lo eseguiamo con il token dell'utente → risultato a Claude → ...
 *                  → risposta finale     → la salviamo nella conversazione e la restituiamo
 * </pre>
 * Dentro una stessa domanda la cronologia cresce solo in coda (append-only) e la risposta del modello
 * viene rimandata così com'è, ragionamento compreso, come richiesto dall'API.
 */
@Slf4j
@Service
public class AssistantChatService {

    /** Fallback automatico lato server: se il modello rifiuta per un falso positivo, l'API riprova con un altro modello. */
    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final ClaudeClient claudeClient;
    private final ShopTools shopTools;
    private final ConversationStore conversationStore;
    private final AssistantProperties.Ai settings;

    public AssistantChatService(ClaudeClient claudeClient, ShopTools shopTools, ConversationStore conversationStore,
                                AssistantProperties properties) {
        this.claudeClient = claudeClient;
        this.shopTools = shopTools;
        this.conversationStore = conversationStore;
        this.settings = properties.ai();
    }

    public boolean available() {
        return claudeClient.configured();
    }

    public ChatResponse chat(String userId, String bearerToken, ChatRequest request) {
        String conversationId = request.conversationId() == null ? UUID.randomUUID().toString() : request.conversationId();
        List<ChatTurn> history = request.conversationId() == null
                ? List.of()
                : conversationStore.find(userId, conversationId).orElseThrow(ConversationNotFoundException::new);
        ChatTurn question = ChatTurn.user(request.message().trim());

        List<MessageParam> messages = new ArrayList<>();
        history.forEach(turn -> messages.add(toParam(turn)));
        messages.add(toParam(question));

        Set<String> toolsUsed = new LinkedHashSet<>();
        String reply = runAgentLoop(messages, bearerToken, toolsUsed);

        conversationStore.append(userId, conversationId, history, question, ChatTurn.assistant(reply));
        return new ChatResponse(conversationId, reply, List.copyOf(toolsUsed));
    }

    public boolean deleteConversation(String userId, String conversationId) {
        return conversationStore.delete(userId, conversationId);
    }

    private String runAgentLoop(List<MessageParam> messages, String bearerToken, Set<String> toolsUsed) {
        for (int round = 0; round < settings.maxToolRounds(); round++) {
            Message response = claudeClient.send(params(messages));
            StopReason stopReason = response.stopReason().orElse(StopReason.END_TURN);

            if (StopReason.REFUSAL.equals(stopReason)) {
                log.info("Richiesta rifiutata dal modello: {}", response.stopDetails().orElse(null));
                return AssistantPrompt.REFUSAL_REPLY;
            }
            if (!StopReason.TOOL_USE.equals(stopReason)) {
                String text = textOf(response);
                if (StopReason.MAX_TOKENS.equals(stopReason)) {
                    log.warn("Risposta troncata per max_tokens ({}).", settings.maxTokens());
                }
                return text.isBlank() ? AssistantPrompt.TOO_MANY_STEPS_REPLY : text;
            }

            // La risposta del modello torna nella cronologia intatta (blocchi di ragionamento inclusi)...
            messages.add(response.toParam());
            // ...seguita da tutti i risultati degli strumenti in un unico messaggio utente.
            List<ContentBlockParam> results = new ArrayList<>();
            for (ContentBlock block : response.content()) {
                block.toolUse().ifPresent(toolUse -> {
                    toolsUsed.add(toolUse.name());
                    results.add(ContentBlockParam.ofToolResult(execute(toolUse, bearerToken)));
                });
            }
            messages.add(MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(results).build());
        }
        log.warn("Superato il limite di {} giri di strumenti.", settings.maxToolRounds());
        return AssistantPrompt.TOO_MANY_STEPS_REPLY;
    }

    private ToolResultBlockParam execute(ToolUseBlock toolUse, String bearerToken) {
        ToolOutcome outcome = shopTools.execute(toolUse.name(), toolUse._input(), bearerToken);
        log.debug("Strumento {} eseguito (errore: {}).", toolUse.name(), outcome.isError());
        return ToolResultBlockParam.builder()
                .toolUseId(toolUse.id())
                .content(outcome.content())
                .isError(outcome.isError())
                .build();
    }

    private MessageCreateParams params(List<MessageParam> messages) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(settings.model())
                .maxTokens(settings.maxTokens())
                .system(AssistantPrompt.SYSTEM)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(settings.effort())).build())
                // Cache automatica del prefisso stabile (istruzioni, strumenti, cronologia).
                .cacheControl(CacheControlEphemeral.builder().build())
                .messages(messages)
                .putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        shopTools.definitions().forEach(builder::addTool);
        return builder.build();
    }

    private static MessageParam toParam(ChatTurn turn) {
        return MessageParam.builder()
                .role(turn.fromUser() ? MessageParam.Role.USER : MessageParam.Role.ASSISTANT)
                .content(turn.text())
                .build();
    }

    /** Solo i blocchi di testo: il ragionamento ({@code thinking}) non viene mostrato al cliente. */
    private static String textOf(Message response) {
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text().strip())
                .filter(text -> !text.isEmpty())
                .collect(Collectors.joining("\n\n"));
    }
}
