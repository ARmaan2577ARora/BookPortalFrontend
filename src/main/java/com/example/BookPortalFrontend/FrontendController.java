package com.example.bookPortalFrontend;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
public class FrontendController {
    private final BackendClientService backend;
    private final CartService cart;
    private final CurrentUser currentUser;

    @Value("${backend.base-url}")
    private String backendBaseUrl;

    public FrontendController(BackendClientService backend, CartService cart, CurrentUser currentUser) {
        this.backend = backend;
        this.cart = cart;
        this.currentUser = currentUser;
    }

    @ModelAttribute
    public void common(Model model, HttpSession session) {
        model.addAttribute("cartCount", cart.count(session));
        model.addAttribute("loggedInUser", currentUser.get(session));
        model.addAttribute("backendBaseUrl", backendBaseUrl);
        model.addAttribute("isAdmin", currentUser.admin(session));
        model.addAttribute("isCustomer", currentUser.customer(session));
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/books";
    }

    @GetMapping("/books")
    public String books(@RequestParam(required = false) String q, @RequestParam(required = false) Integer authorId, @RequestParam(required = false) Integer publisherId, Model model) {
        Map<String, Object> response = backend.get("/internal/catalog/books", Map.of("q", q == null ? "" : q, "authorId", authorId == null ? "" : authorId, "publisherId", publisherId == null ? "" : publisherId));
        model.addAttribute("activePage", "books");
        model.addAttribute("books", backend.list(response, "books"));
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("filterText", authorId != null ? "Books by selected author" : publisherId != null ? "Books by selected publisher" : "");
        return "books";
    }

    @GetMapping("/books/{bookId}")
    public String bookDetail(@PathVariable Integer bookId, Model model) {
        Map<String, Object> response = backend.get("/internal/catalog/books/" + bookId);
        model.addAttribute("activePage", "books");
        model.addAttribute("book", backend.map(response, "book"));
        model.addAttribute("storeBooks", backend.list(response, "storeBooks"));
        return "book-detail";
    }

    @GetMapping("/books/{bookId}/stores")
    public String compareStores(@PathVariable Integer bookId, @RequestParam(required = false) String city, Model model) {
        Map<String, Object> response = backend.get("/internal/catalog/books/" + bookId + "/stores", Map.of("city", city == null ? "" : city));
        model.addAttribute("activePage", "stores");
        model.addAttribute("book", backend.map(response, "book"));
        model.addAttribute("storeBooks", backend.list(response, "storeBooks"));
        model.addAttribute("city", city == null ? "" : city);
        return "store-availability";
    }

    @PostMapping("/cart/add")
    public String addCart(@RequestParam Integer storeBookId, @RequestParam(defaultValue = "1") Integer quantity, @RequestParam(defaultValue = "/cart") String redirectTo, HttpSession session, RedirectAttributes ra) {
        cart.add(session, storeBookId, quantity);
        ra.addFlashAttribute("success", "Book added to cart");
        return "redirect:" + redirectTo;
    }

    @PostMapping("/cart/update")
    public String updateCart(@RequestParam Integer storeBookId, @RequestParam Integer quantity, HttpSession session) { cart.update(session, storeBookId, quantity); return "redirect:/cart"; }
    @PostMapping("/cart/remove")
    public String removeCart(@RequestParam Integer storeBookId, HttpSession session) { cart.remove(session, storeBookId); return "redirect:/cart"; }

    @GetMapping("/cart")
    public String cart(Model model, HttpSession session) {
        model.addAttribute("activePage", "cart");
        model.addAttribute("cartLines", cart.lines(session));
        model.addAttribute("cartTotal", cart.total(session));
        return "cart";
    }

    @GetMapping("/checkout")
    public String checkout(Model model, HttpSession session, RedirectAttributes ra) {
        if (!currentUser.loggedIn(session)) { ra.addFlashAttribute("error", "Please login before checkout"); return "redirect:/login"; }
        if (cart.isEmpty(session)) { ra.addFlashAttribute("error", "Cart is empty"); return "redirect:/books"; }
        String email = currentUser.email(session);
        model.addAttribute("activePage", "checkout");
        model.addAttribute("cartLines", cart.lines(session));
        model.addAttribute("cartTotal", cart.total(session));
        model.addAttribute("addresses", backend.list(backend.get("/internal/users/" + email + "/addresses"), "addresses"));
        model.addAttribute("customer", currentUser.get(session));
        return "checkout";
    }

