package com.spendwise.product.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.common.exception.ApiException;
import com.spendwise.product.dto.ShoppingPreferencesRequest;
import com.spendwise.product.dto.ShoppingPreferencesResponse;
import com.spendwise.product.dto.ShoppingSearchHistoryResponse;
import com.spendwise.product.dto.ShoppingSearchHistoryResponse.SearchView;
import com.spendwise.product.entity.ShoppingPreferences;
import com.spendwise.product.repository.ShoppingPreferencesRepository;
import com.spendwise.product.repository.ShoppingSearchSessionRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShoppingProfileService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final ShoppingPreferencesRepository preferencesRepository;
    private final ShoppingSearchSessionRepository searchRepository;
    private final ObjectMapper objectMapper;

    public ShoppingProfileService(ShoppingPreferencesRepository preferencesRepository,
            ShoppingSearchSessionRepository searchRepository, ObjectMapper objectMapper) {
        this.preferencesRepository = preferencesRepository;
        this.searchRepository = searchRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ShoppingPreferencesResponse getPreferences(String ownerId) {
        return preferencesRepository.findById(ownerId)
                .map(this::toResponse)
                .orElseGet(ShoppingPreferencesResponse::empty);
    }

    @Transactional
    public ShoppingPreferencesResponse savePreferences(String ownerId, ShoppingPreferencesRequest request) {
        if ((request.maxPrice() == null) != (request.currencyCode() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SHOPPING_PREFERENCES",
                    "maxPrice and currencyCode must be supplied together.");
        }
        String categories = encode(clean(request.preferredCategories()));
        String brands = encode(clean(request.preferredBrands()));
        String retailers = encode(clean(request.preferredRetailers()));
        String keywords = encode(clean(request.keywords()));
        ShoppingPreferences entity = preferencesRepository.findById(ownerId).orElseGet(() ->
                new ShoppingPreferences(ownerId, categories, brands, retailers, keywords,
                        request.maxPrice(), request.currencyCode()));
        entity.update(categories, brands, retailers, keywords, request.maxPrice(), request.currencyCode());
        return toResponse(preferencesRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public ShoppingSearchHistoryResponse history(String ownerId, int page, int size) {
        var results = searchRepository.findByOwnerId(ownerId,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new ShoppingSearchHistoryResponse(results.getContent().stream().map(SearchView::from).toList(),
                page, size, results.getTotalElements());
    }

    private ShoppingPreferencesResponse toResponse(ShoppingPreferences entity) {
        return ShoppingPreferencesResponse.from(entity, decode(entity.getPreferredCategories()),
                decode(entity.getPreferredBrands()), decode(entity.getPreferredRetailers()),
                decode(entity.getKeywords()));
    }

    private List<String> decode(String value) {
        try {
            return objectMapper.readValue(value, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_STORED_PREFERENCES",
                    "Saved shopping preferences could not be read.");
        }
    }

    private String encode(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PREFERENCES_SERIALIZATION_FAILED",
                    "Shopping preferences could not be saved.");
        }
    }

    private List<String> clean(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(value -> value != null && !value.isBlank())
                .map(String::strip).distinct().toList();
    }
}
