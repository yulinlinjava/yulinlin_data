package com.yulinlin.repository;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** Repository interface scanning options. */
@ConfigurationProperties("yulinlin.repository")
public class RepositoryProperties {

    /**
     * Packages containing {@code @JoinRepository} interfaces. Plain packages scan recursively;
     * {@code *}, {@code **}, and {@code ?} are supported. Empty uses the application root package.
     */
    private List<String> scanPackages = new ArrayList<>();

    public List<String> getScanPackages() {
        return scanPackages;
    }

    public void setScanPackages(List<String> scanPackages) {
        this.scanPackages = scanPackages == null ? new ArrayList<>() : new ArrayList<>(scanPackages);
    }
}
