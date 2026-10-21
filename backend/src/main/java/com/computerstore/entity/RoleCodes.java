package com.computerstore.entity;

import java.util.List;

public final class RoleCodes {
    private RoleCodes() {}

    public static final String MANAGER = "QUAN_LY";
    public static final String RECEPTIONIST = "LE_TAN";
    public static final String TECHNICIAN = "THO";
    public static final String ACCOUNTANT = "KE_TOAN";
    public static final String WAREHOUSE = "THU_KHO";
    public static final String CUSTOMER = "KHACH_HANG";

    public static final List<String> STAFF = List.of(MANAGER, RECEPTIONIST, TECHNICIAN, ACCOUNTANT, WAREHOUSE);
}
