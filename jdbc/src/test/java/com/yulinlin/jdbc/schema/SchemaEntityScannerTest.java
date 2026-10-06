package com.yulinlin.jdbc.schema;

import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.jdbc.schema.fixtures.good.Owner;
import com.yulinlin.jdbc.schema.fixtures.good.nested.NestedOwner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaEntityScannerTest {
    @Test void recursivelyFindsOwnersAndIgnoresProjectionClasses() {
        assertThat(SchemaEntityScanner.scan(List.of("com.yulinlin.jdbc.schema.fixtures.good")))
                .containsExactlyInAnyOrder(Owner.class, NestedOwner.class);
    }

    @Test void rejectsMultipleOwnersForOneTable() {
        assertThatThrownBy(() -> SchemaEntityScanner.scan(
                List.of("com.yulinlin.jdbc.schema.fixtures.conflict")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple auto-schema owners")
                .hasMessageContaining("shared_table")
                .hasMessageContaining("autoSchema = true");
    }

    @Test void emptyConfigurationScansNothing() {
        assertThat(SchemaEntityScanner.scan(List.of())).isEmpty();
    }

    @Test void automaticSchemaOwnershipIsOptIn() throws Exception {
        assertThat(JoinTable.class.getDeclaredMethod("autoSchema").getDefaultValue()).isEqualTo(false);
    }

    @Test void supportsOneLevelAndAnyDepthPackageWildcards() {
        assertThat(SchemaEntityScanner.scan(List.of("com.yulinlin.*.schema.fixtures.good")))
                .containsExactlyInAnyOrder(Owner.class, NestedOwner.class);
        assertThat(SchemaEntityScanner.scan(List.of("com.yulinlin.**.fixtures.good")))
                .containsExactlyInAnyOrder(Owner.class, NestedOwner.class);
    }

    @Test void rejectsPartialOrMalformedWildcards() {
        assertThatThrownBy(() -> SchemaEntityScanner.scan(List.of("com.yulinlin.jdbc*.schema")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid schema package pattern");
    }
}
