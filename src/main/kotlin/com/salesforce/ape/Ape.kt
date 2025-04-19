package com.salesforce.ape

import com.salesforce.revoman.ReVoman
import com.salesforce.revoman.input.config.Kick
import org.http4k.core.PolyHandler
import org.http4k.hotreload.HotReloadServer
import org.http4k.hotreload.HotReloadable
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

val pmCollectionPathArg =
  Tool.Arg.multi.required(
    "collectionPaths",
    "JSON array of Absolute paths to the Postman Collection files",
  )

val pmEnvironmentPathArg =
  Tool.Arg.multi.required(
    "environmentPaths",
    "JSON array of Absolute paths to the Postman Environment files",
  )

val revomanToolHandler: ToolHandler = { toolRequest ->
  val pmCollectionPaths = pmCollectionPathArg(toolRequest)
  val pmEnvironmentPaths = pmEnvironmentPathArg(toolRequest)
  val rundown =
    ReVoman.revUp(
      Kick.configure()
        .templatePaths(pmCollectionPaths)
        .environmentPaths(pmEnvironmentPaths)
        .nodeModulesPath("js")
        .off()
    )
  ToolResponse.Ok(listOf(Content.Text(rundown.mutableEnv.postmanEnvJSONFormat)))
}

val revomanDepGraphHandler: ToolHandler = { toolRequest ->
  val pmCollectionPaths = pmCollectionPathArg(toolRequest)
  val depGraph = ReVoman.buildDepGraph(Kick.configure().templatePaths(pmCollectionPaths).off())
  ToolResponse.Ok(listOf(Content.Text(depGraph.toJson())))
}

class ReloadableMCP : HotReloadable<PolyHandler> {
  override fun create() =
    mcpHttpStreaming(
      ServerMetaData(McpEntity.of("Ape MCP server"), Version.of("1.0.0"), ToolsChanged),
      Tool(
        "execute-collection-file",
        "Executes Postman Collections files along with the environment files",
        pmCollectionPathArg,
        pmEnvironmentPathArg,
      ) bind revomanToolHandler,
      Tool(
        "dep-graph",
        "Accepts a JSON array of Postman collections and provides a map of variable name and Postman Collection that can create that variable",
        pmCollectionPathArg,
      ) bind revomanDepGraphHandler,
      Tool(
        "execute-collection",
        "Accepts a JSON array of Postman collections and environment files to execute",
        pmCollectionPathArg,
      ) bind revomanDepGraphHandler,
    )
}

fun main() {
  HotReloadServer.poly<ReloadableMCP>(Helidon(3001)).start()
}
