package com.tartis_recon_ai_parking.application.stay.dto;

import java.util.List;

public class StayPageDTO {

    private final List<StayDTO> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;

    public StayPageDTO(final List<StayDTO> content, final int page, final int size,
                       final long totalElements, final int totalPages) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    public List<StayDTO> getContent() {
        return this.content;
    }

    public int getPage() {
        return this.page;
    }

    public int getSize() {
        return this.size;
    }

    public long getTotalElements() {
        return this.totalElements;
    }

    public int getTotalPages() {
        return this.totalPages;
    }
}
