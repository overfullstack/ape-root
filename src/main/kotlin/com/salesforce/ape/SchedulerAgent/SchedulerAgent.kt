package com.salesforce.ape.SchedulerAgent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser
import com.salesforce.ape.SchedulerAgent.SalesforceLLM

class SchedulerAgent {

    val toolRegistry = ToolRegistry {
        tools(SchedulerAgentTools.CustomTools())
    }

    fun simpleAgent(): AIAgent<String, String> {
        val agent = AIAgent(
            promptExecutor = SalesforceLLM.getLlmExecutor(),
            llmModel = SalesforceLLM.getModel(),
            toolRegistry = toolRegistry,
            systemPrompt = "You help the user in booking service appointments.",
            strategy = SchedulerAgentStrategy.schedulerAIAgentStrategy
        )

        return agent;
    }
}

suspend fun main() {

    val agent = SchedulerAgent()
    val ans = agent.simpleAgent().run("")

    println(ans)

}

