package com.procureflow.common;

import com.procureflow.category.Category;
import com.procureflow.user.User;

/** Small reference DTOs reused inside larger responses. */
public final class Refs {

    private Refs() {
    }

    public record UserRef(Long id, String name, String email, String department) {
        public static UserRef from(User u) {
            return new UserRef(u.getId(), u.getFullName(), u.getEmail(), u.getDepartment());
        }
    }

    public record CategoryRef(Long id, String name) {
        public static CategoryRef from(Category c) {
            return new CategoryRef(c.getId(), c.getName());
        }
    }
}
