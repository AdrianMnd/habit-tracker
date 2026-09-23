package com.adrian.habittracker.service;

import com.adrian.habittracker.dto.CategoryRequest;
import com.adrian.habittracker.dto.CategoryResponse;
import com.adrian.habittracker.entity.Category;
import com.adrian.habittracker.entity.User;
import com.adrian.habittracker.exception.CategoryAlreadyExistsException;
import com.adrian.habittracker.exception.ResourceNotFoundException;
import com.adrian.habittracker.repository.CategoryRepository;
import com.adrian.habittracker.repository.HabitRepository;
import com.adrian.habittracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final HabitRepository habitRepository;
    private final UserRepository userRepository;

    public List<CategoryResponse> findAll(Long userId) {
        return categoryRepository.findByUserIdOrderByNameAsc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CategoryResponse create(Long userId, CategoryRequest request) {
        if (categoryRepository.existsByUserIdAndNameIgnoreCase(userId, request.name())) {
            throw new CategoryAlreadyExistsException("Ya tienes una categoria con ese nombre");
        }

        User user = userRepository.getReferenceById(userId);
        Category category = new Category();
        category.setUser(user);
        category.setName(request.name());
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void delete(Long id, Long userId) {
        Category category = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria no encontrada: " + id));

        // Antes de borrar, desvinculamos cualquier habito que la tuviera
        // asignada - si no, quedarian con un category_id apuntando a una
        // fila que ya no existe (una referencia "huerfana").
        habitRepository.clearCategoryReferences(id);
        categoryRepository.delete(category);
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
