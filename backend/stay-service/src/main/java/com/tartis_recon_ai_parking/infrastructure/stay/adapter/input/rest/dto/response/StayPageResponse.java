package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response;

import java.util.List;

/**
 * Pagina de estancias para el listado con filtros (GET /v1/stays).
 * Corresponde al schema {@code StayPage} del openapi.yml.
 */
public class StayPageResponse {

    private List<StayResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public StayPageResponse() {
    }

    public StayPageResponse(List<StayResponse> content, int page, int size,
                            long totalElements, int totalPages) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    public List<StayResponse> getContent() {
        return content;
    }

    public void setContent(List<StayResponse> content) {
        this.content = content;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
}
