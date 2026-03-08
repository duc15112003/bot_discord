package com.discord.bot.config;

import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JsonPropertySourceLoader implements PropertySourceLoader {

    @Override
    public String[] getFileExtensions() {
        return new String[] { "json" };
    }

    @Override
    public List<PropertySource<?>> load(String name, Resource resource) throws IOException {
        System.out.println(">>> [JsonPropertySourceLoader] Loading: " + name);
        JsonParser parser = JsonParserFactory.getJsonParser();
        Map<String, Object> map = parser.parseMap(resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        if (map.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> flattened = flatten(map);
        System.out.println(">>> [JsonPropertySourceLoader] Loaded " + flattened.size() + " properties");
        return Collections.singletonList(new MapPropertySource(name, flattened));
    }

    private Map<String, Object> flatten(Map<String, Object> map) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        flatten("", map, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Map<String, Object> source, Map<String, Object> target) {
        source.forEach((key, value) -> {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (value instanceof Map) {
                flatten(path, (Map<String, Object>) value, target);
            } else if (value instanceof List) {
                target.put(path, value); // Keep lists as is or handle them if needed
            } else {
                target.put(path, value);
            }
        });
    }
}
