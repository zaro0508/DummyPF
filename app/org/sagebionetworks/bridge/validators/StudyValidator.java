package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.BridgeUtils.COMMA_SPACE_JOINER;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;

import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class StudyValidator implements Validator {
    public static final StudyValidator INSTANCE = new StudyValidator();
    private static final int MAX_SYNAPSE_LENGTH = 100;
    private static final String BRIDGE_IDENTIFIER_PATTERN = "^[a-z0-9-]+$";
    private static final String SYNAPSE_IDENTIFIER_PATTERN = "^[a-zA-Z0-9_-]+$";
    private static final String JS_IDENTIFIER_PATTERN = "^[a-zA-Z0-9_][a-zA-Z0-9_-]*$";
    /**
     * Dynamically inspect an instance of StudyParticipant with null fields included in the JSON
     * to get the field names that are reserved, and cannot be used for user profile attributes.
     */
    private static final Set<String> RESERVED_ATTR_NAMES = Sets
            .newHashSet(new ObjectMapper().valueToTree(new StudyParticipant.Builder().build()).fieldNames());
    
    @Override
    public boolean supports(Class<?> clazz) {
        return Study.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object obj, Errors errors) {
        Study study = (Study)obj;
        if (StringUtils.isBlank(study.getIdentifier())) {
            errors.rejectValue("identifier", "is required");
        } else {
            if (!study.getIdentifier().matches(BRIDGE_IDENTIFIER_PATTERN)) {
                errors.rejectValue("identifier", "must contain only lower-case letters and/or numbers with optional dashes");
            }
            if (study.getIdentifier().length() < 2) {
                errors.rejectValue("identifier", "must be at least 2 characters");
            }
        }
        if (StringUtils.isBlank(study.getName())) {
            errors.rejectValue("name", "is required");
        }
        if (StringUtils.isBlank(study.getSponsorName())) {
            errors.rejectValue("sponsorName", "is required");
        }
        if (StringUtils.isBlank(study.getSupportEmail())) {
            errors.rejectValue("supportEmail", "is required");
        }
        if (StringUtils.isBlank(study.getTechnicalEmail())) {
            errors.rejectValue("technicalEmail", "is required");
        }
        if (StringUtils.isBlank(study.getConsentNotificationEmail())) {
            errors.rejectValue("consentNotificationEmail", "is required");
        }
        // These *should* be set if they are null, with defaults
        if (study.getPasswordPolicy() == null) {
            errors.rejectValue("passwordPolicy", "is required");
        } else {
            errors.pushNestedPath("passwordPolicy");
            PasswordPolicy policy = study.getPasswordPolicy();
            if (!isInRange(policy.getMinLength(), 2)) {
                errors.rejectValue("minLength", "must be 2-"+PasswordPolicy.FIXED_MAX_LENGTH+" characters");
            }
            errors.popNestedPath();
        }
        if (study.getMinAgeOfConsent() < 0) {
            errors.rejectValue("minAgeOfConsent", "must be zero (no minimum age of consent) or higher");
        }
        if (study.getMaxNumOfParticipants() < 0) {
            errors.rejectValue("maxNumOfParticipants", "must be zero (no limit on enrollees) or higher");
        }
        validateTemplate(errors, study.getVerifyEmailTemplate(), "verifyEmailTemplate");
        validateTemplate(errors, study.getResetPasswordTemplate(), "resetPasswordTemplate");
        
        for (String userProfileAttribute : study.getUserProfileAttributes()) {
            if (RESERVED_ATTR_NAMES.contains(userProfileAttribute)) {
                String msg = String.format("'%s' conflicts with existing user profile property", userProfileAttribute);
                errors.rejectValue("userProfileAttributes", msg);
            }
            // Stormpath requires this to be a valid JavaScript identifier, despite the fact that it
            // is using JSON to store the data (where properties are string escaped). So don't allow invalid 
            // identifiers to be saved in study metadata.
            if (!userProfileAttribute.matches(JS_IDENTIFIER_PATTERN)) {
                String msg = String.format("'%s' must contain only digits, letters, underscores and dashes, and cannot start with a dash", userProfileAttribute);
                errors.rejectValue("userProfileAttributes", msg);
            }
        }
        validateEmails(errors, study.getSupportEmail(), "supportEmail");
        validateEmails(errors, study.getTechnicalEmail(), "technicalEmail");
        validateEmails(errors, study.getConsentNotificationEmail(), "consentNotificationEmail");
        validateDataGroupNamesAndFitForSynapseExport(errors, study.getDataGroups());
    }
    
    private boolean isInRange(int value, int min) {
        return (value >= min && value <= PasswordPolicy.FIXED_MAX_LENGTH);
    }
    
    private void validateEmails(Errors errors, String value, String fieldName) {
        Set<String> emails = BridgeUtils.commaListToOrderedSet(value);
        for (String email : emails) {
            if (!EmailValidator.getInstance().isValid(email)) {
                errors.rejectValue(fieldName, fieldName + " '%s' is not a valid email address", new Object[]{email}, null);
            }
        }
    }
    
    private void validateTemplate(Errors errors, EmailTemplate template, String fieldName) {
        if (template == null) {
            errors.rejectValue(fieldName, "is required");
        } else {
            errors.pushNestedPath(fieldName);
            if (StringUtils.isBlank(template.getSubject())) {
                errors.rejectValue("subject", "is required");
            }
            if (StringUtils.isBlank(template.getBody())) {
                errors.rejectValue("body", "is required");
            } else {
                if (!template.getBody().contains("${url}")) {
                    errors.rejectValue("body", "must contain the ${url} template variable");
                }
            }
            errors.popNestedPath();
        }
    }

    private void validateDataGroupNamesAndFitForSynapseExport(Errors errors, Set<String> dataGroups) {
        if (dataGroups != null) {
            for (String group : dataGroups) {
                if (!group.matches(SYNAPSE_IDENTIFIER_PATTERN)) {
                    errors.rejectValue("dataGroups", "contains invalid tag '"+group+"' (only letters, numbers, underscore and dash allowed)");
                }
            }
            String ser = COMMA_SPACE_JOINER.join(dataGroups);
            if (ser.length() > MAX_SYNAPSE_LENGTH) {
                errors.rejectValue("dataGroups", "will not export to Synapse (string is over "+MAX_SYNAPSE_LENGTH+" characters: '" + ser + "')");
            }
        }
    }

}
