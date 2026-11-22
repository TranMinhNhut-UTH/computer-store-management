package com.computerstore.computer_store_backend.operations.category.v1;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Stage B requires V2 successful history and all four compatible catalog rows. */
public final class V3__grant_category_permissions extends BaseJavaMigration {
    @Override public Integer getChecksum() { return FrozenCategoryRolloutV1.checksum("3"); }
    @Override public boolean canExecuteInTransaction() { return true; }
    @Override public void migrate(Context context) throws Exception { FrozenCategoryRolloutV1.migrate(context.getConnection(), true); }
}
