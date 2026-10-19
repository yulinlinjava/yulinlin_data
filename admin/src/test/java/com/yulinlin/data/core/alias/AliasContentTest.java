package com.yulinlin.data.core.alias;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AliasContentTest {
    @Test void absentAndObjectSourcesUseIdentityMappings() {
        for (Class<?> source : new Class<?>[]{null, Object.class}) {
            for (boolean underscore : new boolean[]{false, true}) {
                var mapping = AliasContent.newInstance(source, underscore);
                assertThat(mapping.toColumn("displayName")).isEqualTo("displayName");
                assertThat(mapping.toKey("display_name")).isEqualTo("display_name");
            }
        }
    }

    @Test void emptyMappingsDoNotShareMutableAliasesOrChildren() {
        for (Class<?> source : new Class<?>[]{null, Object.class}) {
            var first = AliasContent.newInstance(source, true);
            first.put("name", "custom_name");
            first.getChildren("details").put("value", "custom_value");
            var second = AliasContent.newInstance(source, true);
            assertThat(second).isNotSameAs(first);
            assertThat(second.toColumn("name")).isEqualTo("name");
            assertThat(second.getChildren("details").toColumn("value")).isEqualTo("value");
        }
    }
}
