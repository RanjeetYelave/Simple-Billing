package com.billing.simple.billsoft.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "app_config")
public class AppConfig {
    @Id
    private String configKey;
    private String configValue;
}