    @PostMapping("/checkout/address")
    public String addAddress(@RequestParam String houseAddress, @RequestParam String city, @RequestParam(required = false) String state,
                             @RequestParam(required = false) String zipcode, @RequestParam(defaultValue = "India") String country,
                             @RequestParam(defaultValue = "false") boolean makeDefault, HttpSession session, RedirectAttributes ra) {
        if (!currentUser.loggedIn(session)) return "redirect:/login";
        try {
            backend.post("/internal/users/" + currentUser.email(session) + "/addresses", Map.of("houseAddress", houseAddress, "city", city, "state", state == null ? "" : state, "zipcode", zipcode == null ? "" : zipcode, "country", country, "makeDefault", makeDefault));
            ra.addFlashAttribute("success", "Address added. Select it and place order.");
        } catch (Exception ex) { ra.addFlashAttribute("error", backend.errorMessage(ex)); }
        return "redirect:/checkout";
    }

    @PostMapping("/checkout/place-order")
    public String placeOrder(@RequestParam Integer addressId, @RequestParam(defaultValue = "COD") String paymentMethod, HttpSession session, RedirectAttributes ra) {
        if (!currentUser.loggedIn(session)) return "redirect:/login";
        try {
            Map<String, Object> response = backend.post("/internal/checkout/place-order", Map.of("email", currentUser.email(session), "addressId", addressId, "paymentMethod", paymentMethod, "items", cart.checkoutItems(session)));
            Map<String, Object> order = backend.map(response, "order");
            cart.clear(session);
            return "redirect:/order-confirmation/" + order.get("orderId");
        } catch (Exception ex) { ra.addFlashAttribute("error", backend.errorMessage(ex)); return "redirect:/checkout"; }
    }

    @GetMapping("/order-confirmation/{orderId}")
    public String orderConfirmation(@PathVariable Integer orderId, Model model, HttpSession session) {
        if (!currentUser.loggedIn(session)) return "redirect:/login";
        List<Map<String, Object>> orders = backend.list(backend.get("/internal/checkout/orders/" + currentUser.email(session)), "orders");
        Map<String, Object> order = orders.stream().filter(o -> String.valueOf(orderId).equals(String.valueOf(o.get("orderId")))).findFirst().orElse(Map.of("orderId", orderId));
        model.addAttribute("activePage", "orders");
        model.addAttribute("order", order);
        return "order-confirmation";
    }

    @GetMapping("/my-orders")
    public String myOrders(Model model, HttpSession session) {
        if (!currentUser.loggedIn(session)) return "redirect:/login";
        model.addAttribute("activePage", "orders");
        model.addAttribute("orders", backend.list(backend.get("/internal/checkout/orders/" + currentUser.email(session)), "orders"));
        return "my-orders";
    }


    @PostMapping("/my-orders/{orderId}/cancel")
    public String cancelOrder(@PathVariable Integer orderId, HttpSession session, RedirectAttributes ra) {
        if (!currentUser.loggedIn(session)) return "redirect:/login";
        try {
            backend.post("/internal/checkout/orders/" + currentUser.email(session) + "/" + orderId + "/cancel", Map.of());
            ra.addFlashAttribute("success", "Order cancelled and stock restored");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", backend.errorMessage(ex));
        }
        return "redirect:/my-orders";
    }

    @GetMapping("/authors-publishers")
    public String authorsPublishers(@RequestParam(required = false) String q, Model model) {
        Map<String, Object> response = backend.get("/internal/catalog/authors-publishers", Map.of("q", q == null ? "" : q));
        model.addAttribute("activePage", "authors");
        model.addAttribute("authors", backend.list(response, "authors"));
        model.addAttribute("publishers", backend.list(response, "publishers"));
        model.addAttribute("q", q == null ? "" : q);
        return "authors-publishers";
    }



    @GetMapping("/authors/{authorId}")
    public String authorDetail(@PathVariable Integer authorId, Model model) {
        Map<String, Object> directory = backend.get("/internal/catalog/authors-publishers");
        List<Map<String, Object>> authors = backend.list(directory, "authors");
        Map<String, Object> author = findById(authors, "authorId", authorId);
        Map<String, Object> booksResponse = backend.get("/internal/catalog/books", Map.of("authorId", authorId));
        model.addAttribute("activePage", "authors");
        model.addAttribute("author", author);
        model.addAttribute("books", backend.list(booksResponse, "books"));
        return "author-detail";
    }

    @GetMapping("/publishers/{publisherId}")
    public String publisherDetail(@PathVariable Integer publisherId, Model model) {
        Map<String, Object> directory = backend.get("/internal/catalog/authors-publishers");
        List<Map<String, Object>> publishers = backend.list(directory, "publishers");
        Map<String, Object> publisher = findById(publishers, "publisherId", publisherId);
        Map<String, Object> booksResponse = backend.get("/internal/catalog/books", Map.of("publisherId", publisherId));
        model.addAttribute("activePage", "authors");
        model.addAttribute("publisher", publisher);
        model.addAttribute("books", backend.list(booksResponse, "books"));
        return "publisher-detail";
    }

