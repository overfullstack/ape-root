package com.salesforce.ape

import com.salesforce.ape.CoreUtils.ASSERT_COMPOSITE_GRAPH_RESPONSE_SUCCESS
import com.salesforce.ape.CoreUtils.ASSERT_COMPOSITE_RESPONSE_SUCCESS
import com.salesforce.revoman.ReVoman
import com.salesforce.revoman.input.config.HookConfig.Companion.post
import com.salesforce.revoman.input.config.Kick
import com.salesforce.revoman.input.config.StepPick.PostTxnStepPick.PickUtils.afterStepContainingHeader
import com.salesforce.revoman.internal.json.MoshiReVoman
import com.salesforce.revoman.output.ExeType.HTTP_STATUS
import io.github.oshai.kotlinlogging.KotlinLogging
import org.http4k.core.PolyHandler
import org.http4k.hotreload.HotReloadServer
import org.http4k.hotreload.HotReloadable
import org.http4k.jsonrpc.ErrorMessage
import org.http4k.lens.csv
import org.http4k.mcp.ToolHandler
import org.http4k.mcp.ToolResponse
import org.http4k.mcp.model.Content
import org.http4k.mcp.model.McpEntity
import org.http4k.mcp.model.Tool
import org.http4k.mcp.protocol.ServerMetaData
import org.http4k.mcp.protocol.ServerProtocolCapability.ToolsChanged
import org.http4k.mcp.protocol.Version
import org.http4k.routing.bind
import org.http4k.routing.mcpHttpStreaming
import org.http4k.server.Helidon

val PM_COLLECTION_PATH =
  listOf(
    "pm-templates/core/milestone/persona-creation.postman_collection.json",
    "pm-templates/core/milestone/tax-setup.postman_collection.json",
    "pm-templates/core/milestone/billing-setup-with-milestone.postman_collection.json",
    "pm-templates/core/milestone/product-setup.postman_collection.json",
    "pm-templates/core/milestone/place-order.postman_collection.json",
    "pm-templates/core/milestone/order-to-billingSchedule.postman_collection.json",
    "pm-templates/core/milestone/invoice-with-recovery.postman_collection.json",
  )
val PM_ENVIRONMENT_PATH = listOf("pm-templates/core/milestone/env.postman_environment.json")

val variableNameArg = Tool.Arg.required("variableName", "variable name to set or create")
val milestoneSplitArg =
  Tool.Arg.csv().optional("milestoneSplit", "Comma seperated milestone percentage split")
val prevVariableNameArg =
  Tool.Arg.required("prevVariableName", "variable name to set in the previous execution")
val prevEnvArg =
  Tool.Arg.required(
    "previousEnvironment",
    "`mutableEnv` JSON property from previous `command_` call",
  )

val queryChainHandler: ToolHandler = { toolRequest ->
  try {
    val pmCollectionPaths = PM_COLLECTION_PATH

    val variableName = variableNameArg(toolRequest)
    logger.info { "`query-chain` called with variableName: $variableName" }
    val chain =
      ReVoman.queryChainForVariable(
        variableName,
        Kick.configure().templatePaths(pmCollectionPaths).off(),
      )
    val variableToPmTemplate = chain.toJson()
    ToolResponse.Ok(listOf(Content.Text(variableToPmTemplate)))
  } catch (e: Exception) {
    ToolResponse.Error(
      ErrorMessage(1, e.message ?: "Unknown error occurred in query chain handler: ${e.message}")
    )
  }
}

const val IGNORE_HTTP_STATUS_UNSUCCESSFUL = "ignoreHTTPStatusUnsuccessful"
val WAIT_HOOK = post(afterStepContainingHeader("isAsync"), { _, _ -> Thread.sleep(5000) })

