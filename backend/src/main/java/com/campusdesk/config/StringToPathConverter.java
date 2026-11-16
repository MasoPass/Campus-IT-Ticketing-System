package com.campusdesk.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

// so paths in application.properties are read as normal file paths
@Component
@ConfigurationPropertiesBinding
public class StringToPathConverter implements Converter<String, Path> {
    @Override
    public Path convert(String source) {
        String s = source.trim();
        return s.isEmpty() ? null : Path.of(s);
    }
}
