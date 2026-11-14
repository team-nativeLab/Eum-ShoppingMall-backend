package com.example.shopping.domain.home.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.shopping.domain.home.entity.SiteSetting;

public interface SiteSettingRepository extends JpaRepository<SiteSetting, String> {
}
