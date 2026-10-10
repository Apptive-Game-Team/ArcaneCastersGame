package com.wordonline.server.preview;

import java.util.List;

public record PreviewCatalog(String status, String revision, List<Entry> magics) {
    public record Entry(String name, String hash, int bytes, boolean available) { }
}
