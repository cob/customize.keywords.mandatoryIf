package com.cultofbits.customize.mandatoryif.validators;

import com.cultofbits.recordm.core.model.FieldKeyArguments;
import com.cultofbits.recordm.core.model.Instance;
import com.cultofbits.recordm.core.model.InstanceField;
import com.cultofbits.recordm.customvalidators.api.AbstractOnCreateValidator;
import com.cultofbits.recordm.customvalidators.api.ErrorType;
import com.cultofbits.recordm.customvalidators.api.OnUpdateValidator;
import com.cultofbits.recordm.customvalidators.api.ValidationError;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.cultofbits.recordm.customvalidators.api.ValidationError.custom;
import static com.cultofbits.recordm.customvalidators.api.ValidationError.standard;

public class MandatoryIfValidator extends AbstractOnCreateValidator implements OnUpdateValidator {

    public static final String KEYWORD = "$mandatoryIf";

    protected static final Pattern EXPRESSION_PATTERN = Pattern.compile("(.*?)(=|!=|>|<|$)(.*)");

    @SuppressWarnings("UnstableApiUsage")
    private static final LoadingCache<String, Expr> EXPRESSION_CACHE_BUILDER = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(24, TimeUnit.HOURS)
            .build(CacheLoader.from(MandatoryIfValidator::buildExpression));

    @Override
    public Collection<ValidationError> onCreate(Instance instance) {
        return validateInstanceFields(instance.getRootFields());
    }

    @Override
    public Collection<ValidationError> onUpdate(Instance persistedInstance, Instance updatedInstance) {
        return validateInstanceFields(updatedInstance.getRootFields());
    }

    public Collection<ValidationError> validateInstanceFields(List<InstanceField> instanceFields) {
        List<ValidationError> errors = new ArrayList<>();

        for (InstanceField instanceField : instanceFields) {
            if ((!instanceField.isVisible() || instanceField.getValue() != null)
                && instanceField.children.isEmpty()) {
                continue;
            }

            if (!instanceField.fieldDefinition.containsExtension(KEYWORD)) {
                errors.addAll(validateInstanceFields(instanceField.children));
                continue;
            }

            if (instanceField.children.isEmpty()
                    && (instanceField.getValue() != null || !instanceField.fieldDefinition.containsExtension(KEYWORD)))
                continue;

            FieldKeyArguments keywordArgs = instanceField.fieldDefinition.argsFor(KEYWORD);
            List<String> expressions = !(keywordArgs instanceof FieldKeyArguments.None) ? keywordArgs.get() : Collections.emptyList();

            if (expressions.isEmpty() && instanceField.getValue() == null) {
                errors.add(standard(instanceField, ErrorType.MANDATORY));

            } else {
                evaluateAsAND(instanceField, expressions, errors);
            }

            if (instanceField.children.size() > 0) {
                errors.addAll(validateInstanceFields(instanceField.children));
            }
        }

        return errors;
    }

    protected void evaluateAsAND(InstanceField instanceField, List<String> expressions, List<ValidationError> errors) {
        boolean isError = true;

        for (String mandatoryExpression : expressions) {
            //noinspection UnstableApiUsage
            Expr expr = EXPRESSION_CACHE_BUILDER.getUnchecked(mandatoryExpression);

            try {
                isError = expr.isTrue(instanceField.getClosest(expr.fieldName)) && isError;
            } catch (Exception e) {
                errors.add(custom(instanceField, "Error evaluating mandatoryIf expression: " + mandatoryExpression));
                break;
            }
        }

        if (isError) {
            errors.add(standard(instanceField, ErrorType.MANDATORY));
        }

    }

    protected static Expr buildExpression(String arg) {
        if (arg == null || arg.trim().isEmpty()) {
            return new Expr();
        }

        Matcher expMatcher = EXPRESSION_PATTERN.matcher(arg);
        if (!expMatcher.matches()) {
            throw new IllegalStateException("The expression pattern should have matched. {{"
                    + "expression:" + arg + "}}");
        }

        return new Expr(expMatcher.group(1), expMatcher.group(2), expMatcher.group(3));
    }

    protected static class Expr {
        private String fieldName;
        private String operation;
        private String value;

        public Expr() {
        }

        public Expr(String fieldName, String operation, String value) {
            this.fieldName = fieldName.trim();
            this.operation = operation != null && !operation.isEmpty() ? operation.trim() : null;
            this.value = value != null && !value.isEmpty() ? value.trim() : null;
        }

        public boolean isTrue(InstanceField sourceField) {
            String fieldValue = sourceField.getValue();

            if ("=".equals(operation)) {
                return (value == null && fieldValue == null) // both are null
                        || (value != null && value.equals(fieldValue));

            } else if ("!=".equals(operation)) {
                return (value == null && fieldValue != null) // both are null
                        || (value != null && !value.equals(fieldValue));

            } else if (">".equals(operation)) {
                return value != null && fieldValue != null
                        && Float.parseFloat(fieldValue) > Float.parseFloat(value);

            } else if ("<".equals(operation)) {
                return value != null && fieldValue != null
                        && Float.parseFloat(fieldValue) < Float.parseFloat(value);
            }

            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Expr expr = (Expr) o;
            return Objects.equals(fieldName, expr.fieldName)
                    && Objects.equals(operation, expr.operation)
                    && Objects.equals(value, expr.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fieldName, operation, value);
        }

        @Override
        public String toString() {
            return "Expr{" +
                    "fieldName='" + fieldName + '\'' +
                    ", operation='" + operation + '\'' +
                    ", value='" + value + '\'' +
                    '}';
        }
    }
}
