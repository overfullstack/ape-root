package com.salesforce.ape

import com.salesforce.revoman.ReVoman
import com.salesforce.revoman.input.config.Kick
import org.http4k.core.PolyHandler
import org.http4k.hotreload.HotReloadServer
import org.http4k.hotreload.HotReloadable
import org.http4k.lens.string
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
  Tool.Arg.string().required("collectionPath", "Absolute path to the Postman Collection file")
val pmEnvironmentPathArg =
  Tool.Arg.string().required("environmentPath", "Absolute path to the Postman Environment file")

fun toolDefinitionFor(): Tool =
  Tool(
    "ape",
    "Executes Postman Collections files along with the environment files",
    pmCollectionPathArg,
    pmEnvironmentPathArg,
  )

val revomanToolHandler: ToolHandler = { toolRequest ->
  val pmCollectionPath = pmCollectionPathArg(toolRequest)
  val pmEnvironmentPath = pmEnvironmentPathArg(toolRequest)
  val rundown =
    ReVoman.revUp(
      Kick.configure()
        .templatePath(pmCollectionPath)
        .environmentPath(pmEnvironmentPath)
        .nodeModulesPath("js")
        .off()
    )
  ToolResponse.Ok(listOf(Content.Text(rundown.mutableEnv.postmanEnvJSONFormat)))
}

class ReloadableMCP : HotReloadable<PolyHandler> {
  override fun create() =
    mcpHttpStreaming(
      ServerMetaData(McpEntity.of("Ape MCP server"), Version.of("1.0.0"), ToolsChanged),
      toolDefinitionFor() bind revomanToolHandler,
    )
}

fun main() {
  HotReloadServer.poly<ReloadableMCP>(Helidon(3001)).start()
}
