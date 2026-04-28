package com.monitor.amazon.controller;

import com.monitor.amazon.dto.PriceCheckResponse;
import com.monitor.amazon.dto.ProductRequest;
import com.monitor.amazon.dto.ProductResponse;
import com.monitor.amazon.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/products")
    public String productsPage(Model model) {
        model.addAttribute("products", productService.getAllProducts());
        return "products";
    }

    @GetMapping("/dashboard")
    public String dashboardPage(Model model) {
        model.addAttribute("products", productService.getAllProducts());
        return "dashboard";
    }

    @GetMapping("/api/products")
    @ResponseBody
    public List<ProductResponse> listProducts() {
        return productService.getAllProducts();
    }

    @PostMapping("/api/products")
    @ResponseBody
    public ResponseEntity<?> addProduct(@RequestBody ProductRequest request) {
        try {
            return ResponseEntity.ok(productService.addProduct(request));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    @DeleteMapping("/api/products/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/products/{id}/toggle")
    @ResponseBody
    public ResponseEntity<ProductResponse> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(productService.toggleActive(id));
    }

    @PutMapping("/api/products/{id}")
    @ResponseBody
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable Long id, @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @GetMapping("/api/products/{id}/history")
    @ResponseBody
    public List<PriceCheckResponse> getHistory(@PathVariable Long id) {
        return productService.getHistory(id);
    }
}
