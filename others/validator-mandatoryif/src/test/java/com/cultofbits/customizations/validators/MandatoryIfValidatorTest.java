package com.cultofbits.customizations.validators;

import com.cultofbits.recordm.core.model.Definition;
import com.cultofbits.recordm.core.model.FieldDefinition;
import com.cultofbits.recordm.core.model.Instance;
import com.cultofbits.recordm.core.model.InstanceField;
import com.cultofbits.recordm.customvalidators.api.ValidationError;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertEquals;

public class MandatoryIfValidatorTest {

    private final MandatoryIfValidator validator = new MandatoryIfValidator();


    @Test
    public void pass_validation_if_condition_fails() {
        Instance instance = anInstance(
            aField("User Type", "$[Robot,User]", "Robot"),
            aField("Address", "$mandatoryIf(User Type=User)", null));

        assertTrue(validator.validateInstanceFields(instance.getFields()).isEmpty());
    }

    @Test
    public void pass_validation_if_field_is_not_empty() {
        Instance instance = anInstance(
            aField("User Type", "$[Robot,User]", "Robot"),
            aField("Address", "$mandatoryIf(User Type=)", null));

        assertTrue(validator.validateInstanceFields(instance.getFields()).isEmpty());
    }

    @Test
    public void pass_validation_if_condition_true_and_field_has_value() {
        Instance instance = anInstance(
            aField("User Type", "$[Robot,User]", "User"),
            aField("Address", "$mandatoryIf(User Type=User)", "an address"));

        assertTrue(validator.validateInstanceFields(instance.getFields()).isEmpty());
    }

    @Test
    public void fail_validation_when_condition_is_true() {
        Instance instance = anInstance(
            aField("User Type", "$[Robot,User]", "User"),
            aField("Address", "$mandatoryIf(User Type=User)", null));

        assertTrue(validator.validateInstanceFields(instance.getFields()).size() > 0);
    }

    @Test
    public void fail_validation_when_target_field_is_not_empty() {
        Instance instance = anInstance(
            aField("User Type", "$[Robot,User]", "User"),
            aField("Address", "$mandatoryIf(User Type!=)", null));

        assertTrue(validator.validateInstanceFields(instance.getFields()).size() > 0);
    }

    @Test
    public void fail_validation_if_no_condition_and_value_is_null() {
        Instance instance = anInstance(
            aField("Address", "$mandatoryIf", null));

        assertTrue(validator.validateInstanceFields(instance.getFields()).size() > 0);
    }

    @Test
    public void fail_validation_if_range_condition_is_false() {
        Instance instance = anInstance(
            aField("Distance", "$number", "0"),
            aField("Message", "$mandatoryIf(Distance > 10)", null));

        assertTrue(validator.validateInstanceFields(instance.getFields()).isEmpty());
    }

    @Test
    public void fail_validation_if_range_condition_is_true() {
        Instance instance = anInstance(
            aField("Distance", "$number", "0"),
            aField("Message", "$mandatoryIf(Distance < 10)", null));

        assertTrue(validator.validateInstanceFields(instance.getFields()).size() > 0);
    }

    @Test
    public void pass_validation_if_not_greater_than() {
        Instance instance = anInstance(
            aField("Number", "$[1,2,3,4,5]", null),
            aField("Message", "$mandatoryIf(Number < 2)", null));

        assertTrue(validator.validateInstanceFields(instance.getFields()).isEmpty());
    }

    @Test
    public void can_validate_instances_with_groups() {
        InstanceField field1 = aField("User Type", "$[Robot,User]", "User");
        InstanceField field2 = aField("Address", "$mandatoryIf(User Type!=)", null);

        InstanceField groupField = aField("Group", "$group", "3");
        groupField.children = Arrays.asList(field1, field2);

        Instance instance = anInstance(groupField, field1, field2);

        assertTrue(validator.validateInstanceFields(instance.getFields()).size() > 0);
    }

