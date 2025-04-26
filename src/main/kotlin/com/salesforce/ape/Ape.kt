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

val PM_ENVIRONMENT_PATH = listOf("pm-templates/core/milestone/env.postman_environment.json")

val milestoneSplitArg =
  Tool.Arg.csv()
    .optional(
      "milestoneSplit",
      "Comma seperated milestone percentage split to create BillingTreatmentItems",
    )
val prevEnvArg =
  Tool.Arg.required(
    "previousEnvironment",
    "`mutableEnv` JSON property from previous `command_` call response response",
  )

const val IGNORE_HTTP_STATUS_UNSUCCESSFUL = "ignoreHTTPStatusUnsuccessful"
val WAIT_HOOK = post(afterStepContainingHeader("isAsync"), { _, _ -> Thread.sleep(5000) })

val queryChainPersonaCreation: ToolHandler = { toolRequest ->
  try {
    val pmCollectionPaths = "pm-templates/core/milestone/persona-creation.postman_collection.json"
    val variableName = "passwordReset"
    val chain =
      ReVoman.queryChainForVariable(
        variableName,
        Kick.configure().templatePaths(pmCollectionPaths).off(),
      )
    val template = chain.toJson()
    ToolResponse.Ok(Content.Text(template))
  } catch (e: Exception) {
    ToolResponse.Error(
      ErrorMessage(
        1,
        e.message ?: "Unknown error occurred in persona creation handler: ${e.message}",
      )
    )
  }
}

