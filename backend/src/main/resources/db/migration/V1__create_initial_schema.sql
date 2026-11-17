-- V1 initial schema. This migration only creates objects in the configured schema.
-- MySQL 8.0.16+ is required for enforced CHECK constraints.

CREATE TABLE app_role (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code VARCHAR(50) COLLATE utf8mb4_0900_bin NOT NULL,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(255) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_app_role PRIMARY KEY (id),
  CONSTRAINT uq_app_role_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_vi_0900_ai_ci;

CREATE TABLE app_user (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  full_name VARCHAR(150) NOT NULL,
  email VARCHAR(254) COLLATE utf8mb4_0900_bin NULL,
  phone VARCHAR(30) NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  last_login_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_app_user PRIMARY KEY (id),
  CONSTRAINT uq_app_user_username UNIQUE (username),
  CONSTRAINT uq_app_user_email UNIQUE (email),
  CONSTRAINT ck_app_user_active CHECK (is_active IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_vi_0900_ai_ci;

CREATE TABLE permission (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(255) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_permission PRIMARY KEY (id),
  CONSTRAINT uq_permission_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_vi_0900_ai_ci;

CREATE TABLE user_role (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  role_id BIGINT UNSIGNED NOT NULL,
  assigned_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_user_role PRIMARY KEY (id),
  CONSTRAINT uq_user_role_user_role UNIQUE (user_id, role_id),
  KEY ix_user_role_role (role_id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES app_role (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_vi_0900_ai_ci;

CREATE TABLE role_permission (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  role_id BIGINT UNSIGNED NOT NULL,
  permission_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_role_permission PRIMARY KEY (id),
  CONSTRAINT uq_role_permission_role_permission UNIQUE (role_id, permission_id),
  KEY ix_role_permission_permission (permission_id),
  CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES app_role (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permission (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_vi_0900_ai_ci;

CREATE TABLE category (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code VARCHAR(50) COLLATE utf8mb4_0900_bin NOT NULL,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(500) NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_category PRIMARY KEY (id),
  CONSTRAINT uq_category_code UNIQUE (code),
  CONSTRAINT ck_category_active CHECK (is_active IN (0, 1)),
  KEY ix_category_active_name (is_active, name)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_vi_0900_ai_ci;

CREATE TABLE supplier (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code VARCHAR(50) COLLATE utf8mb4_0900_bin NOT NULL,
  name VARCHAR(200) NOT NULL,
  phone VARCHAR(30) NULL,
  email VARCHAR(254) NULL,
  address VARCHAR(500) NULL,
  notes TEXT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_supplier PRIMARY KEY (id),
  CONSTRAINT uq_supplier_code UNIQUE (code),
  CONSTRAINT ck_supplier_active CHECK (is_active IN (0, 1)),
  KEY ix_supplier_name (name),
  KEY ix_supplier_active (is_active)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_vi_0900_ai_ci;

CREATE TABLE customer (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code VARCHAR(50) COLLATE utf8mb4_0900_bin NOT NULL,
  full_name VARCHAR(150) NOT NULL,
  phone VARCHAR(30) NULL,
  email VARCHAR(254) NULL,
  address VARCHAR(500) NULL,
  notes TEXT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_customer PRIMARY KEY (id),
  CONSTRAINT uq_customer_code UNIQUE (code),
  CONSTRAINT ck_customer_active CHECK (is_active IN (0, 1)),
  KEY ix_customer_phone (phone),
  KEY ix_customer_full_name (full_name)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_vi_0900_ai_ci;

CREATE TABLE product (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  sku VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL,
  name VARCHAR(200) NOT NULL,
  brand_name VARCHAR(100) NULL,
  model_code VARCHAR(100) NULL,
  description TEXT NULL,
  category_id BIGINT UNSIGNED NOT NULL,
  selling_price DECIMAL(19,0) NOT NULL,
  min_stock_level BIGINT NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_product PRIMARY KEY (id),
  CONSTRAINT uq_product_sku UNIQUE (sku),
  CONSTRAINT ck_product_selling_price CHECK (selling_price >= 0),
  CONSTRAINT ck_product_min_stock CHECK (min_stock_level >= 0),
  CONSTRAINT ck_product_active CHECK (is_active IN (0, 1)),
  KEY ix_product_category_active (category_id, is_active),
  KEY ix_product_name (name),
  KEY ix_product_brand (brand_name),
  KEY ix_product_model (model_code),
  CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_vi_0900_ai_ci;

CREATE TABLE inventory (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  product_id BIGINT UNSIGNED NOT NULL,
  quantity_on_hand BIGINT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_inventory PRIMARY KEY (id),
  CONSTRAINT uq_inventory_product UNIQUE (product_id),
  CONSTRAINT ck_inventory_quantity CHECK (quantity_on_hand >= 0),
  CONSTRAINT ck_inventory_version CHECK (version >= 0),
  CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_vi_0900_ai_ci;

CREATE TABLE purchase (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  document_no VARCHAR(40) COLLATE utf8mb4_0900_bin NOT NULL,
  supplier_id BIGINT UNSIGNED NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_by BIGINT UNSIGNED NOT NULL,
  confirmed_by BIGINT UNSIGNED NULL,
  confirmed_at DATETIME(6) NULL,
  notes VARCHAR(1000) NULL,
  cancelled_at DATETIME(6) NULL,
  cancelled_by BIGINT UNSIGNED NULL,
  cancel_reason VARCHAR(500) NULL,
  total_amount DECIMAL(19,0) NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_purchase PRIMARY KEY (id),
  CONSTRAINT uq_purchase_document_no UNIQUE (document_no),
  CONSTRAINT ck_purchase_total CHECK (total_amount >= 0),
  CONSTRAINT ck_purchase_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),
  CONSTRAINT ck_purchase_transition CHECK (
    (status = 'DRAFT' AND confirmed_by IS NULL AND confirmed_at IS NULL AND cancelled_by IS NULL AND cancelled_at IS NULL AND cancel_reason IS NULL)
    OR (status = 'CONFIRMED' AND confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL AND cancelled_by IS NULL AND cancelled_at IS NULL AND cancel_reason IS NULL)
    OR (status = 'CANCELLED' AND confirmed_by IS NULL AND confirmed_at IS NULL AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL AND cancel_reason IS NOT NULL)
  ),
  KEY ix_purchase_status_created (status, created_at),
  KEY ix_purchase_supplier_created (supplier_id, created_at),
  CONSTRAINT fk_purchase_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_purchase_created_by FOREIGN KEY (created_by) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_purchase_confirmed_by FOREIGN KEY (confirmed_by) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_purchase_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_vi_0900_ai_ci;

CREATE TABLE purchase_item (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  purchase_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  product_name_snapshot VARCHAR(200) NOT NULL,
  sku_snapshot VARCHAR(64) NOT NULL,
  quantity BIGINT NOT NULL,
  unit_cost DECIMAL(19,0) NOT NULL,
  line_total DECIMAL(19,0) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_purchase_item PRIMARY KEY (id),
  CONSTRAINT uq_purchase_item_purchase_product UNIQUE (purchase_id, product_id),
  CONSTRAINT ck_purchase_item_quantity CHECK (quantity > 0),
  CONSTRAINT ck_purchase_item_unit_cost CHECK (unit_cost >= 0),
  CONSTRAINT ck_purchase_item_line_total CHECK (line_total >= 0),
  KEY ix_purchase_item_product (product_id),
  CONSTRAINT fk_purchase_item_purchase FOREIGN KEY (purchase_id) REFERENCES purchase (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_purchase_item_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_vi_0900_ai_ci;

CREATE TABLE sales_order (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_no VARCHAR(40) COLLATE utf8mb4_0900_bin NOT NULL,
  customer_id BIGINT UNSIGNED NULL,
  status VARCHAR(20) NOT NULL,
  created_by BIGINT UNSIGNED NOT NULL,
  confirmed_by BIGINT UNSIGNED NULL,
  confirmed_at DATETIME(6) NULL,
  completed_at DATETIME(6) NULL,
  completed_by BIGINT UNSIGNED NULL,
  cancelled_at DATETIME(6) NULL,
  cancelled_by BIGINT UNSIGNED NULL,
  cancel_reason VARCHAR(500) NULL,
  total_amount DECIMAL(19,0) NOT NULL DEFAULT 0,
  notes VARCHAR(1000) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_sales_order PRIMARY KEY (id),
  CONSTRAINT uq_sales_order_order_no UNIQUE (order_no),
  CONSTRAINT ck_sales_order_total CHECK (total_amount >= 0),
  CONSTRAINT ck_sales_order_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'COMPLETED', 'CANCELLED')),
  CONSTRAINT ck_sales_order_audit_pairs CHECK (
    (confirmed_by IS NULL AND confirmed_at IS NULL OR confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)
    AND (completed_by IS NULL AND completed_at IS NULL OR completed_by IS NOT NULL AND completed_at IS NOT NULL)
  ),
  CONSTRAINT ck_sales_order_transition CHECK (
    (status = 'DRAFT' AND confirmed_by IS NULL AND completed_by IS NULL AND cancelled_by IS NULL AND cancelled_at IS NULL AND cancel_reason IS NULL)
    OR (status = 'CONFIRMED' AND confirmed_by IS NOT NULL AND completed_by IS NULL AND cancelled_by IS NULL AND cancelled_at IS NULL AND cancel_reason IS NULL)
    OR (status = 'COMPLETED' AND confirmed_by IS NOT NULL AND completed_by IS NOT NULL AND cancelled_by IS NULL AND cancelled_at IS NULL AND cancel_reason IS NULL)
    OR (status = 'CANCELLED' AND completed_by IS NULL AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL AND cancel_reason IS NOT NULL)
  ),
  KEY ix_sales_order_status_created (status, created_at),
  KEY ix_sales_order_customer_created (customer_id, created_at),
  CONSTRAINT fk_sales_order_customer FOREIGN KEY (customer_id) REFERENCES customer (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_sales_order_created_by FOREIGN KEY (created_by) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_sales_order_confirmed_by FOREIGN KEY (confirmed_by) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_sales_order_completed_by FOREIGN KEY (completed_by) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_sales_order_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_vi_0900_ai_ci;

CREATE TABLE sales_order_item (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  product_name_snapshot VARCHAR(200) NOT NULL,
  sku_snapshot VARCHAR(64) NOT NULL,
  brand_snapshot VARCHAR(100) NULL,
  model_snapshot VARCHAR(100) NULL,
  quantity BIGINT NOT NULL,
  unit_price DECIMAL(19,0) NOT NULL,
  line_total DECIMAL(19,0) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_sales_order_item PRIMARY KEY (id),
  CONSTRAINT uq_sales_order_item_order_product UNIQUE (order_id, product_id),
  CONSTRAINT ck_sales_order_item_quantity CHECK (quantity > 0),
  CONSTRAINT ck_sales_order_item_unit_price CHECK (unit_price >= 0),
  CONSTRAINT ck_sales_order_item_line_total CHECK (line_total >= 0),
  KEY ix_sales_order_item_product (product_id),
  CONSTRAINT fk_sales_order_item_order FOREIGN KEY (order_id) REFERENCES sales_order (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_sales_order_item_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_vi_0900_ai_ci;

CREATE TABLE payment (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  payment_no VARCHAR(40) COLLATE utf8mb4_0900_bin NOT NULL,
  order_id BIGINT UNSIGNED NOT NULL,
  amount DECIMAL(19,0) NOT NULL,
  method VARCHAR(30) NOT NULL,
  status VARCHAR(20) NOT NULL,
  paid_at DATETIME(6) NOT NULL,
  recorded_by BIGINT UNSIGNED NOT NULL,
  voided_at DATETIME(6) NULL,
  voided_by BIGINT UNSIGNED NULL,
  void_reason VARCHAR(500) NULL,
  reference_note VARCHAR(255) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_payment PRIMARY KEY (id),
  CONSTRAINT uq_payment_payment_no UNIQUE (payment_no),
  CONSTRAINT ck_payment_amount CHECK (amount > 0),
  CONSTRAINT ck_payment_method CHECK (method IN ('CASH', 'BANK_TRANSFER', 'CARD')),
  CONSTRAINT ck_payment_status CHECK (status IN ('RECORDED', 'VOIDED')),
  CONSTRAINT ck_payment_void_fields CHECK (
    (status = 'RECORDED' AND voided_at IS NULL AND voided_by IS NULL AND void_reason IS NULL)
    OR (status = 'VOIDED' AND voided_at IS NOT NULL AND voided_by IS NOT NULL AND void_reason IS NOT NULL)
  ),
  KEY ix_payment_order_paid (order_id, paid_at),
  CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES sales_order (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_payment_recorded_by FOREIGN KEY (recorded_by) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_payment_voided_by FOREIGN KEY (voided_by) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_vi_0900_ai_ci;

CREATE TABLE sales_return (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  return_no VARCHAR(40) COLLATE utf8mb4_0900_bin NOT NULL,
  order_id BIGINT UNSIGNED NOT NULL,
  status VARCHAR(20) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  created_by BIGINT UNSIGNED NOT NULL,
  confirmed_by BIGINT UNSIGNED NULL,
  confirmed_at DATETIME(6) NULL,
  cancelled_at DATETIME(6) NULL,
  cancelled_by BIGINT UNSIGNED NULL,
  cancel_reason VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_sales_return PRIMARY KEY (id),
  CONSTRAINT uq_sales_return_return_no UNIQUE (return_no),
  CONSTRAINT ck_sales_return_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),
  CONSTRAINT ck_sales_return_audit_pair CHECK (confirmed_by IS NULL AND confirmed_at IS NULL OR confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL),
  CONSTRAINT ck_sales_return_transition CHECK (
    (status = 'DRAFT' AND confirmed_by IS NULL AND cancelled_by IS NULL AND cancelled_at IS NULL AND cancel_reason IS NULL)
    OR (status = 'CONFIRMED' AND confirmed_by IS NOT NULL AND cancelled_by IS NULL AND cancelled_at IS NULL AND cancel_reason IS NULL)
    OR (status = 'CANCELLED' AND confirmed_by IS NULL AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL AND cancel_reason IS NOT NULL)
  ),
  KEY ix_sales_return_order_status (order_id, status),
  KEY ix_sales_return_created (created_at),
  CONSTRAINT fk_sales_return_order FOREIGN KEY (order_id) REFERENCES sales_order (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_sales_return_created_by FOREIGN KEY (created_by) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_sales_return_confirmed_by FOREIGN KEY (confirmed_by) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_sales_return_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_vi_0900_ai_ci;

CREATE TABLE sales_return_item (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  return_id BIGINT UNSIGNED NOT NULL,
  sales_order_item_id BIGINT UNSIGNED NOT NULL,
  quantity BIGINT NOT NULL,
  restock_quantity BIGINT NOT NULL DEFAULT 0,
  unit_refund_amount DECIMAL(19,0) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_sales_return_item PRIMARY KEY (id),
  CONSTRAINT uq_sales_return_item_return_order_item UNIQUE (return_id, sales_order_item_id),
  CONSTRAINT ck_sales_return_item_quantity CHECK (quantity > 0),
  CONSTRAINT ck_sales_return_item_restock CHECK (restock_quantity >= 0 AND restock_quantity <= quantity),
  CONSTRAINT ck_sales_return_item_refund CHECK (unit_refund_amount >= 0),
  KEY ix_sales_return_item_order_item (sales_order_item_id),
  CONSTRAINT fk_sales_return_item_return FOREIGN KEY (return_id) REFERENCES sales_return (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_sales_return_item_order_item FOREIGN KEY (sales_order_item_id) REFERENCES sales_order_item (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_vi_0900_ai_ci;

CREATE TABLE payment_refund (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  refund_no VARCHAR(40) COLLATE utf8mb4_0900_bin NOT NULL,
  payment_id BIGINT UNSIGNED NOT NULL,
  return_id BIGINT UNSIGNED NULL,
  amount DECIMAL(19,0) NOT NULL,
  method VARCHAR(30) NOT NULL,
  refunded_at DATETIME(6) NOT NULL,
  refunded_by BIGINT UNSIGNED NOT NULL,
  reason VARCHAR(500) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_payment_refund PRIMARY KEY (id),
  CONSTRAINT uq_payment_refund_refund_no UNIQUE (refund_no),
  CONSTRAINT ck_payment_refund_amount CHECK (amount > 0),
  CONSTRAINT ck_payment_refund_method CHECK (method IN ('CASH', 'BANK_TRANSFER', 'CARD')),
  KEY ix_payment_refund_payment_refunded (payment_id, refunded_at),
  KEY ix_payment_refund_return (return_id),
  CONSTRAINT fk_payment_refund_payment FOREIGN KEY (payment_id) REFERENCES payment (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_payment_refund_return FOREIGN KEY (return_id) REFERENCES sales_return (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_payment_refund_user FOREIGN KEY (refunded_by) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_vi_0900_ai_ci;

CREATE TABLE stocktake (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  stocktake_no VARCHAR(40) COLLATE utf8mb4_0900_bin NOT NULL,
  status VARCHAR(20) NOT NULL,
  performed_by BIGINT UNSIGNED NOT NULL,
  started_at DATETIME(6) NOT NULL,
  completed_at DATETIME(6) NULL,
  cancelled_at DATETIME(6) NULL,
  cancelled_by BIGINT UNSIGNED NULL,
  cancel_reason VARCHAR(500) NULL,
  notes VARCHAR(1000) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_stocktake PRIMARY KEY (id),
  CONSTRAINT uq_stocktake_stocktake_no UNIQUE (stocktake_no),
  CONSTRAINT ck_stocktake_status CHECK (status IN ('IN_PROGRESS', 'PENDING_APPROVAL', 'COMPLETED', 'CANCELLED')),
  CONSTRAINT ck_stocktake_cancel_fields CHECK (
    (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL AND cancel_reason IS NOT NULL)
    OR (status <> 'CANCELLED' AND cancelled_at IS NULL AND cancelled_by IS NULL AND cancel_reason IS NULL)
  ),
  KEY ix_stocktake_status_started (status, started_at),
  CONSTRAINT fk_stocktake_performed_by FOREIGN KEY (performed_by) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_stocktake_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_vi_0900_ai_ci;

CREATE TABLE stocktake_item (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  stocktake_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  system_quantity BIGINT NOT NULL,
  snapshot_at DATETIME(6) NOT NULL,
  counted_quantity BIGINT NULL,
  counted_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_stocktake_item PRIMARY KEY (id),
  CONSTRAINT uq_stocktake_item_stocktake_product UNIQUE (stocktake_id, product_id),
  CONSTRAINT ck_stocktake_item_system_qty CHECK (system_quantity >= 0),
  CONSTRAINT ck_stocktake_item_counted_qty CHECK (counted_quantity IS NULL OR counted_quantity >= 0),
  CONSTRAINT ck_stocktake_item_count_pair CHECK (counted_quantity IS NULL AND counted_at IS NULL OR counted_quantity IS NOT NULL AND counted_at IS NOT NULL),
  KEY ix_stocktake_item_product (product_id),
  CONSTRAINT fk_stocktake_item_stocktake FOREIGN KEY (stocktake_id) REFERENCES stocktake (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_stocktake_item_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_vi_0900_ai_ci;

CREATE TABLE inventory_adjustment (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  adjustment_no VARCHAR(40) COLLATE utf8mb4_0900_bin NOT NULL,
  status VARCHAR(20) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  proposed_by BIGINT UNSIGNED NOT NULL,
  approved_by BIGINT UNSIGNED NULL,
  proposed_at DATETIME(6) NOT NULL,
  approved_at DATETIME(6) NULL,
  cancelled_at DATETIME(6) NULL,
  cancelled_by BIGINT UNSIGNED NULL,
  cancel_reason VARCHAR(500) NULL,
  stocktake_id BIGINT UNSIGNED NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_inventory_adjustment PRIMARY KEY (id),
  CONSTRAINT uq_inventory_adjustment_no UNIQUE (adjustment_no),
  CONSTRAINT ck_inventory_adjustment_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
  CONSTRAINT ck_inventory_adjustment_approval_pair CHECK (approved_by IS NULL AND approved_at IS NULL OR approved_by IS NOT NULL AND approved_at IS NOT NULL),
  CONSTRAINT ck_inventory_adjustment_approver CHECK (approved_by IS NULL OR approved_by <> proposed_by),
  CONSTRAINT ck_inventory_adjustment_transition CHECK (
    (status = 'PENDING' AND approved_by IS NULL AND cancelled_by IS NULL AND cancelled_at IS NULL AND cancel_reason IS NULL)
    OR (status = 'APPROVED' AND approved_by IS NOT NULL AND cancelled_by IS NULL AND cancelled_at IS NULL AND cancel_reason IS NULL)
    OR (status = 'REJECTED' AND approved_by IS NULL AND cancelled_by IS NULL AND cancelled_at IS NULL AND cancel_reason IS NULL)
    OR (status = 'CANCELLED' AND approved_by IS NULL AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL AND cancel_reason IS NOT NULL)
  ),
  KEY ix_inventory_adjustment_status_proposed (status, proposed_at),
  KEY ix_inventory_adjustment_stocktake (stocktake_id),
  CONSTRAINT fk_inventory_adjustment_proposed_by FOREIGN KEY (proposed_by) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_inventory_adjustment_approved_by FOREIGN KEY (approved_by) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_inventory_adjustment_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_inventory_adjustment_stocktake FOREIGN KEY (stocktake_id) REFERENCES stocktake (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_vi_0900_ai_ci;

CREATE TABLE inventory_adjustment_item (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  adjustment_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  quantity_delta BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_inventory_adjustment_item PRIMARY KEY (id),
  CONSTRAINT uq_inventory_adjustment_item_adjustment_product UNIQUE (adjustment_id, product_id),
  CONSTRAINT ck_inventory_adjustment_item_delta CHECK (quantity_delta <> 0),
  KEY ix_inventory_adjustment_item_product (product_id),
  CONSTRAINT fk_inventory_adjustment_item_adjustment FOREIGN KEY (adjustment_id) REFERENCES inventory_adjustment (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_inventory_adjustment_item_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_vi_0900_ai_ci;

CREATE TABLE inventory_transaction (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  product_id BIGINT UNSIGNED NOT NULL,
  transaction_type VARCHAR(30) NOT NULL,
  quantity_delta BIGINT NOT NULL,
  quantity_after BIGINT NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  recorded_by BIGINT UNSIGNED NOT NULL,
  purchase_item_id BIGINT UNSIGNED NULL,
  sales_order_item_id BIGINT UNSIGNED NULL,
  sales_return_item_id BIGINT UNSIGNED NULL,
  adjustment_item_id BIGINT UNSIGNED NULL,
  reversal_of_transaction_id BIGINT UNSIGNED NULL,
  reversal_reason VARCHAR(500) NULL,
  note VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_inventory_transaction PRIMARY KEY (id),
  CONSTRAINT ck_inventory_transaction_type CHECK (transaction_type IN ('PURCHASE_IN', 'SALE_OUT', 'RETURN_IN', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'SALE_CANCEL_IN')),
  CONSTRAINT ck_inventory_transaction_delta CHECK (quantity_delta <> 0),
  CONSTRAINT ck_inventory_transaction_after CHECK (quantity_after >= 0),
  CONSTRAINT ck_inventory_transaction_source CHECK (
    (reversal_of_transaction_id IS NULL
      AND ((purchase_item_id IS NOT NULL) + (sales_order_item_id IS NOT NULL) + (sales_return_item_id IS NOT NULL) + (adjustment_item_id IS NOT NULL)) = 1
      AND reversal_reason IS NULL
      AND transaction_type <> 'SALE_CANCEL_IN')
    OR (reversal_of_transaction_id IS NOT NULL
      AND purchase_item_id IS NULL AND sales_order_item_id IS NULL AND sales_return_item_id IS NULL AND adjustment_item_id IS NULL
      AND reversal_reason IS NOT NULL
      AND transaction_type = 'SALE_CANCEL_IN')
  ),
  CONSTRAINT uq_inventory_transaction_purchase_item UNIQUE (purchase_item_id),
  CONSTRAINT uq_inventory_transaction_sales_item UNIQUE (sales_order_item_id),
  CONSTRAINT uq_inventory_transaction_return_item UNIQUE (sales_return_item_id),
  CONSTRAINT uq_inventory_transaction_adjustment_item UNIQUE (adjustment_item_id),
  CONSTRAINT uq_inventory_transaction_reversal UNIQUE (reversal_of_transaction_id),
  KEY ix_inventory_transaction_product_occurred (product_id, occurred_at),
  KEY ix_inventory_transaction_type_occurred (transaction_type, occurred_at),
  CONSTRAINT fk_inventory_transaction_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_inventory_transaction_user FOREIGN KEY (recorded_by) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_inventory_transaction_purchase_item FOREIGN KEY (purchase_item_id) REFERENCES purchase_item (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_inventory_transaction_sales_item FOREIGN KEY (sales_order_item_id) REFERENCES sales_order_item (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_inventory_transaction_return_item FOREIGN KEY (sales_return_item_id) REFERENCES sales_return_item (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_inventory_transaction_adjustment_item FOREIGN KEY (adjustment_item_id) REFERENCES inventory_adjustment_item (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_inventory_transaction_reversal FOREIGN KEY (reversal_of_transaction_id) REFERENCES inventory_transaction (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_vi_0900_ai_ci;

-- The four role codes are explicitly identified in the design. No credentials,
-- permission catalog, role-permission mapping, or operational sample data is seeded.
INSERT INTO app_role (code, name, description, created_at, updated_at) VALUES
  ('ADMIN', 'Admin', NULL, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
  ('MANAGER', 'Manager', NULL, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
  ('SALES_STAFF', 'Sales Staff', NULL, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
  ('WAREHOUSE_STAFF', 'Warehouse Staff', NULL, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
