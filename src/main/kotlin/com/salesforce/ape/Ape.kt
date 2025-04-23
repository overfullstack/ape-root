package com.salesforce.ape

import com.salesforce.ape.CoreUtils.ASSERT_COMPOSITE_GRAPH_RESPONSE_SUCCESS
import com.salesforce.ape.CoreUtils.ASSERT_COMPOSITE_RESPONSE_SUCCESS
import com.salesforce.revoman.ReVoman
import com.salesforce.revoman.input.config.HookConfig.Companion.post
import com.salesforce.revoman.input.config.Kick
import com.salesforce.revoman.input.config.StepPick.PostTxnStepPick.PickUtils.afterStepContainingHeader
import com.salesforce.revoman.internal.json.MoshiReVoman
import com.salesforce.revoman.output.ExeType.HTTP_STATUS
import com.salesforce.revoman.output.postman.PostmanEnvironment.EnvEntry
import io.github.oshai.kotlinlogging.KotlinLogging
import org.http4k.core.PolyHandler
import org.http4k.hotreload.HotReloadServer
import org.http4k.hotreload.HotReloadable
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
    "pm-templates/core/milestone/persona-creation-and-setup.postman_collection.json",
    "pm-templates/core/milestone/milestone-setup.postman_collection.json",
    "pm-templates/core/milestone/bmp-create-runtime.postman_collection.json",
  )
val PM_ENVIRONMENT_PATH = listOf("pm-templates/core/milestone/env.postman_environment.json")

/*val pmCollectionPathArg =
  Tool.Arg.csv()
    .required("collectionPaths", "CSV string of Absolute paths to the Postman Collection files")
val pmEnvironmentPathArg =
  Tool.Arg.csv()
    .optional("environmentPaths", "CSV string of Absolute paths to the Postman Environment files")*/

val variableNameArg = Tool.Arg.required("variableName", "variable name to set or create")
val milestoneSplitArg =
  Tool.Arg.csv().optional("milestoneSplit", "Comma seperated milestone percentage split")
val prevVariableNameArg =
  Tool.Arg.required("prevVariableName", "variable name to set in the previous execution")
val prevEnvArg = Tool.Arg.required("previousEnvironment", "previous execution response JSON")

/*val revomanToolHandler: ToolHandler = { toolRequest ->
  val pmCollectionPaths = PM_COLLECTION_PATH
  val pmEnvironmentPaths = PM_ENVIRONMENT_PATH
  val rundown =
    ReVoman.revUp(
      Kick.configure()
        .templatePaths(pmCollectionPaths)
        .environmentPaths(pmEnvironmentPaths)
        .nodeModulesPath("js")
        .off()
    )
  ToolResponse.Ok(listOf(Content.Text(rundown.mutableEnv.postmanEnvJSONFormat)))
}*/

val queryChainHandler: ToolHandler = { toolRequest ->
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
}

const val IGNORE_HTTP_STATUS_UNSUCCESSFUL = "ignoreHTTPStatusUnsuccessful"
val WAIT_HOOK = post(afterStepContainingHeader("isAsync"), { _, _ -> Thread.sleep(5000) })

val exeHandler: ToolHandler = { toolRequest ->
  val pmCollectionPaths = PM_COLLECTION_PATH
  val pmEnvironmentPaths = PM_ENVIRONMENT_PATH
  val variableName = variableNameArg(toolRequest)
  val milestoneSplit = milestoneSplitArg(toolRequest)

  val environment =
    ReVoman.exeChainForVariable(
      variableName,
      Kick.configure()
        .templatePaths(pmCollectionPaths)
        .dynamicEnvironment(
          mapOf("milestoneSplit" to (milestoneSplit?.joinToString(",") ?: "30,20,30"))
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
  ToolResponse.Ok(listOf(Content.Text(environment.envJson)))
}

val resumeExeHandler: ToolHandler = { toolRequest ->
  val pmCollectionPaths = PM_COLLECTION_PATH
  val pmEnvironmentPaths = PM_ENVIRONMENT_PATH
  val prevVariableName = prevVariableNameArg(toolRequest)
  val variableName = variableNameArg(toolRequest)
  val moshiReVoman = MoshiReVoman.initMoshi()
  val prevEnv = moshiReVoman.fromJson<List<EnvEntry>>(prevEnvArg(toolRequest))!!

  val environment =
    ReVoman.diffExeChainForVariable(
      prevVariableName,
      variableName,
      Kick.configure()
        .templatePaths(pmCollectionPaths)
        .environmentPaths(pmEnvironmentPaths)
        .dynamicEnvironment(prevEnv.associate { it.key to it.value as? String })
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
  ToolResponse.Ok(listOf(Content.Text(environment.envJson)))
}

class ReloadableMCP : HotReloadable<PolyHandler> {
  override fun create() =
    mcpHttpStreaming(
      ServerMetaData(McpEntity.of("Ape MCP server"), Version.of("1.0.0"), ToolsChanged),
      /*Tool(
        "execute-collection-file",
        "Accepts a CSV string of Postman collections absolute paths and environment files absolute paths to execute",
        pmCollectionPathArg,
        pmEnvironmentPathArg,
      ) bind revomanToolHandler,*/
      /*Tool(
        "dep-graph",
        "Accepts a CSV string of Postman collections absolute paths and provides a map of variable name and Postman Collection that can create that variable",
        pmCollectionPathArg,
      ) bind revomanDepGraphHandler,*/
      Tool(
        "query-chain",
        "Accepts a CSV string of Postman collections absolute paths and variable name. Returns a consolidated Postman collection that can then later be executed to set that variable",
        variableNameArg,
      ) bind queryChainHandler,
      Tool(
        "exe-chain",
        "Accepts a CSV string of Postman collections absolute paths, environment files absolute paths, and variable name. Returns the environment variables set in the execution",
        variableNameArg,
      ) bind exeHandler,
      Tool(
        "resume-exe-chain",
        "Accepts a CSV string of Postman collections absolute paths, environment files absolute paths, previous environment JSON file, variable name set in the previous execution and variable name to set in the current execution. Returns the environment variables set in the execution",
        variableNameArg,
        prevVariableNameArg,
        prevEnvArg,
      ) bind resumeExeHandler,
    )
}

fun main() {
  HotReloadServer.poly<ReloadableMCP>(Helidon(3001)).start()
}

private val logger = KotlinLogging.logger {}
