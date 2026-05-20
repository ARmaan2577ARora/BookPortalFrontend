package com.example.bookPortalFrontend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

@Service
public class BackendClientService {
    private final RestTemplate restTemplate;
    private final String backendBaseUrl;
    private final ParameterizedTypeReference<Map<String, Object>> mapType = new ParameterizedTypeReference<>() {};

    public BackendClientService(RestTemplate restTemplate, @Value("${backend.base-url}") String backendBaseUrl) {
        this.restTemplate = restTemplate;
        this.backendBaseUrl = backendBaseUrl;
    }

    public Map<String, Object> get(String path) {
        return restTemplate.exchange(backendBaseUrl + path, HttpMethod.GET, null, mapType).getBody();
    }

    public Map<String, Object> get(String path, Map<String, Object> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(backendBaseUrl + path);
        params.forEach((key, value) -> { if (value != null && !String.valueOf(value).isBlank()) builder.queryParam(key, value); });
        return restTemplate.exchange(builder.toUriString(), HttpMethod.GET, null, mapType).getBody();
    }

    public Map<String, Object> post(String path, Map<String, Object> body) {
        return restTemplate.exchange(backendBaseUrl + path, HttpMethod.POST, new HttpEntity<>(body), mapType).getBody();
    }

    public List<Map<String, Object>> list(Map<String, Object> response, String key) {
        Object value = response == null ? null : response.get(key);
        if (!(value instanceof List<?> raw)) return List.of();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object item : raw) if (item instanceof Map<?, ?> map) list.add((Map<String, Object>) map);
        return list;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> map(Map<String, Object> response, String key) {
        Object value = response == null ? null : response.get(key);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    public String errorMessage(Exception ex) {
        if (ex instanceof HttpClientErrorException h && h.getResponseBodyAsString() != null && !h.getResponseBodyAsString().isBlank()) {
            return h.getResponseBodyAsString();
        }
        return ex.getMessage() == null ? "Backend request failed" : ex.getMessage();
    }
}
