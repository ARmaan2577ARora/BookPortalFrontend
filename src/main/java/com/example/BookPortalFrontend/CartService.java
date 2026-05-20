package com.example.bookPortalFrontend;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class CartService {
    private static final String CART_KEY = "cart";
    private final BackendClientService backend;

    public CartService(BackendClientService backend) {
        this.backend = backend;
    }

    @SuppressWarnings("unchecked")
    public Map<Integer, Integer> cart(HttpSession session) {
        Object cart = session.getAttribute(CART_KEY);
        if (cart instanceof Map<?, ?> existing) return (Map<Integer, Integer>) existing;
        Map<Integer, Integer> newCart = new LinkedHashMap<>();
        session.setAttribute(CART_KEY, newCart);
        return newCart;
    }

    public void add(HttpSession session, Integer storeBookId, Integer quantity) {
        Map<Integer, Integer> cart = cart(session);
        cart.put(storeBookId, cart.getOrDefault(storeBookId, 0) + Math.max(1, quantity == null ? 1 : quantity));
    }

    public void update(HttpSession session, Integer storeBookId, Integer quantity) {
        Map<Integer, Integer> cart = cart(session);
        if (quantity == null || quantity <= 0) cart.remove(storeBookId); else cart.put(storeBookId, quantity);
    }

    public void remove(HttpSession session, Integer storeBookId) { cart(session).remove(storeBookId); }
    public void clear(HttpSession session) { cart(session).clear(); }
    public boolean isEmpty(HttpSession session) { return cart(session).isEmpty(); }
    public int count(HttpSession session) { return cart(session).values().stream().mapToInt(Integer::intValue).sum(); }

    public List<Map<String, Object>> lines(HttpSession session) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : cart(session).entrySet()) {
            Map<String, Object> sb = backend.map(backend.get("/internal/catalog/store-books/" + entry.getKey()), "storeBook");
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("storeBookId", entry.getKey());
            line.put("quantity", entry.getValue());
            line.put("storeBook", sb);
            BigDecimal price = decimal(sb.get("price"));
            line.put("subtotal", price.multiply(BigDecimal.valueOf(entry.getValue())));
            lines.add(line);
        }
        return lines;
    }

    public BigDecimal total(HttpSession session) {
        return lines(session).stream().map(line -> decimal(line.get("subtotal"))).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<Map<String, Object>> checkoutItems(HttpSession session) {
        List<Map<String, Object>> items = new ArrayList<>();

        for (Map.Entry<Integer, Integer> entry : cart(session).entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("storeBookId", entry.getKey());
            item.put("quantity", entry.getValue());
            items.add(item);
        }

        return items;
    }

    private BigDecimal decimal(Object o) {
        if (o == null || String.valueOf(o).isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(String.valueOf(o));
    }
}
