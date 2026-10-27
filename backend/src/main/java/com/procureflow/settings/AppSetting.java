package com.procureflow.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "app_settings")
@Getter
@Setter
public class AppSetting {

    @Id
    @Column(name = "setting_key", length = 100)
    private String name;

    @Column(name = "setting_value", nullable = false, length = 500)
    private String value;
}
