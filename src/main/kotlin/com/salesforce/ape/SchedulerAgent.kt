package com.salesforce.ape

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgent.Companion.invoke
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.ToolRegistry.Companion.invoke
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser
import com.salesforce.ape.CoreUtils.ASSERT_COMPOSITE_GRAPH_RESPONSE_SUCCESS
import com.salesforce.ape.CoreUtils.ASSERT_COMPOSITE_RESPONSE_SUCCESS
import com.salesforce.revoman.ReVoman
import com.salesforce.revoman.input.config.Kick
import com.salesforce.revoman.input.config.StepPick.PostTxnStepPick.PickUtils.afterStepContainingHeader

class SchedulerAgent {

    @LLMDescription("Tools for obtaining the work type group")
    class CustomTools : ToolSet {

        @Tool
        @LLMDescription("Gets a response of the work Type Groups available")
        fun getWorkType(): String {

            val pmCollectionPaths = "scheduler-e2e/Koog_Test.json"
            val pmEnvironmentPaths = listOf("scheduler-e2e/Koog_Test_Env.json")

            val dynamicEnv = mutableMapOf<String, String>()

            val rundown = ReVoman.revUp(
                Kick.configure()
                    .templatePaths(pmCollectionPaths)
                    .dynamicEnvironment(dynamicEnv)
                    .environmentPaths(pmEnvironmentPaths)
                    .haltOnFailureOfTypeExcept(
                        com.salesforce.revoman.output.ExeType.HTTP_STATUS,
                        afterStepContainingHeader(IGNORE_HTTP_STATUS_UNSUCCESSFUL),
                    )
                    .hooks(
                        WAIT_HOOK,
                        ASSERT_COMPOSITE_GRAPH_RESPONSE_SUCCESS,
                        ASSERT_COMPOSITE_RESPONSE_SUCCESS,
                    )
                    .nodeModulesPath("js")
                    .off(),
            )

            val lastStep = rundown.stepReports.last();
            //val saResponse = lastStep.responseInfo?.get()?.httpMsg?.body.toString()
            println()
            println()
            println("HELLO")
            //println(lastStep.responseInfo.responseInfo.httpMessage)
            return lastStep.toString()
        }
    }

    val toolRegistry = ToolRegistry {
        tools(CustomTools())
        tool(SayToUser)
        tool(AskUser)
    }

    fun simpleAgent(): AIAgent<String, String> {
        val agent = AIAgent(
            promptExecutor = SalesforceLLM.getLlmExecutor(),
            llmModel = SalesforceLLM.getModel(),
            toolRegistry = toolRegistry
        )

        return agent;
    }

}

suspend fun main() {

    val agent = SchedulerAgent()
    val ans = agent.simpleAgent().run("Give me the id of a work type group for depositing cash? Answer in less than 5 words and tell the user")

    println(ans)
}

