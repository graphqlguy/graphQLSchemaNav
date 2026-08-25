package com.graphqlguy.schemanav.agent;

/**
 * The running bill for one agent task. Two different currencies are tracked, and
 * keeping them apart is the point:
 *
 * - prompt/completion tokens are the provider's own usage numbers, reported by the
 *   model after every call. This is what a run actually costs. Prompt tokens grow
 *   each turn because the whole conversation so far (including every tool result)
 *   is sent again on every call; that resending is exactly why context size matters.
 *
 * - tool payload tokens are what the tool results injected into that conversation,
 *   measured with the project's fixed yardstick. This is the part schema navigation
 *   controls: a smaller, better-targeted payload shrinks every subsequent prompt.
 */
public class TokenLedger {

    private int modelCalls;
    private long promptTokens;
    private long completionTokens;
    private long toolPayloadTokens;

    public void addModelCall(long prompt, long completion) {
        modelCalls++;
        promptTokens += prompt;
        completionTokens += completion;
    }

    public void addToolPayload(long tokens) {
        toolPayloadTokens += tokens;
    }

    public int modelCalls() { return modelCalls; }
    public long promptTokens() { return promptTokens; }
    public long completionTokens() { return completionTokens; }
    public long toolPayloadTokens() { return toolPayloadTokens; }
}
