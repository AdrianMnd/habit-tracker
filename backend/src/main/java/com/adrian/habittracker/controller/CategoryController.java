package com.adrian.habittracker.controller;

import com.adrian.habittracker.dto.CategoryRequest;
import com.adrian.habittracker.dto.CategoryResponse;
import com.adrian.habittracker.security.UserPrincipal;
import com.adrian.habittracker.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public List<CategoryResponse> findAll(@AuthenticationPrincipal UserPrincipal currentUser) {
        return categoryService.findAll(currentUser.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request,
                                    @AuthenticationPrincipal UserPrincipal currentUser) {
        return categoryService.create(currentUser.getId(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal currentUser) {
        categoryService.delete(id, currentUser.getId());
    }
}
