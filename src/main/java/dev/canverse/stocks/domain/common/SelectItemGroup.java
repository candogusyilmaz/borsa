package dev.canverse.stocks.domain.common;

import java.util.List;

public record SelectItemGroup(String group, List<SelectItem> items) {
}