    @GetMapping("/stores")
    public String stores(@RequestParam(required = false) String city, Model model) {
        Map<String, Object> response = backend.get("/internal/catalog/stores", Map.of("city", city == null ? "" : city));
        model.addAttribute("activePage", "stores");
        model.addAttribute("stores", backend.list(response, "stores"));
        model.addAttribute("storeCounts", response.get("storeCounts"));
        model.addAttribute("city", city == null ? "" : city);
        return "stores";
    }

    @GetMapping("/stores/{storeId}/books")
    public String storeBooks(@PathVariable Integer storeId, Model model) {
        Map<String, Object> response = backend.get("/internal/catalog/stores/" + storeId + "/books");
        model.addAttribute("activePage", "stores");
        model.addAttribute("store", backend.map(response, "store"));
        model.addAttribute("storeBooks", backend.list(response, "storeBooks"));
        return "store-books";
    }

    @GetMapping("/profile")
    public String profile(Model model, HttpSession session) {
        if (!currentUser.loggedIn(session)) return "redirect:/login";
        model.addAttribute("activePage", "profile");
        model.addAttribute("customer", currentUser.get(session));
        model.addAttribute("addresses", backend.list(backend.get("/internal/users/" + currentUser.email(session) + "/addresses"), "addresses"));
        return "profile";
    }

    @GetMapping("/analytics")
    public String analytics(Model model, HttpSession session) {
        if (!currentUser.admin(session)) return "redirect:/login";
        model.addAttribute("activePage", "analytics");
        model.addAttribute("analytics", backend.get("/internal/admin/analytics"));
        return "analytics";
    }

    @PostMapping("/admin/orders/{orderId}/status")
    public String updateStatus(@PathVariable Integer orderId, @RequestParam String status, HttpSession session, RedirectAttributes ra) {
        if (!currentUser.admin(session)) return "redirect:/login";
        try {
            backend.post("/internal/admin/orders/" + orderId + "/status", Map.of("status", status));
            ra.addFlashAttribute("success", "Order status updated");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", backend.errorMessage(ex));
        }
        return "redirect:/analytics";
    }

    @GetMapping("/login") public String login() { return "login"; }


    @GetMapping("/oauth2/callback")
    public String oauth2Callback(@RequestParam String token, HttpSession session, RedirectAttributes ra) {
        try {
            Map<String, Object> user = backend.map(backend.get("/internal/oauth2/session", Map.of("token", token)), "user");
            session.setAttribute("loggedInUser", user);
            String role = String.valueOf(user.get("roleName"));
            if ("ADMIN".equalsIgnoreCase(role)) return "redirect:/analytics";
            return cart.isEmpty(session) ? "redirect:/books" : "redirect:/checkout";
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "GitHub login failed or expired. Please try again.");
            return "redirect:/login";
        }
    }

    @PostMapping("/login")
    public String doLogin(@RequestParam String email, @RequestParam String password, HttpSession session, RedirectAttributes ra) {
        try {
            Map<String, Object> user = backend.map(backend.post("/internal/users/login", Map.of("email", email, "password", password)), "user");
            session.setAttribute("loggedInUser", user);
            String role = String.valueOf(user.get("roleName"));
            if ("ADMIN".equalsIgnoreCase(role)) return "redirect:/analytics";
            return cart.isEmpty(session) ? "redirect:/books" : "redirect:/checkout";
        } catch (Exception ex) { ra.addFlashAttribute("error", "Invalid email or password"); return "redirect:/login"; }
    }

    @GetMapping("/signup") public String signup() { return "signup"; }
    @PostMapping("/signup")
    public String doSignup(@RequestParam String fullName, @RequestParam String email, @RequestParam String password, @RequestParam(required = false) String phone, RedirectAttributes ra) {
        try { backend.post("/internal/users/signup", Map.of("fullName", fullName, "email", email, "password", password, "phone", phone == null ? "" : phone)); ra.addFlashAttribute("success", "Signup successful. Please login."); return "redirect:/login"; }
        catch (Exception ex) { ra.addFlashAttribute("error", backend.errorMessage(ex)); return "redirect:/signup"; }
    }

    @GetMapping("/logout") public String logout(HttpSession session) { session.invalidate(); return "redirect:/login?logout"; }


    private Map<String, Object> findById(List<Map<String, Object>> items, String key, Integer id) {
        if (id == null) return Map.of();
        return items.stream()
                .filter(item -> String.valueOf(id).equals(String.valueOf(item.get(key))))
                .findFirst()
                .orElse(Map.of());
    }
}
