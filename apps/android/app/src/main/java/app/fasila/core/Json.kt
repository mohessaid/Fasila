package app.fasila.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * The config and item documents are owned by the Rust core (`fasila_core::config`,
 * `fasila_core::schedule::Item`). The app edits them as JSON trees rather than mirroring
 * the schema in Kotlin classes, so fields this app version doesn't know about survive a
 * round trip unchanged.
 */
val FasilaJson: Json = Json {
    ignoreUnknownKeys = true
    prettyPrint = false
}

val EmptyObject = JsonObject(emptyMap())

fun JsonElement?.asObject(): JsonObject = this as? JsonObject ?: EmptyObject
fun JsonElement?.asArray(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())

fun JsonObject.obj(key: String): JsonObject = this[key].asObject()
fun JsonObject.str(key: String, default: String = ""): String =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: default
fun JsonObject.dbl(key: String, default: Double = 0.0): Double =
    (this[key] as? JsonPrimitive)?.doubleOrNull ?: default
fun JsonObject.int(key: String, default: Int = 0): Int =
    (this[key] as? JsonPrimitive)?.intOrNull ?: (this[key] as? JsonPrimitive)?.doubleOrNull?.toInt() ?: default
fun JsonObject.intOrNull(key: String): Int? =
    (this[key] as? JsonPrimitive)?.let { if (it is JsonNull) null else it.intOrNull }
fun JsonObject.bool(key: String, default: Boolean = false): Boolean =
    (this[key] as? JsonPrimitive)?.booleanOrNull ?: default

/** Returns a copy with [key] set to [value]. */
fun JsonObject.with(key: String, value: JsonElement): JsonObject = JsonObject(this + (key to value))
fun JsonObject.with(key: String, value: String): JsonObject = with(key, JsonPrimitive(value))
fun JsonObject.with(key: String, value: Number?): JsonObject = with(key, value?.let { JsonPrimitive(it) } ?: JsonNull)
fun JsonObject.with(key: String, value: Boolean): JsonObject = with(key, JsonPrimitive(value))

/** Returns a copy with the value at a dotted path (e.g. `params.adjustments.fajr`) replaced. */
fun JsonObject.withPath(path: String, value: JsonElement): JsonObject {
    val parts = path.split('.')
    fun go(node: JsonObject, i: Int): JsonObject =
        if (i == parts.lastIndex) node.with(parts[i], value)
        else node.with(parts[i], go(node.obj(parts[i]), i + 1))
    return go(this, 0)
}

fun jsonObjectOf(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(
    pairs.associate { (k, v) ->
        k to when (v) {
            null -> JsonNull
            is JsonElement -> v
            is String -> JsonPrimitive(v)
            is Number -> JsonPrimitive(v)
            is Boolean -> JsonPrimitive(v)
            else -> error("unsupported JSON value: $v")
        }
    },
)

fun parseObject(text: String): JsonObject? =
    runCatching { FasilaJson.parseToJsonElement(text) as? JsonObject }.getOrNull()
