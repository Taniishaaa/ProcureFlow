package com.procureflow.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CategoryDto(Long id, String name, String description, boolean active) {
        public static CategoryDto from(Category c) {
            return new CategoryDto(c.getId(), c.getName(), c.getDescription(), c.isActive());
        }
    }

    public record CategoryInput(@NotBlank @Size(max = 100) String name, @Size(max = 300) String description) {
    }

    public record ActiveInput(boolean active) {
    }
}
