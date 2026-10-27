package com.procureflow.settings;

import com.procureflow.audit.AuditService;
import com.procureflow.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Application-level settings with built-in defaults, editable by administrators. */
@Service
public class SettingsService {

    public static final String PO_PREFIX = "po.prefix";
    public static final String REQUEST_MAX_TOTAL = "request.max_total";
    public static final String COMPANY_NAME = "company.name";

    public record SettingDto(String key, String value, String description) {
    }

    private record Definition(String defaultValue, String description) {
    }

    private static final Map<String, Definition> DEFINITIONS = new LinkedHashMap<>();

    static {
        DEFINITIONS.put(COMPANY_NAME, new Definition("ProcureFlow Demo Corp",
                "Company name shown in the application header"));
        DEFINITIONS.put(PO_PREFIX, new Definition("PO",
                "Prefix of generated purchase order numbers (2-6 capital letters)"));
        DEFINITIONS.put(REQUEST_MAX_TOTAL, new Definition("5000000",
                "Maximum estimated total allowed on a single purchase request"));
    }

    private final AppSettingRepository repository;
    private final AuditService audit;

    public SettingsService(AppSettingRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public String get(String key) {
        Definition def = DEFINITIONS.get(key);
        if (def == null) {
            throw new IllegalArgumentException("Unknown setting " + key);
        }
        return repository.findById(key).map(AppSetting::getValue).orElse(def.defaultValue());
    }

    @Transactional(readOnly = true)
    public BigDecimal getDecimal(String key) {
        return new BigDecimal(get(key));
    }

    @Transactional(readOnly = true)
    public List<SettingDto> all() {
        List<SettingDto> result = new ArrayList<>();
        for (Map.Entry<String, Definition> e : DEFINITIONS.entrySet()) {
            result.add(new SettingDto(e.getKey(), get(e.getKey()), e.getValue().description()));
        }
        return result;
    }

    @Transactional
    public List<SettingDto> update(Map<String, String> values) {
        for (Map.Entry<String, String> e : values.entrySet()) {
            String key = e.getKey();
            String value = e.getValue() == null ? "" : e.getValue().trim();
            if (!DEFINITIONS.containsKey(key)) {
                throw ApiException.badRequest("Unknown setting '" + key + "'");
            }
            validate(key, value);
            AppSetting setting = repository.findById(key).orElseGet(() -> {
                AppSetting s = new AppSetting();
                s.setName(key);
                return s;
            });
            String old = setting.getValue();
            setting.setValue(value);
            repository.save(setting);
            audit.log("UPDATE_SETTING", "SETTING", null,
                    key + ": '" + (old == null ? DEFINITIONS.get(key).defaultValue() : old) + "' -> '" + value + "'");
        }
        return all();
    }

    private void validate(String key, String value) {
        switch (key) {
            case PO_PREFIX -> {
                if (!value.matches("^[A-Z]{2,6}$")) {
                    throw ApiException.badRequest("PO prefix must be 2-6 capital letters");
                }
            }
            case REQUEST_MAX_TOTAL -> {
                try {
                    if (new BigDecimal(value).signum() <= 0) {
                        throw ApiException.badRequest("Maximum request total must be greater than zero");
                    }
                } catch (NumberFormatException ex) {
                    throw ApiException.badRequest("Maximum request total must be a number");
                }
            }
            case COMPANY_NAME -> {
                if (value.isBlank() || value.length() > 100) {
                    throw ApiException.badRequest("Company name must be 1-100 characters");
                }
            }
            default -> {
            }
        }
    }
}
