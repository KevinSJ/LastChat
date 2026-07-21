package me.rerere.rikkahub.data.ai.tools

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.core.ToolApprovalMode
import java.time.Instant
import java.time.ZoneId

fun createSettingsOpenTool(context: Context, permissionBroker: AgentPermissionBroker): Tool = Tool(
    name = "settings_open",
    description = "Open a whitelisted Android settings page, such as accessibility, notification access, app details, overlay, battery optimization, location, or default apps.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("target", buildJsonObject {
                    put("type", "string")
                    put("description", "Settings page target.")
                    put("enum", buildJsonArray {
                        add("app_details")
                        add("accessibility")
                        add("notification_access")
                        add("usage_access")
                        add("overlay")
                        add("battery_optimization")
                        add("location")
                        add("default_apps")
                    })
                })
            },
            required = listOf("target")
        )
    },
    approvalMode = ToolApprovalMode.RequiresApproval,
    execute = { input ->
        permissionBroker.ensureGranted("apps", "settings_open")
        val target = input.jsonObject["target"]?.jsonPrimitive?.contentOrNull ?: error("target is required")
        val intent = settingsIntent(context, target)
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        buildJsonObject {
            put("success", true)
            put("target", target)
        }
    }
)

fun createIntentOpenTool(context: Context, permissionBroker: AgentPermissionBroker): Tool = Tool(
    name = "intent_open",
    description = "Open a whitelisted Android intent action with optional data URI. Dangerous or non-whitelisted actions are rejected.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("action", buildJsonObject {
                    put("type", "string")
                    put("description", "Intent action.")
                    put("enum", buildJsonArray {
                        add("view")
                        add("dial")
                        add("sendto")
                        add("web_search")
                    })
                })
                put("data_uri", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional data URI. Only http/https/tel/mailto/smsto are allowed.")
                })
            },
            required = listOf("action")
        )
    },
    approvalMode = ToolApprovalMode.RequiresApproval,
    execute = { input ->
        permissionBroker.ensureGranted("apps", "intent_open")
        val action = input.jsonObject["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
        val dataUri = input.jsonObject["data_uri"]?.jsonPrimitive?.contentOrNull
        val intent = whitelistedIntent(action, dataUri)
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        buildJsonObject {
            put("success", true)
            put("action", action)
        }
    }
)

fun createCalendarListTool(context: Context, permissionBroker: AgentPermissionBroker): Tool = Tool(
    name = "calendar_list",
    description = "List Android calendar events after READ_CALENDAR is granted.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("from_epoch_ms", buildJsonObject {
                    put("type", "integer")
                    put("description", "Start Unix epoch millis. Defaults to now.")
                })
                put("to_epoch_ms", buildJsonObject {
                    put("type", "integer")
                    put("description", "End Unix epoch millis. Defaults to 7 days from start.")
                })
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("description", "Maximum events. Defaults to 30.")
                })
            }
        )
    },
    execute = { input ->
        permissionBroker.ensureGranted("calendar_read", "calendar_list")
        val events = queryCalendarEvents(context, input)
        buildJsonObject {
            put("events", events)
        }
    }
)

fun createCalendarCreateTool(context: Context, permissionBroker: AgentPermissionBroker): Tool = Tool(
    name = "calendar_create",
    description = "Create an Android calendar event. Requires WRITE_CALENDAR and explicit approval.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("title", buildJsonObject {
                    put("type", "string")
                    put("description", "Event title.")
                })
                put("start_time", buildJsonObject {
                    put("type", "string")
                    put("description", "ISO-8601 start time, for example 2026-05-03T10:00:00+08:00.")
                })
                put("end_time", buildJsonObject {
                    put("type", "string")
                    put("description", "ISO-8601 end time.")
                })
                put("start_epoch_ms", buildJsonObject {
                    put("type", "integer")
                    put("description", "Start Unix epoch millis. Used if start_time is absent.")
                })
                put("end_epoch_ms", buildJsonObject {
                    put("type", "integer")
                    put("description", "End Unix epoch millis. Used if end_time is absent.")
                })
                put("description", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional event description.")
                })
                put("location", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional event location.")
                })
            },
            required = listOf("title")
        )
    },
    approvalMode = ToolApprovalMode.RequiresApproval,
    execute = { input ->
        permissionBroker.ensureGranted("calendar_write", "calendar_create")
        val eventId = createCalendarEvent(context, input)
        buildJsonObject {
            put("success", true)
            put("event_id", eventId)
        }
    }
)