    @Test
    public void can_validate_instances_with_children() {
        InstanceField usernameField = aField("username", "$mandatoryIf(User Type=User)", null);
        InstanceField addressField = aField("Address", "$mandatoryIf(User Type!=)", null);

        InstanceField parentField = aField("User Type", "$[Robot,User]", "User");
        parentField.children = Arrays.asList(usernameField, addressField);

        Instance instance = anInstance(parentField, usernameField, addressField);

        assertTrue(validator.validateInstanceFields(instance.getFields()).size() > 0);
    }

    @Test
    public void can_validate_instances_with_children_but_fail_condition() {
        InstanceField usernameField = aField("username", "$mandatoryIf(User Type=User)", null);

        InstanceField parentField = aField("User Type", "$[Robot,User]", "Robot");
        parentField.children = Collections.singletonList(usernameField);

        Instance instance = anInstance(parentField, usernameField);

        assertTrue(validator.validateInstanceFields(instance.getFields()).isEmpty());
    }

    @Test
    public void can_validate_instances_with_parent_with_null_value_and_fail_condition() {
        InstanceField usernameField = aField("username", "$mandatoryIf(User Type=User)", null);

        InstanceField parentField = aField("User Type", "$[Robot,User]", null);
        parentField.children = Collections.singletonList(usernameField);

        Instance instance = anInstance(parentField, usernameField);

        assertTrue(validator.validateInstanceFields(instance.getFields()).isEmpty());
    }

    @Test
    public void can_compare_instances_with_strings() {
        InstanceField stateField = aField("state", null, "1. teste");
        InstanceField validatedField = aField("validated", "$mandatoryIf(state>=0)", null);

        Instance instance = anInstance(stateField, validatedField);

        assertTrue(validator.validateInstanceFields(instance.getFields()).size() > 0);
    }

    @Test
    public void can_compare_instances_with_strings_alphabetically() {
        InstanceField stateField = aField("state", null, "1. teste");
        InstanceField validatedField = aField("validated", "$mandatoryIf(state<=1. teste)", null);

        Instance instance = anInstance(stateField, validatedField);

        assertTrue(validator.validateInstanceFields(instance.getFields()).size() > 0);
    }

    @Test
    public void supports_multiple_values() {
        InstanceField stateField = aField("state", null, "mau 1");
        InstanceField validatedField = aField("validated", "$mandatoryIf(state = mau 1\\, mau 2)", null);

        Instance instance = anInstance(stateField, validatedField);

        assertTrue(validator.validateInstanceFields(instance.getFields()).size() > 0);
    }

    @Test
    public void supports_different_from_multiple_values() {
        InstanceField stateField = aField("state", null, "mau 3");
        InstanceField validatedField = aField("validated", "$mandatoryIf(state != \\,mau 1\\, mau 2)", null);

        Instance instance = anInstance(stateField, validatedField);

        assertTrue(validator.validateInstanceFields(instance.getFields()).size() > 0);
    }

    @Test
    public void supports_different_from_multiple_values_with_blank() {
        InstanceField stateField = aField("state", null, "");
        InstanceField validatedField = aField("validated", "$mandatoryIf(state != \\,mau 1\\, mau 2)", null);

        Instance instance = anInstance(stateField, validatedField);

        assertTrue(validator.validateInstanceFields(instance.getFields()).size() == 0);
    }

    public static Instance anInstance(InstanceField... fields) {
        Instance instance = new Instance();
        instance.definition = new Definition(1, "A Definition");

        instance.fields = Arrays.asList(fields);
        instance.definition.setFieldDefinitions(new ArrayList<>(
            instance.fields.stream()
                .peek(f -> f.instance = instance)
                .map(f -> f.fieldDefinition)
                .peek(fd -> fd.definition = instance.definition)
                .collect(Collectors.toSet())
        ));

        return instance;
    }

    public static InstanceField aField(String name, String description, String value) {
        FieldDefinition fieldDefinition = new FieldDefinition(null, name, null, description, null);
        InstanceField instanceField = new InstanceField();
        instanceField.fieldDefinition = fieldDefinition;
        instanceField.setValue(value);

        return instanceField;
    }

}