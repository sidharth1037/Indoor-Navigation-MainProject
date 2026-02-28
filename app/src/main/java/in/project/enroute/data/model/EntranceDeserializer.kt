package `in`.project.enroute.data.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

/**
 * Custom Gson deserializer for [Entrance] that handles the `stairs` field
 * in both old (boolean) and new (string) JSON formats.
 *
 * Old format:  `"stairs": true`  → treated as `"bottom"` (legacy compat)
 * New format:  `"stairs": "top"` / `"stairs": "bottom"`
 * Absent / false / null:         → null (regular entrance)
 */
class EntranceDeserializer : JsonDeserializer<Entrance> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Entrance {
        val obj = json.asJsonObject

        val id = obj.get("id").asInt
        val x = obj.get("x").asFloat
        val y = obj.get("y").asFloat
        val name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString
        val roomNo = obj.get("room_no")?.takeIf { !it.isJsonNull }?.asString
        val available = obj.get("available")?.asBoolean ?: true

        // Handle `stairs` field: can be boolean (old) or string (new) or absent
        val stairs: String? = obj.get("stairs")?.let { stairsElement ->
            when {
                stairsElement.isJsonNull -> null
                stairsElement.isJsonPrimitive -> {
                    val prim = stairsElement.asJsonPrimitive
                    when {
                        prim.isBoolean -> if (prim.asBoolean) "bottom" else null
                        prim.isString -> prim.asString.takeIf { it.isNotBlank() }
                        else -> null
                    }
                }
                else -> null
            }
        }

        // `floor` field: the floor number this stair connects to
        val floor = obj.get("floor")?.takeIf { !it.isJsonNull }?.asFloat

        return Entrance(
            id = id,
            x = x,
            y = y,
            name = name,
            roomNo = roomNo,
            stairs = stairs,
            floor = floor,
            available = available
        )
    }
}