private fun settingsIntent(context: Context, target: String): Intent = when (target) {
    "app_details" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    "notification_access" -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    "usage_access" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    "overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
    "battery_optimization" -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    "default_apps" -> Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    else -> error("Unsupported settings target: $target")
}

private fun whitelistedIntent(action: String, dataUri: String?): Intent {
    val intentAction = when (action) {
        "view" -> Intent.ACTION_VIEW
        "dial" -> Intent.ACTION_DIAL
        "sendto" -> Intent.ACTION_SENDTO
        "web_search" -> Intent.ACTION_WEB_SEARCH
        else -> error("Unsupported intent action: $action")
    }
    val uri = dataUri?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
    if (uri != null) {
        val scheme = uri.scheme
        require(scheme in setOf("http", "https", "tel", "mailto", "smsto")) {
            "Unsupported data_uri scheme: $scheme"
        }
    }
    return Intent(intentAction, uri)
}

private fun queryCalendarEvents(context: Context, input: JsonElement): JsonArray = buildJsonArray {
    val params = input.jsonObject
    val now = System.currentTimeMillis()
    val from = params["from_epoch_ms"]?.jsonPrimitive?.longOrNull ?: now
    val to = params["to_epoch_ms"]?.jsonPrimitive?.longOrNull ?: (from + 7L * 24L * 60L * 60L * 1000L)
    val limit = (params["limit"]?.jsonPrimitive?.intOrNull ?: 30).coerceIn(1, 100)
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        .appendPath(from.toString())
        .appendPath(to.toString())
        .build()
    var count = 0
    context.contentResolver.query(
        uri,
        arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        ),
        null,
        null,
        "${CalendarContract.Instances.BEGIN} ASC"
    )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
        val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
        val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
        val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
        val locationIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
        val calendarIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
        while (cursor.moveToNext() && count < limit) {
            add(buildJsonObject {
                put("event_id", cursor.getLong(idIndex))
                put("title", cursor.getString(titleIndex).orEmpty())
                put("begin_epoch_ms", cursor.getLong(beginIndex))
                put("end_epoch_ms", cursor.getLong(endIndex))
                put("location", cursor.getString(locationIndex).orEmpty())
                put("calendar", cursor.getString(calendarIndex).orEmpty())
            })
            count++
        }
    }
}

private fun createCalendarEvent(context: Context, input: JsonElement): Long {
    val params = input.jsonObject
    val calendarId = firstWritableCalendarId(context)
    val start = timeMillis(params, "start_time", "start_epoch_ms")
    val end = timeMillis(params, "end_time", "end_epoch_ms")
    require(end > start) { "end_time must be after start_time" }
    val title = params["title"]?.jsonPrimitive?.contentOrNull ?: error("title is required")
    val description = params["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val location = params["location"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val values = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calendarId)
        put(CalendarContract.Events.TITLE, title)
        put(CalendarContract.Events.DESCRIPTION, description)
        put(CalendarContract.Events.EVENT_LOCATION, location)
        put(CalendarContract.Events.DTSTART, start)
        put(CalendarContract.Events.DTEND, end)
        put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
    }
    val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        ?: error("Failed to create calendar event")
    return ContentUris.parseId(uri)
}

private fun firstWritableCalendarId(context: Context): Long {
    context.contentResolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        arrayOf(CalendarContract.Calendars._ID),
        "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
        arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            return cursor.getLong(0)
        }
    }
    error("No writable calendar found")
}

private fun timeMillis(params: JsonObject, isoName: String, epochName: String): Long {
    params[isoName]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return Instant.parse(it).toEpochMilli() }
    return params[epochName]?.jsonPrimitive?.longOrNull ?: error("$isoName or $epochName is required")
}
