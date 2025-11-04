package com.example.smartshop.models.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.io.Serializable;
import java.util.List;

/**
 * Wrapper cho Page để có thể cache với Redis
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CacheablePage<T> implements Serializable {

    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;

    /**
     * Convert từ Spring Page sang CacheablePage
     */
    public static <T> CacheablePage<T> of(Page<T> page) {
        return new CacheablePage<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.isEmpty()
        );
    }
}
