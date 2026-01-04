package com.salesforce.ape.SchedulerAgent

import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

object SalesforceLLM {

    val executorVar = null;

    fun getLlmExecutor(): SingleLLMPromptExecutor {

        val settingsVar = OpenAIClientSettings(
            baseUrl = "https://express-llm-gateway.sfproxy.devx-preprod.aws-esvc1-useast2.aws.sfdc.cl",
            chatCompletionsPath = "/chat/completions"
        )

        //Set the env variable with your API Key
        val apiKeyVar: String = System.getenv("API_KEY") ?: "<Your API Key>"

        val openAiClient = OpenAILLMClient(
            apiKey = apiKeyVar,
            settings = settingsVar
        )

        return SingleLLMPromptExecutor(openAiClient)
    }

    fun getModel(): LLModel {
        val custom: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "gpt-5.2",
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.ToolChoice,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Schema.JSON.Standard,
                LLMCapability.Speculation,
                LLMCapability.Tools,
                LLMCapability.Vision.Image,
                LLMCapability.Document,
                LLMCapability.Completion,
                LLMCapability.MultipleChoices,
                LLMCapability.OpenAIEndpoint.Completions,
                LLMCapability.OpenAIEndpoint.Responses,
            ),
            contextLength = 1,
            maxOutputTokens = 1,
        )

        return custom
    }

}