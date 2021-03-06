package dev.dules;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ClassUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import dev.dules.exception.InvalidCSVSourceException;
import dev.dules.formatter.FieldFormatter;
import dev.dules.formatter.FormatterRegistry;

public class EasyCSV {
    private static final Logger logger = LogManager.getLogger();

    public EasyCSV(final Object source) {
        if (source == null) {
            throw new IllegalArgumentException();
        }
        this.source = source;
        setDefaults();
    }

    private EasyCSV() {
        setDefaults();
    }

    public static EasyCSV builder() {
        return new EasyCSV();
    }

    private Object source;
    private String separator;
    private List<Field> fields;
    private String header;
    private String rows;
    private Map<String, String> fieldFormatPattern;

    private void setDefaults() {
        this.separator = ",";
        this.header = "";
        this.rows = "";
        setFields();
    }

    public String build() {
        if (this.source == null) {
            throw new InvalidCSVSourceException();
        }
        this.buildHeader().buildRows();
        return String.format("%s%n%s", header, rows);
    }

    public EasyCSV setSource(final Object source) {
        if (source == null) {
            throw new UnsupportedOperationException("can't set a null source object");
        }
        this.source = source;
        setFields();
        return this;
    }

    private void setFields() {
        if (this.source != null) {
            this.fields = Arrays.asList(this.source.getClass().getDeclaredFields());
        }
    }

    public List<Field> getFields() {
        if (fields == null) {
            fields = new ArrayList<>();
        }
        return Collections.unmodifiableList(fields);
    }

    public EasyCSV setFieldPattern(final String fieldName, final String pattern) {
        if (this.fieldFormatPattern == null) {
            this.fieldFormatPattern = new HashMap<>();
        }
        this.fieldFormatPattern.put(fieldName, pattern);
        return this;
    }

    private EasyCSV buildHeader() {
        this.header = getFields().stream().filter(f -> isTypeSupported(f.getType())).map(Field::getName)
                .collect(Collectors.joining(separator));
        return this;
    }

    private EasyCSV buildRows() {
        this.rows = getFields().stream().filter(f -> isTypeSupported(f.getType())).map(f -> {
            try {
                f.setAccessible(true);
                return getFormattedValue(f.getName(), f.get(source));
            } catch (final IllegalArgumentException | IllegalAccessException e) {
                logger.error(e.getMessage());
                return "";
            }
        }).collect(Collectors.joining(separator));
        return this;
    }

    public boolean isTypeSupported(final Class<?> c) {
        return ClassUtils.isPrimitiveOrWrapper(c) || FormatterRegistry.containsClassFormatter(c);
    }
    
    /***
     * 
     * @param fieldName A case-sensitive field name.
     * @param fieldValue The pattern that can be used to achieve the desired string format. The formatter implementation may or may not use it.
     * @return A formatted String
     */
    public String getFormattedValue(final String fieldName, final Object fieldValue) {
        final FieldFormatter customFormatter = FormatterRegistry.find(fieldValue.getClass());
        if (customFormatter != null) {
            String pattern = fieldFormatPattern.get(fieldName);
            if (pattern == null) {
                pattern = customFormatter.getDefaultPattern();
            }
            return customFormatter.format(fieldValue, pattern);
        } else if (ClassUtils.isPrimitiveOrWrapper(fieldValue.getClass())) {
            return fieldValue.getClass().cast(fieldValue).toString();
        } else {
            return "";
        }
    }
}