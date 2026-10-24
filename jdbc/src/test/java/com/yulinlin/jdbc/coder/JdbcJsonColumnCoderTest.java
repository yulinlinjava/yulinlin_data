package com.yulinlin.jdbc.coder;

import com.yulinlin.data.lang.json.JsonUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JdbcJsonColumnCoderTest {
    @Test
    void wholeMapsListsAndObjectsStillRoundTripThroughFieldCoders() throws Exception {
        var coders = new JdbcCoderManager();
        var encoded = coders.createEncoderBuffer();
        var payload = Map.of("name", "O'Reilly #{key} -> value", "enabled", false);
        var tags = List.of("one", "中文");
        var profile = new Profile();
        profile.name = "Alice";
        profile.enabled = true;
        encoded.put("payload", payload).put("tags", tags).put("profile", profile);
        assertEquals(JsonUtil.toJson(payload), encoded.toMap().get("payload"));
        assertEquals(JsonUtil.toJson(tags), encoded.toMap().get("tags"));
        assertEquals(JsonUtil.toJson(profile), encoded.toMap().get("profile"));

        var decoded = coders.createDecoderBuffer();
        decoded.putAll(encoded.toMap());
        assertEquals(payload, decoded.getObject("payload", Row.class.getField("payload")));
        assertEquals(tags, decoded.getObject("tags", Row.class.getField("tags")));
        Profile result = decoded.getObject("profile", Row.class.getField("profile"));
        assertEquals(profile.name, result.name);
        assertEquals(profile.enabled, result.enabled);
    }

    public static class Row {
        public Map<String, Object> payload;
        public List<String> tags;
        public Profile profile;
    }

    public static class Profile {
        public String name;
        public boolean enabled;
    }
}
