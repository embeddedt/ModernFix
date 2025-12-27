package org.embeddedt.modernfix.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class OptionCategories {
    private static String defaultCategory = "default";
    private static final Map<String, String> categoryByName = new HashMap<>();
    private static final List<String> categoryOrder = new ArrayList<>();
    public static void load() {
        try(InputStream stream = OptionCategories.class.getResourceAsStream("/modernfix/option_categories.json")) {
            if(stream == null)
                throw new FileNotFoundException("option_categories.json");
            JsonObject object = new JsonParser().parse(new JsonReader(new InputStreamReader(stream, StandardCharsets.UTF_8))).getAsJsonObject();
            defaultCategory = object.get("default").getAsString();
            JsonObject obj = object.get("categories").getAsJsonObject();
            for(Map.Entry<String, JsonElement> category : obj.entrySet()) {
                categoryOrder.add(category.getKey());
                for(JsonElement e : category.getValue().getAsJsonArray()) {
                    categoryByName.put(e.getAsString(), category.getKey());
                }
            }
        } catch(IOException | RuntimeException e) {
            e.printStackTrace();
            categoryOrder.clear();
            categoryByName.clear();
            categoryOrder.add("default");
        }
    }

    public static List<String> getCategoriesInOrder() {
        return Collections.unmodifiableList(categoryOrder);
    }

    public static String getCategoryForOption(String optionName) {
        String category = categoryByName.get(optionName);
        if(category == null) {
            int lastDotIdx = optionName.lastIndexOf('.');
            if(lastDotIdx > 0) {
                category = getCategoryForOption(optionName.substring(0, lastDotIdx - 1));
            } else
                category = defaultCategory;
        }
        return category;
    }
}
