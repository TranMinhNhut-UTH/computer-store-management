package com.computerstore.computer_store_backend.partners;

import com.computerstore.computer_store_backend.partners.SupplierPermissionRolloutIT.DisposableMysql;
import com.computerstore.computer_store_backend.partners.repository.CustomerRepository;
import com.computerstore.computer_store_backend.partners.repository.SupplierRepository;
import com.computerstore.computer_store_backend.partners.service.CustomerService;
import com.computerstore.computer_store_backend.partners.service.SupplierService;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import java.nio.file.Path;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

class PartnerReferenceIT {
    @TempDir Path directory;

    @Test void selectionGuardsReadCommittedStatusAndHoldLocksInTheCallerTransaction() throws Exception {
        var home = System.getProperty("csm.mysql.home");
        assertNotNull(home, "Set csm.mysql.home to the Windows MySQL 8 installation");
        try (var mysql = DisposableMysql.start(Path.of(home), directory)) {
            var db = ReportsOverviewIT.initialize(mysql);
            db.update("""
                    INSERT INTO supplier (id, code, name, created_at, updated_at) VALUES
                    (1, 'OLD', 'Original supplier', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
                    (2, 'NEW', 'Replacement supplier', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                    """);
            db.update("""
                    INSERT INTO customer (id, code, full_name, created_at, updated_at)
                    VALUES (1, 'REF', 'Customer fixture', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                    """);
            try (var app = StaffManagementIT.runApp(mysql, 0)) {
                var supplierRepo = app.getBean(SupplierRepository.class);
                var customerRepo = app.getBean(CustomerRepository.class);
                var supplier = app.getBean(SupplierService.class);
                var customer = app.getBean(CustomerService.class);
                var manager = app.getBean(PlatformTransactionManager.class);
                var write = new TransactionTemplate(manager);
                var readOnly = new TransactionTemplate(manager);
                readOnly.setReadOnly(true);

                assertThrows(IllegalTransactionStateException.class, () -> supplier.requireActiveForPurchase(2, null));
                assertThrows(IllegalTransactionStateException.class, () -> customer.requireActiveForSale(1));
                assertThrows(IllegalTransactionStateException.class, () -> readOnly.executeWithoutResult(
                        status -> supplier.requireActiveForPurchase(2, 1L)));
                assertThrows(IllegalTransactionStateException.class, () -> readOnly.executeWithoutResult(
                        status -> customer.requireActiveForSale(1)));

                var source = mysql.dataSource(ReportsOverviewIT.SCHEMA);
                try (var competing = source.getConnection(); var statement = competing.createStatement()) {
                    statement.execute("SET SESSION innodb_lock_wait_timeout=1");
                    write.executeWithoutResult(status -> {
                        supplier.requireActiveForPurchase(2, 1L);
                        customer.requireActiveForSale(1);
                        for (String sql : new String[]{"UPDATE supplier SET is_active=FALSE WHERE id=1",
                                "UPDATE supplier SET is_active=FALSE WHERE id=2",
                                "UPDATE customer SET is_active=FALSE WHERE id=1"}) {
                            assertEquals(1205, assertThrows(SQLException.class, () -> statement.executeUpdate(sql)).getErrorCode());
                        }
                    });
                    assertEquals(1, statement.executeUpdate("UPDATE supplier SET is_active=FALSE WHERE id=1"));
                }
                write.executeWithoutResult(status -> supplier.requireActiveForPurchase(2, 1L));

                assertEquals(ErrorCode.BUSINESS_CONFLICT, assertThrows(BusinessException.class,
                        () -> write.executeWithoutResult(status -> {
                            assertTrue(supplierRepo.findById(2L).orElseThrow().getActive());
                            db.update("UPDATE supplier SET is_active=FALSE WHERE id=2");
                            supplier.requireActiveForPurchase(2, null);
                        })).code());
                assertEquals(ErrorCode.BUSINESS_CONFLICT, assertThrows(BusinessException.class,
                        () -> write.executeWithoutResult(status -> {
                            assertTrue(customerRepo.findById(1L).orElseThrow().getActive());
                            db.update("UPDATE customer SET is_active=FALSE WHERE id=1");
                            customer.requireActiveForSale(1);
                        })).code());
            }
        }
    }
}
