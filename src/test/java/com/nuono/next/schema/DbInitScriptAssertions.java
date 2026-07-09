package com.nuono.next.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.system.schema.DbInitMigrationRegistry;

public final class DbInitScriptAssertions {
    private DbInitScriptAssertions() {
    }

    public static void assertInitScriptsInclude(String... locations) {
        assertThat(DbInitMigrationRegistry.initScriptResourceLocations())
                .contains(locations);
    }
}
