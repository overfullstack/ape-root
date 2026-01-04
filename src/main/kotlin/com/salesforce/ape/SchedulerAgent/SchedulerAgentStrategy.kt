package com.salesforce.ape.SchedulerAgent

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeAppendPrompt
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStructured
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.structure.StructuredResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("Work_Type_Group_Response")
@LLMDescription("Outputs_the_LLM_Response_to_determine_work_type_group")
data class WorkTypeGroupResponse(
    @property:LLMDescription("The Id of the work type Group")
    val Id: String,
    @property:LLMDescription("The name of the work type Group")
    val name: String,
    @property:LLMDescription("Follow Up Question - Optional")
    val followUpQuestion: String
)

object SchedulerAgentStrategy {

    val schedulerAIAgentStrategy = strategy("Create a service appointment.") {

        val resetMemoryNode by node<String, String> { input ->
            llm.writeSession {
                // Wipes all previous messages from the history
                prompt = prompt("") {
                }
            }
            input
        }

        val workTypeSubgraph by subgraph<String, String>("Work type subgraph", tools = SchedulerAgentTools.CustomTools().asTools()) {

            var workTypeGroupFound = false

            // Define nodes for the strategy
            val nodeStartPrompt by nodeAppendPrompt<String>(name = "Work Type Group Prompt") {
                system(
                    "'Role: You are a specialized Assistant for Work Type Groups.\n" +
                            "\n" +
                            "Task: Analyze the provided criteria and identify the single most relevant Work Type Group ID and its corresponding Name.\n" +
                            "\n" +
                            "Constraints:\n" +
                            "\n" +
                            "Uniqueness: You must output exactly ONE Work Type Group. Do not provide a list or options.\n" +
                            "\n" +
                            "Strict Matching: Only select an ID if it matches the criteria with high confidence.\n" +
                            "\n" +
                            "Fallback: If no existing Work Type Group matches the criteria, the Id should be 'Not Found', and you should ask a " +
                            "follow up for the user in less than 10 words.\n" +
                            "\n" +
                            "Formatting: Output the ID and Name in the following format: [ID] - [Name]."
                )
            }

            val nodeUserInputPrompt by nodeAppendPrompt<String>(name = "Work Type Group User Input Prompt") {
                var question = "Enter the details of the work type group Id you want to book an appointment for -"
                println(question)
                val input = readln()
                system(question)
                user(input)
            }

            val nodeSendInput by nodeLLMRequest()
            val nodeGetLLMResponse by nodeLLMRequestStructured<WorkTypeGroupResponse>(
                name = "response-node",
            )

            val processResult by node<Result<StructuredResponse<WorkTypeGroupResponse>>, String> { result ->
                when {
                    result.isSuccess -> {
                        var workTypeGroup = result.getOrNull()?.component1()
                        println(workTypeGroup)
                        if (workTypeGroup?.Id.equals("Not Found")) {
                            workTypeGroupFound = false
                            "The work type group was not found."
                        } else {
                            workTypeGroupFound = true
                            "The work type group was found. " + workTypeGroup?.Id
                        }
                    }
                    result.isFailure -> {
                        "Failed to get structured forecast: ${result.exceptionOrNull()?.message}"
                    }
                    else -> "Unknown result state"
                }
            }
            val nodeExecuteTool by nodeExecuteTool()
            val nodeSendToolResult by nodeLLMSendToolResult()

            // Define edges between nodes
            // Start -> Send input
            edge(nodeStart forwardTo resetMemoryNode)
            edge(resetMemoryNode forwardTo nodeStartPrompt)
            edge(nodeStartPrompt forwardTo nodeUserInputPrompt)
            edge(nodeUserInputPrompt forwardTo nodeSendInput)

            // Send input -> Finish
            edge(
                (nodeSendInput forwardTo nodeFinish)
                        onAssistantMessage { true }
            )

            // Send input -> Execute tool
            edge(
                (nodeSendInput forwardTo nodeExecuteTool)
                        onToolCall { true }
            )

            // Execute tool -> Send the tool result
            edge(nodeExecuteTool forwardTo nodeSendToolResult)

            // Send the tool result -> finish
            edge(
                (nodeSendToolResult forwardTo nodeGetLLMResponse)
                        onAssistantMessage { true }
            )

            edge(nodeGetLLMResponse forwardTo processResult)
            edge(processResult forwardTo nodeFinish)

            edge(
                (nodeSendToolResult forwardTo nodeExecuteTool)
                        onToolCall { true }
            )
        }

        nodeStart then workTypeSubgraph then nodeFinish
    }
}