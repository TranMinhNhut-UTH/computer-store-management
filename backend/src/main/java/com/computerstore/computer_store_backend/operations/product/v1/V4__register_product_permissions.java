package com.computerstore.computer_store_backend.operations.product.v1;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Stage A. Explicitly registered by the standalone Product runner only. */
public final class V4__register_product_permissions extends BaseJavaMigration {
    @Override public Integer getChecksum() { return FrozenProductRolloutV1.checksum(4); }
    @Override public boolean canExecuteInTransaction() { return true; }
    @Override public void migrate(Context context) throws Exception { FrozenProductRolloutV1.migrate(context.getConnection(), 4); }
}
