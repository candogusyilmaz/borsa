package dev.canverse.stocks.platform.web;

import java.util.List;

public record SliceResponse<T>(List<T> items, int page, int size, boolean hasNext) {

    public SliceResponse {
        items = List.copyOf(items);
    }
}
