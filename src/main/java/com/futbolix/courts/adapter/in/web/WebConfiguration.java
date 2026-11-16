package com.futbolix.courts.adapter.in.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    private final AdministratorInterceptor administratorInterceptor;

    public WebConfiguration(AdministratorInterceptor administratorInterceptor) {
        this.administratorInterceptor = administratorInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(administratorInterceptor).addPathPatterns("/api/v1/admin/**");
    }
}