val queryChainOneTimeProduct: ToolHandler = { toolRequest ->
  try {
    val pmCollectionPaths = "pm-templates/core/milestone/product-setup.postman_collection.json"

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

val queryChainTax: ToolHandler = { toolRequest ->
  try {
    val pmCollectionPaths = "pm-templates/core/milestone/tax-setup.postman_collection.json"
    val variableName = "activatedTaxPolicyId"
    val chain =
      ReVoman.queryChainForVariable(
        variableName,
        Kick.configure().templatePaths(pmCollectionPaths).off(),
      )
    val template = chain.toJson()
    ToolResponse.Ok(Content.Text(template))
  } catch (e: Exception) {
    ToolResponse.Error(
      ErrorMessage(
        1,
        e.message ?: "Unknown error occurred in tax-billing setup handler: ${e.message}",
      )
    )
  }
}

val queryChainBilling: ToolHandler = { toolRequest ->
  try {
    val pmCollectionPaths =
      "pm-templates/core/milestone/billing-setup-with-milestone.postman_collection.json"
    val variableName = "activatedBillingPolicyId"
    val chain =
      ReVoman.queryChainForVariable(
        variableName,
        Kick.configure().templatePaths(pmCollectionPaths).off(),
      )
    val template = chain.toJson()
    ToolResponse.Ok(Content.Text(template))
  } catch (e: Exception) {
    ToolResponse.Error(
      ErrorMessage(
        1,
        e.message ?: "Unknown error occurred in tax-billing setup handler: ${e.message}",
      )
    )
  }
}

val queryChainPlaceOrder: ToolHandler = { toolRequest ->
  try {
    val pmCollectionPaths = "pm-templates/core/milestone/place-order.postman_collection.json"

    val variableName = "activatedOrderId"
    val chain =
      ReVoman.queryChainForVariable(
        variableName,
        Kick.configure().templatePaths(pmCollectionPaths).off(),
      )
    val template = chain.toJson()
    ToolResponse.Ok(Content.Text(template))
  } catch (e: Exception) {
    ToolResponse.Error(
      ErrorMessage(1, e.message ?: "Unknown error occurred in query chain handler: ${e.message}")
    )
  }
}

val queryChainBillingSchedule: ToolHandler = { toolRequest ->
  try {
    val pmCollectionPaths =
      "pm-templates/core/milestone/order-to-billingSchedule.postman_collection.json"
    val variableName = "billingScheduleId"
    val chain =
      ReVoman.queryChainForVariable(
        variableName,
        Kick.configure().templatePaths(pmCollectionPaths).off(),
      )
    val template = chain.toJson()
    ToolResponse.Ok(Content.Text(template))
  } catch (e: Exception) {
    ToolResponse.Error(
      ErrorMessage(
        1,
        e.message ?: "Unknown error occurred in billing schedule handler: ${e.message}",
      )
    )
  }
}

val queryChainInvoiceBillingSchedule: ToolHandler = { toolRequest ->
  try {
    val pmCollectionPaths = "pm-templates/core/milestone/invoice.postman_collection.json"
    val variableName = "invoiceId"
    val chain =
      ReVoman.queryChainForVariable(
        variableName,
        Kick.configure().templatePaths(pmCollectionPaths).off(),
      )
    val template = chain.toJson()
    ToolResponse.Ok(Content.Text(template))
  } catch (e: Exception) {
    ToolResponse.Error(
      ErrorMessage(
        1,
        e.message ?: "Unknown error occurred in invoice generation handler: ${e.message}",
      )
    )
  }
}

val queryChainInvoiceWithRecovery: ToolHandler = { toolRequest ->
  try {
    val pmCollectionPaths =
      "pm-templates/core/milestone/invoice-with-billingSchedule-recovery.postman_collection.json"
    val variableName = "invoiceIdAfterRecovery"
    val chain =
      ReVoman.queryChainForVariable(
        variableName,
        Kick.configure().templatePaths(pmCollectionPaths).off(),
      )
    val template = chain.toJson()
    ToolResponse.Ok(Content.Text(template))
  } catch (e: Exception) {
    ToolResponse.Error(
      ErrorMessage(
        1,
        e.message ?: "Unknown error occurred in invoice generation handler: ${e.message}",
      )
    )
  }
}

/** *** COMMAND TOOLS *** */
val commandPersonaCreation: ToolHandler = { toolRequest ->
  try {
    val pmCollectionPaths = "pm-templates/core/milestone/persona-creation.postman_collection.json"
    val pmEnvironmentPaths = PM_ENVIRONMENT_PATH
    val variableName = "passwordReset"

    val rundown =
      ReVoman.exeChainForVariable(
        variableName,
        Kick.configure()
          .templatePaths(pmCollectionPaths)
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
    ToolResponse.Ok(Content.Text(rundown.toJson()))
  } catch (e: Exception) {
    ToolResponse.Error(
      ErrorMessage(4, e.message ?: "Unknown error occurred in persona setup handler: ${e.message}")
    )
  }
}

val commandTaxSetup: ToolHandler = { toolRequest ->
  try {
    val pmCollectionPaths = "pm-templates/core/milestone/tax-setup.postman_collection.json"
    val pmEnvironmentPaths = PM_ENVIRONMENT_PATH
    val variableName = "activatedTaxPolicyId"
    val moshiReVoman = MoshiReVoman.initMoshi()
    val prevEnv = moshiReVoman.fromJson<Map<String, Any?>>(prevEnvArg(toolRequest))!!

    val rundown =
      ReVoman.exeChainForVariable(
        variableName,
        Kick.configure()
          .templatePaths(pmCollectionPaths)
          .environmentPaths(pmEnvironmentPaths)
          .dynamicEnvironment(prevEnv.mapValues { it.value.toString() })
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
      ErrorMessage(5, e.message ?: "Unknown error occurred in tax setup handler: ${e.message}")
    )
  }
}

val commandBillingSetupWithMilestone: ToolHandler = { toolRequest ->
  try {
    val pmCollectionPaths =
      "pm-templates/core/milestone/billing-setup-with-milestone.postman_collection.json"
    val pmEnvironmentPaths = PM_ENVIRONMENT_PATH
    val variableName = "activatedBillingPolicyId"
    val moshiReVoman = MoshiReVoman.initMoshi()
    val milestoneSplit = milestoneSplitArg(toolRequest)
    val prevEnv = moshiReVoman.fromJson<Map<String, Any?>>(prevEnvArg(toolRequest))!!

    val rundown =
      ReVoman.exeChainForVariable(
        variableName,
        Kick.configure()
          .templatePaths(pmCollectionPaths)
          .environmentPaths(pmEnvironmentPaths)
          .dynamicEnvironment(
            prevEnv.mapValues { it.value.toString() } +
              mapOf(
                "percentage1" to (milestoneSplit?.getOrNull(0) ?: "30"),
                "percentage2" to (milestoneSplit?.getOrNull(1) ?: "30"),
                "percentage3" to (milestoneSplit?.getOrNull(2) ?: "40"),
              )
          )
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
      ErrorMessage(6, e.message ?: "Unknown error occurred in billing setup handler: ${e.message}")
    )
  }
}

val commandCreateOneTimeProduct: ToolHandler = { toolRequest ->
  try {
    val pmCollectionPaths = "pm-templates/core/milestone/product-setup.postman_collection.json"
    val pmEnvironmentPaths = PM_ENVIRONMENT_PATH
    val variableName = "oneTimePriceBookEntryId"
    val moshiReVoman = MoshiReVoman.initMoshi()
    val prevEnv = moshiReVoman.fromJson<Map<String, Any?>>(prevEnvArg(toolRequest))!!

    val rundown =
      ReVoman.exeChainForVariable(
        variableName,
        Kick.configure()
          .templatePaths(pmCollectionPaths)
          .dynamicEnvironment(prevEnv.mapValues { it.value.toString() })
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
    ToolResponse.Ok(Content.Text(rundown.toJson()))
  } catch (e: Exception) {
    ToolResponse.Error(
      ErrorMessage(2, e.message ?: "Unknown error occurred in execution handler: ${e.message}")
    )
  }
}

val commandBillingSchedule: ToolHandler = { toolRequest ->
  try {
    val pmCollectionPaths =
      "pm-templates/core/milestone/order-to-billingSchedule.postman_collection.json"
    val pmEnvironmentPaths = PM_ENVIRONMENT_PATH
    val variableName = "billingScheduleId"
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
        7,
        e.message ?: "Unknown error occurred in billing schedule handler: ${e.message}",
      )
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

val commandInvoiceBillingSchedule: ToolHandler = { toolRequest ->
  try {
    val pmCollectionPaths = "pm-templates/core/milestone/invoice.postman_collection.json"
    val pmEnvironmentPaths = PM_ENVIRONMENT_PATH
    val variableName = "invoiceId"
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
        8,
        e.message ?: "Unknown error occurred in invoice generation handler: ${e.message}",
      )
    )
  }
}

val commandRecoverInvoice: ToolHandler = { toolRequest ->
  try {
    val pmCollectionPaths =
      "pm-templates/core/milestone/invoice-with-billingSchedule-recovery.postman_collection.json"
    val pmEnvironmentPaths = PM_ENVIRONMENT_PATH
    val variableName = "invoiceIdAfterRecovery"
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
        9,
        e.message ?: "Unknown error occurred in invoice recovery handler: ${e.message}",
      )
    )
  }
}

class ReloadableMCP : HotReloadable<PolyHandler> {
  override fun create() =
    mcpHttpStreaming(
      ServerMetaData(McpEntity.of("Ape MCP server"), Version.of("1.0.0"), ToolsChanged),
      Tool(
        "query_persona-setup",
        """Helps to understand the steps involved in creating a Persona, including all permissions and settings.
      |The tool returns a Postman collection that demonstrates the complete chain of API calls needed for all persona creation.
      |This is a prerequisite for all other `query_` operations."""
          .trimMargin(),
      ) bind queryChainPersonaCreation,
      Tool(
        "query_create-order",
        """Helps to understand the steps involved in creating an Order, including all related dependencies.
          |It depends on `query_product-setup` MCP tool. Execute that before this.
          |The tool returns a Postman collection that demonstrates the complete chain of API calls needed to create and configure the entity.
          |Returns a consolidated Postman collection with all the steps involved in creating an Order.
          |If the returned data is large, consume in chunks."""
          .trimMargin(),
      ) bind queryChainPlaceOrder,
      Tool(
        "query_tax-setup",
        """Helps to understand the steps involved in setting up Tax and Billing configurations.
      |Depends on `query_persona-setup` MCP tool. Execute that before this.
      |Returns a consolidated Postman collection with all Tax and Billing setup steps.
      |Required before Product Setup."""
          .trimMargin(),
      ) bind queryChainTax,
      Tool(
        "query_billing-setup-with-milestone",
        """Helps to understand the steps involved in setting up Tax and Billing configurations.
      |Depends on `query_persona-setup` MCP tool. Execute that before this.
      |Returns a consolidated Postman collection with all Tax and Billing setup steps.
      |Required before Product Setup."""
          .trimMargin(),
      ) bind queryChainBilling,
      Tool(
        "query_product-setup",
        """Helps to understand the steps involved in creating creating a OneTime product, including all related dependencies.
          |Depends on `query_tax-setup` and `query_billing-setup-with-milestone` MCP tools. Execute those before this.
          |The tool returns a Postman collection that demonstrates the complete chain of API calls needed to create and configure the entity.
          |Returns a consolidated Postman collection with all the steps involved in creating a OneTime product.
          |If the returned data is large, consume in chunks."""
          .trimMargin(),
      ) bind queryChainOneTimeProduct,
      Tool(
        "query_billing-schedule",
        """Helps to understand the steps involved in creating Billing Schedules from Order.
      |Depends on `query_create-order` MCP tool. Execute that before this.
      |Returns a consolidated Postman collection for billing schedule creation.
      |Required before Invoice Generation."""
          .trimMargin(),
      ) bind queryChainBillingSchedule,
      Tool(
        "query_invoice-billingSchedule",
        """Helps to understand the steps involved in generating Invoices.
      |Depends on `query_billing-schedule` MCP tool. Execute that before this.
      |Returns a Postman collection for invoice generation process."""
          .trimMargin(),
      ) bind queryChainInvoiceBillingSchedule,
      Tool(
        "query_invoice-with-billingSchedule-recovery",
        """Helps to understand the steps involved to recover a Billing Schedule in error state before invoicing them.
      |Depends on `query_invoice-billingSchedule` MCP tool. Execute that before this.
      |Returns a Postman collection for invoice recovery process."""
          .trimMargin(),
      ) bind queryChainInvoiceWithRecovery,

      /** COMMAND-TOOLS */
      Tool(
        "command_persona-creation",
        """Create a Persona.
          |The tool's response has all the data of steps executed to create a Persona.
          |The tool's response contains a `mutableEnv` JSON property which can be used to send to `command_` tools dependent on this tool.
          |This is a prerequisite for all other `command_` operations.
          |If the returned data is large, consume in chunks."""
          .trimMargin(),
      ) bind commandPersonaCreation,
      Tool(
        "command_place-order",
        """Create an Order.
          |The tool's response has all the data of steps executed to create an Order.
          |It depends on `command_product-setup` MCP tool. Execute `command_product-setup` before this and pass the `mutableEnv` from `command_product-setup` call response
          |The tool's response contains a `mutableEnv` JSON property which can be used to send to `command_` tools dependent on this tool.
          |If the returned data is large, consume in chunks."""
          .trimMargin(),
        prevEnvArg,
      ) bind commandPlaceOrder,
      Tool(
        "command_tax-setup",
        """Setup Tax configuration.
          |The tool's response has all the data of steps executed for Tax setup.
          |It depends on `command_persona-creation` MCP tool. Execute `command_persona-creation` before this and pass the `mutableEnv` from `command_persona-creation` call response
          |The tool's response contains a `mutableEnv` JSON property which can be used to send to `command_` tools dependent on this tool.
          |If the returned data is large, consume in chunks."""
          .trimMargin(),
        prevEnvArg,
      ) bind commandTaxSetup,
      Tool(
        "command_billing-setup-with-milestone",
        """Setup Billing configuration.
          |The tool's response has all the data of steps executed for Billing setup.
          |It depends on `command_tax-setup` MCP tool. Execute `command_tax-setup` before this and pass the `mutableEnv` from `command_tax-setup` call response
          |The tool's response contains a `mutableEnv` JSON property which can be used to send to `command_` tools dependent on this tool.
          |The tool accepts an optional parameter called `milestoneSplit` which is used to create BillingTreatmentItems
          |If the returned data is large, consume in chunks."""
          .trimMargin(),
        prevEnvArg,
        milestoneSplitArg,
      ) bind commandBillingSetupWithMilestone,
      Tool(
        "command_product-setup",
        """Creates a OneTime product.
          |The tool's response has all the data of steps executed to create OneTime product.
          |The tool's response contains a `mutableEnv` JSON property which can be used to send to `command_` tools dependent on this tool.
          |If the returned data is large, consume in chunks."""
          .trimMargin(),
        prevEnvArg,
      ) bind commandCreateOneTimeProduct,
      Tool(
        "command_billing-schedule",
        """Create a Billing Schedule from Order.
          |The tool's response has all the data of steps executed to create a Billing Schedule from Order.
          |It depends on `command_place-order` MCP tool. Execute `command_place-order` before this and pass the `mutableEnv` from `command_place-order` call response
          |The tool's response contains a `mutableEnv` JSON property which can be used to send to `command_` tools dependent on this tool.
          |If the returned data is large, consume in chunks."""
          .trimMargin(),
        prevEnvArg,
      ) bind commandBillingSchedule,
      Tool(
        "command_invoice-billingSchedule",
        """Generate Invoice for a BillingSchedule.
          |The tool's response has all the data of steps executed to generate an Invoice from BillingSchedule.
          |It depends on `command_billing-schedule` MCP tool. Execute `command_billing-schedule` before this and pass the `mutableEnv` from `command_billing-schedule` call response
          |The tool's response contains a `mutableEnv` JSON property which can be used to send to `command_` tools dependent on this tool.
          |If the returned data is large, consume in chunks."""
          .trimMargin(),
        prevEnvArg,
      ) bind commandInvoiceBillingSchedule,
      Tool(
        "command_invoice-with-billingSchedule-recovery",
        """Recovers a BillingSchedule and Invoice that BillingSchedule.
          |The tool's response has all the data of steps executed to recover an Invoice.
          |It depends on `command_invoice-billingSchedule` MCP tool. Execute `command_invoice-billingSchedule` before this and pass the `mutableEnv` from `command_invoice-billingSchedule` call response response
          |The tool's response contains a `mutableEnv` JSON property which can be used to send to `command_` tools dependent on this tool.
          |If the returned data is large, consume in chunks."""
          .trimMargin(),
        prevEnvArg,
      ) bind commandRecoverInvoice,
    )
}

fun main() {
  HotReloadServer.poly<ReloadableMCP>(Helidon(3001)).start()
}

private val logger = KotlinLogging.logger {}
