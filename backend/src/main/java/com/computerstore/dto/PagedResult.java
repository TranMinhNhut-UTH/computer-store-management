package com.computerstore.dto;

import java.util.List;

public record PagedResult<T>(List<T> items, long total, int page, int pageSize) {}
