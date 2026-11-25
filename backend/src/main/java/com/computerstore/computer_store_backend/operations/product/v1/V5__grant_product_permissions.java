package com.computerstore.computer_store_backend.operations.product.v1;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Stage B. Requires matching V4 history and all five compatible Product permissions. */
public final class V5__grant_product_permissions extends BaseJavaMigration {
    @Override public Integer getChecksum() { return FrozenProductRolloutV1.checksum(5); }
    @Override public boolean canExecuteInTransaction() { return true; }
    @Override public void migrate(Context context) throws Exception { FrozenProductRolloutV1.migrate(context.getConnection(), 5); }
}
