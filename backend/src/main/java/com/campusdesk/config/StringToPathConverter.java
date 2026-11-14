package com.campusdesk.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Spring's default String -> Path conversion treats the value as a classpath/URL
 * "resource", which rejects relative paths like "../worker". This converter makes
 * Path settings plain filesystem paths. @ConfigurationPropertiesBinding makes Spring
 * try it before its built-in converters. A blank value becomes null ("not set").
 */
@Component
@ConfigurationPropertiesBinding
public class StringToPathConverter implements Converter<String, Path> {
    @Override
    public Path convert(String source) {
        String s = source.trim();
        return s.isEmpty() ? null : Path.of(s);
    }
}
