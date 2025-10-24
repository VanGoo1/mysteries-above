package me.vangoo.infrastructure;

import com.google.gson.*;
import me.vangoo.domain.Pathway;
import me.vangoo.managers.PathwayManager;

import java.lang.reflect.Type;

public class PathwayAdapter implements JsonSerializer<Pathway> , JsonDeserializer<Pathway> {

    private final PathwayManager pathwayManager;

    public PathwayAdapter(final PathwayManager pathwayManager) {
        this.pathwayManager = pathwayManager;
    }

    @Override
    public Pathway deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        Pathway pathway = pathwayManager.getPathway(jsonElement.getAsString());
        if (pathway == null) {
            throw new JsonParseException("Unknown pathway: " + jsonElement.getAsString());
        }
        return pathway;
    }

    @Override
    public JsonElement serialize(Pathway pathway, Type type, JsonSerializationContext jsonSerializationContext) {
        return new JsonPrimitive(pathway.getName());
    }
}
