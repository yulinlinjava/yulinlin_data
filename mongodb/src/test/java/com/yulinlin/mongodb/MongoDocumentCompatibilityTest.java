package com.yulinlin.mongodb;

import com.yulinlin.data.core.node.base.Eq;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.wrapper.impl.UpdateFieldsWrapper;
import com.yulinlin.mongodb.coder.MongoCoderManager;
import com.yulinlin.mongodb.parse.MongoParseManager;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MongoDocumentCompatibilityTest {
    @Test
    void indexHintsAreIgnoredWithoutChangingTheMongoCommand() {
        var wrapper = new com.yulinlin.data.core.wrapper.impl.SelectWrapper<>().table("people");
        wrapper.where().eq("name", "Alice");
        var request = com.yulinlin.data.core.request.QueryRequest.newInstance(Map.class, wrapper)
                .useIndex("idx_people_name");
        var parser = new MongoParseManager();
        var plain = (com.yulinlin.data.core.parse.ParseResult) parser.parse(wrapper, context(RequestType.select));
        var hinted = (com.yulinlin.data.core.parse.ParseResult) parser.parse(wrapper, context(RequestType.select), request.getIndexHint());
        assertEquals(plain.getRequest(), hinted.getRequest());
        assertFalse(((BsonDocument) hinted.getRequest()).containsKey("hint"));
    }

    @Test
    void dottedDocumentPredicatesRemainNativeMongoFilters() {
        var context = context(RequestType.select);
        var filter = (BsonDocument) new MongoParseManager().parse(new Eq("profile.name", "Alice"), context);
        assertEquals("Alice", filter.getString("profile.name").getValue());
    }

    @Test
    void nativeDottedUpdatesAndWholeDocumentValuesRemainAvailable() {
        var fields = new UpdateFieldsWrapper<>();
        fields.field("profile.name", "Alice").field("settings", Map.of("enabled", true)).inc("stats.count", 1);
        var update = (BsonDocument) new MongoParseManager().parse(fields, context(RequestType.update));
        assertEquals("Alice", update.getDocument("$set").getString("profile.name").getValue());
        assertTrue(update.getDocument("$set").getDocument("settings").getBoolean("enabled").getValue());
        assertEquals(1, update.getDocument("$inc").getNumber("stats.count").intValue());
    }

    private static SimpParamsContext context(RequestType type) {
        return new SimpParamsContext(type, Map.of(), new MongoCoderManager().createEncoderBuffer(), Object.class, true);
    }
}
