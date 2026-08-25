package com.graphqlguy.schemanav.agent;

import com.graphqlguy.schemanav.config.SchemaNavProperties;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;

import java.util.ArrayList;
import java.util.List;

/**
 * The agent loop, run by hand on purpose. Frameworks normally execute tool calls
 * invisibly inside one call() and hand back only the final answer; here every turn is
 * explicit so a reader can watch the mechanism: the model receives the conversation,
 * either answers or asks for tools, the tools run and their results are appended to
 * the conversation, and the loop repeats. Because the WHOLE conversation is resent
 * on every turn, each tool result is paid for again on every later call; that
 * compounding is why the size of what tools return matters so much, and the ledger
 * makes it visible per turn.
 */
public class AgentRunner {

    private static final String SYSTEM = """
            You are an assistant working against a GraphQL schema you have never seen.
            The one hard rule: every type, field, and input field you write MUST have
            appeared in a tool result first. Never invent names. Work in this order:
            1. searchSchema with the user's question to find candidate field coordinates.
            2. introspectType on every type you will touch: the owner types of the fields
               you select, the return types you select fields from, and EVERY input type
               you pass as an argument. If an argument has an input object type, you must
               introspect that input type before using it.
            3. Write ONE GraphQL operation, selecting only the fields the task needs, and
               check it with executeGraphql. If validation fails, introspect the types
               named in the errors, fix the operation, and check again.
            When the operation validates, answer with the final operation and one short
            paragraph explaining how it satisfies the request.""";

    private final ChatModel chatModel;
    private final String modelName;
    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
    private final SchemaNavProperties properties;

    public AgentRunner(ChatModel chatModel, String modelName, SchemaNavProperties properties) {
        this.chatModel = chatModel;
        this.modelName = modelName;
        this.properties = properties;
    }

    public void run(String task, AgentTools tools, TokenLedger ledger, long fullSchemaTokens) {
        // In Spring AI 2.0 a bare ChatModel.call never executes tools itself (execution
        // moved to the ChatClient's ToolCallingAdvisor), so passing callbacks here only
        // ADVERTISES the tools; running them stays this loop's visible, explicit job.
        // Custom prompt options REPLACE the configured defaults, so the model name must
        // be restated, and OllamaChatModel requires its own options class.
        ToolCallingChatOptions options = OllamaChatOptions.builder()
                .model(modelName)
                .toolCallbacks(ToolCallbacks.from(tools))
                .build();
        List<Message> conversation = new ArrayList<>(List.of(
                new SystemMessage(SYSTEM), new UserMessage(task)));

        String finalAnswer = "(the step limit was reached before a final answer)";
        for (int step = 1; step <= properties.getAgent().getMaxSteps(); step++) {
            Prompt prompt = new Prompt(conversation, options);
            ChatResponse response = chatModel.call(prompt);
            long promptTokens = tokenCount(response, true);
            long completionTokens = tokenCount(response, false);
            ledger.addModelCall(promptTokens, completionTokens);
            System.out.printf("model call %d: %d prompt + %d completion tokens (provider-reported)%n",
                    step, promptTokens, completionTokens);

            if (response.hasToolCalls()) {
                response.getResult().getOutput().getToolCalls().forEach(call ->
                        System.out.println("  model asks for: " + call.name()));
                ToolExecutionResult executed =
                        toolCallingManager.executeToolCalls(prompt, response);
                conversation = new ArrayList<>(executed.conversationHistory());
            } else {
                finalAnswer = response.getResult().getOutput().getText();
                break;
            }
        }

        System.out.println();
        System.out.println("answer:");
        System.out.println(finalAnswer);
        System.out.println();
        System.out.println("context receipt (what this run cost):");
        System.out.printf("  model calls         : %d%n", ledger.modelCalls());
        System.out.printf("  prompt tokens       : %,d (provider-reported; the conversation,"
                + " including every tool result, is resent on each call)%n", ledger.promptTokens());
        System.out.printf("  completion tokens   : %,d (provider-reported model output)%n",
                ledger.completionTokens());
        System.out.printf("  tool payload tokens : %,d (fixed yardstick; what the three tools"
                + " injected into the conversation)%n", ledger.toolPayloadTokens());
        System.out.printf("  for contrast        : handing the model the whole schema instead"
                + " would put %,d tokens into EVERY call%n", fullSchemaTokens);
    }

    private long tokenCount(ChatResponse response, boolean prompt) {
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return 0;
        }
        Number value = prompt ? usage.getPromptTokens() : usage.getCompletionTokens();
        return value == null ? 0 : value.longValue();
    }
}
