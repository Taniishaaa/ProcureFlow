package com.procureflow.supplier;

import com.procureflow.category.Category;
import com.procureflow.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "suppliers",
        uniqueConstraints = @UniqueConstraint(name = "uk_supplier_name", columnNames = "name"))
@Getter
@Setter
public class Supplier extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "contact_name", nullable = false, length = 150)
    private String contactName;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "tax_id", length = 50)
    private String taxId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @ManyToMany
    @JoinTable(name = "supplier_categories",
            joinColumns = @JoinColumn(name = "supplier_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<Category> categories = new HashSet<>();

    public boolean supplies(Category category) {
        return categories.stream().anyMatch(c -> c.getId().equals(category.getId()));
    }
}
