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
  Tool.Arg.multi.required("collectionPath", "Absolute path to the Postman Collection file")

val pmEnvironmentPathArg =
  Tool.Arg.multi.required("environmentPath", "Absolute path to the Postman Environment file")

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
        "ape-execute",
        "Executes Postman Collections files along with the environment files",
        pmCollectionPathArg,
        pmEnvironmentPathArg,
      ) bind revomanToolHandler,
      Tool(
        "ape-dep-graph",
        "Accepts multiple Postman collections and provides a dependency graph view of those collections based on the variables",
        pmCollectionPathArg,
      ) bind revomanDepGraphHandler,
    )
}

fun main() {
  HotReloadServer.poly<ReloadableMCP>(Helidon(3001)).start()
}
