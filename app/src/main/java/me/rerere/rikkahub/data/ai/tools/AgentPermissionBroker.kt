package me.rerere.rikkahub.data.ai.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

enum class AgentPermissionStatus {
    Granted,
    Denied,
}

data class AgentPermissionCapability(
    val id: String,
    val title: String,
    val runtimePermissions: List<String> = emptyList(),
)

class AgentPermissionBroker(
    private val context: Context,
) {
    val capabilities = listOf(
        AgentPermissionCapability(
            id = "calendar_read",
            title = "Calendar Read",
            runtimePermissions = listOf(Manifest.permission.READ_CALENDAR),
        ),
        AgentPermissionCapability(
            id = "calendar_write",
            title = "Calendar Write",
            runtimePermissions = listOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR,
            ),
        ),
        AgentPermissionCapability(
            id = "apps",
            title = "Apps list and Open",
            runtimePermissions = emptyList(),
        )
    )

    fun getCapability(id: String): AgentPermissionCapability =
        capabilities.firstOrNull { it.id == id } ?: error("Unknown permission capability: $id")

    fun getStatus(capability: AgentPermissionCapability): AgentPermissionStatus {
        val permissions = capability.runtimePermissions
        if (permissions.isEmpty()) return AgentPermissionStatus.Granted

        val grantedCount = permissions.count { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        return if (grantedCount == permissions.size) {
            AgentPermissionStatus.Granted
        } else {
            AgentPermissionStatus.Denied
        }
    }

    fun ensureGranted(capabilityId: String, toolName: String) {
        val capability = getCapability(capabilityId)
        val status = getStatus(capability)
        if (status != AgentPermissionStatus.Granted) {
            error(
                buildString {
                    append("System permission required: ${capability.title}. ")
                    append("Please grant the required permissions in system settings.")
                }
            )
        }
    }
}
