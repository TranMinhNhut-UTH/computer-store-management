package com.computerstore.computer_store_backend.operations.category.v1;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Explicitly registered by the standalone opt-in runner; outside Boot's Flyway location. */
public final class V2__register_category_permissions extends BaseJavaMigration {
    @Override public Integer getChecksum() { return FrozenCategoryRolloutV1.checksum("2"); }
    @Override public boolean canExecuteInTransaction() { return true; }
    @Override public void migrate(Context context) throws Exception { FrozenCategoryRolloutV1.migrate(context.getConnection(), false); }
}
