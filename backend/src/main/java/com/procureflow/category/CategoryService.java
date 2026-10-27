package com.procureflow.category;

import com.procureflow.audit.AuditService;
import com.procureflow.category.CategoryDtos.CategoryDto;
import com.procureflow.category.CategoryDtos.CategoryInput;
import com.procureflow.common.ApiException;
import com.procureflow.security.SecurityUtils;
import com.procureflow.user.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository repository;
    private final AuditService audit;

    public CategoryService(CategoryRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    /** Inactive categories are only returned to administrators who explicitly ask for them. */
    @Transactional(readOnly = true)
    public List<CategoryDto> list(boolean includeInactive) {
        boolean admin = SecurityUtils.current().role() == Role.ADMIN;
        List<Category> categories = (includeInactive && admin)
                ? repository.findAllByOrderByNameAsc()
                : repository.findByActiveTrueOrderByNameAsc();
        return categories.stream().map(CategoryDto::from).toList();
    }

    @Transactional
    public CategoryDto create(CategoryInput in) {
        String name = in.name().trim();
        if (repository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A category named '" + name + "' already exists");
        }
        Category c = new Category();
        c.setName(name);
        c.setDescription(blankToNull(in.description()));
        c = repository.save(c);
        audit.log("CREATE", "CATEGORY", c.getId(), "Created category '" + name + "'");
        return CategoryDto.from(c);
    }

    @Transactional
    public CategoryDto update(Long id, CategoryInput in) {
        Category c = repository.findById(id).orElseThrow(() -> ApiException.notFound("Category", id));
        String name = in.name().trim();
        if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.conflict("A category named '" + name + "' already exists");
        }
        c.setName(name);
        c.setDescription(blankToNull(in.description()));
        audit.log("UPDATE", "CATEGORY", c.getId(), "Updated category '" + name + "'");
        return CategoryDto.from(c);
    }

    @Transactional
    public CategoryDto setActive(Long id, boolean active) {
        Category c = repository.findById(id).orElseThrow(() -> ApiException.notFound("Category", id));
        c.setActive(active);
        audit.log(active ? "ACTIVATE" : "DEACTIVATE", "CATEGORY", c.getId(),
                (active ? "Activated" : "Deactivated") + " category '" + c.getName() + "'");
        return CategoryDto.from(c);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