val exeHandler: ToolHandler = { toolRequest ->
  try {
    val pmCollectionPaths = PM_COLLECTION_PATH
    val pmEnvironmentPaths = PM_ENVIRONMENT_PATH
    val variableName = variableNameArg(toolRequest)
    val milestoneSplit = milestoneSplitArg(toolRequest)

    val rundown =
      ReVoman.exeChainForVariable(
        variableName,
        Kick.configure()
          .templatePaths(pmCollectionPaths)
          .dynamicEnvironment(
            mapOf(
              "percentage1" to (milestoneSplit?.getOrNull(0) ?: "30"),
              "percentage2" to (milestoneSplit?.getOrNull(1) ?: "30"),
              "percentage3" to (milestoneSplit?.getOrNull(2) ?: "40"),
            )
          )
          .environmentPaths(pmEnvironmentPaths)
          .haltOnFailureOfTypeExcept(
            HTTP_STATUS,
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
    ToolResponse.Ok(listOf(Content.Text(rundown.toJson())))
  } catch (e: Exception) {
    ToolResponse.Error(
      ErrorMessage(2, e.message ?: "Unknown error occurred in execution handler: ${e.message}")
    )
  }
}

val resumeExeHandler: ToolHandler = { toolRequest ->
  try {
    val pmCollectionPaths = PM_COLLECTION_PATH
    val pmEnvironmentPaths = PM_ENVIRONMENT_PATH
    val prevVariableName = prevVariableNameArg(toolRequest)
    val variableName = variableNameArg(toolRequest)
    val moshiReVoman = MoshiReVoman.initMoshi()
    val prevEnv = moshiReVoman.fromJson<Map<String, String?>>(prevEnvArg(toolRequest))!!

    val rundown =
      ReVoman.diffExeChainForVariable(
        prevVariableName,
        variableName,
        Kick.configure()
          .templatePaths(pmCollectionPaths)
          .environmentPaths(pmEnvironmentPaths)
          .dynamicEnvironment(prevEnv)
          .haltOnFailureOfTypeExcept(
            HTTP_STATUS,
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
    ToolResponse.Ok(listOf(Content.Text(rundown.toJson())))
  } catch (e: Exception) {
    ToolResponse.Error(
      ErrorMessage(
        3,
        e.message ?: "Unknown error occurred in resume execution handler: ${e.message}",
      )
    )
  }
}

val queryChainHandlerForPlaceOrder: ToolHandler = { toolRequest ->
  try {
    val pmCollectionPaths = "pm-templates/core/milestone/place-order.postman_collection.json"

    val variableName = "orderId"
    val chain =
      ReVoman.queryChainForVariable(
        variableName,
        Kick.configure().templatePaths(pmCollectionPaths).off(),
      )
    val variableToPmTemplate = chain.toJson()
    ToolResponse.Ok(Content.Text(variableToPmTemplate))
  } catch (e: Exception) {
    ToolResponse.Error(
      ErrorMessage(1, e.message ?: "Unknown error occurred in query chain handler: ${e.message}")
    )
  }
}

val queryChainHandlerForOneTimeProduct: ToolHandler = { toolRequest ->
  try {
    val pmCollectionPaths =
      listOf(
        "pm-templates/core/milestone/persona-creation.postman_collection.json",
        "pm-templates/core/milestone/tax-setup.postman_collection.json",
        "pm-templates/core/milestone/billing-setup-with-milestone.postman_collection.json",
        "pm-templates/core/milestone/product-setup.postman_collection.json",
      )

    val variableName = "oneTimePriceBookEntryId"
    val template =
      ReVoman.queryChainForVariable(
        variableName,
        Kick.configure().templatePaths(pmCollectionPaths).off(),
      )
    val templateJSON = template.toJson()
    ToolResponse.Ok(Content.Text(templateJSON))
  } catch (e: Exception) {
    ToolResponse.Error(
      ErrorMessage(1, e.message ?: "Unknown error occurred in query chain handler: ${e.message}")
    )
  }
}

val commandCreateOneTimeProduct: ToolHandler = { toolRequest ->
  try {
    val pmCollectionPaths =
      listOf(
        "pm-templates/core/milestone/persona-creation.postman_collection.json",
        "pm-templates/core/milestone/tax-setup.postman_collection.json",
        "pm-templates/core/milestone/billing-setup-with-milestone.postman_collection.json",
        "pm-templates/core/milestone/product-setup.postman_collection.json",
      )
    val pmEnvironmentPaths = PM_ENVIRONMENT_PATH
    val variableName = "oneTimePriceBookEntryId"
    val milestoneSplit = milestoneSplitArg(toolRequest)

    val rundown =
      ReVoman.exeChainForVariable(
        variableName,
        Kick.configure()
          .templatePaths(pmCollectionPaths)
          .dynamicEnvironment(
            mapOf(
              "percentage1" to (milestoneSplit?.getOrNull(0) ?: "30"),
              "percentage2" to (milestoneSplit?.getOrNull(1) ?: "30"),
              "percentage3" to (milestoneSplit?.getOrNull(2) ?: "40"),
            )
          )
          .environmentPaths(pmEnvironmentPaths)
          .haltOnFailureOfTypeExcept(
            HTTP_STATUS,
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
    logger.info { "Sending back:\n ${rundown.toJson()}" }
    ToolResponse.Ok(Content.Text(rundown.toJson()))
  } catch (e: Exception) {
    ToolResponse.Error(
      ErrorMessage(2, e.message ?: "Unknown error occurred in execution handler: ${e.message}")
    )
  }
}

val commandPlaceOrder: ToolHandler = { toolRequest ->
  try {
    val pmCollectionPaths = "pm-templates/core/milestone/place-order.postman_collection.json"
    val pmEnvironmentPaths = PM_ENVIRONMENT_PATH
    val variableName = "activatedOrderId"
    val moshiReVoman = MoshiReVoman.initMoshi()
    val prevEnv = moshiReVoman.fromJson<Map<String, String?>>(prevEnvArg(toolRequest))!!

    val rundown =
      ReVoman.exeChainForVariable(
        variableName,
        Kick.configure()
          .templatePaths(pmCollectionPaths)
          .environmentPaths(pmEnvironmentPaths)
          .dynamicEnvironment(prevEnv)
          .haltOnFailureOfTypeExcept(
            HTTP_STATUS,
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
    ToolResponse.Ok(Content.Text(rundown.toJson()))
  } catch (e: Exception) {
    ToolResponse.Error(
      ErrorMessage(
        3,
        e.message ?: "Unknown error occurred in resume execution handler: ${e.message}",
      )
    )
  }
}

class ReloadableMCP : HotReloadable<PolyHandler> {
  override fun create() =
    mcpHttpStreaming(
      ServerMetaData(McpEntity.of("Ape MCP server"), Version.of("1.0.0"), ToolsChanged),
      Tool(
        "query_create-order",
        """Helps to understand the steps involved in creating an Order, including all related dependencies.
          |It depends on `query_product-setup` MCP tool. Execute that before this.
          |The tool returns a Postman collection that demonstrates the complete chain of API calls needed to create and configure the entity.
          |Returns a consolidated Postman collection with all the steps involved in creating an Order.
          |If the returned data is large, consume in chunks."""
          .trimMargin(),
      ) bind queryChainHandlerForPlaceOrder,
      Tool(
        "query_product-setup",
        """Helps to understand the steps involved in creating creating a OneTime product, including all related dependencies.
          |The tool returns a Postman collection that demonstrates the complete chain of API calls needed to create and configure the entity.
          |Returns a consolidated Postman collection with all the steps involved in creating a OneTime product.
          |If the returned data is large, consume in chunks."""
          .trimMargin(),
      ) bind queryChainHandlerForOneTimeProduct,
      Tool(
        "command_product-setup",
        """Creates a OneTime product.
          |The tool's response has all the data of steps executed to create OneTime product.
          |The tool's response contains a `mutableEnv` JSON property which can be used to send to `command_` tools dependent on this tool.
          |If the returned data is large, consume in chunks."""
          .trimMargin(),
      ) bind commandCreateOneTimeProduct,
      Tool(
        "command_place-order",
        """Create an Order.
          |The tool's response has all the data of steps executed to create an Order.
          |It depends on `command_product-setup` MCP tool. Execute `command_product-setup` before this and pass the `mutableEnv` from `command_product-setup` call
          |The tool's response contains a `mutableEnv` JSON property which can be used to send to `command_` tools dependent on this tool.
          |If the returned data is large, consume in chunks."""
          .trimMargin(),
        prevEnvArg,
      ) bind commandPlaceOrder,
    )
}

fun main() {
  HotReloadServer.poly<ReloadableMCP>(Helidon(3001)).start()
}

private val logger = KotlinLogging.logger {}
