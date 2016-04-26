package org.sagebionetworks.bridge.services;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EMAIL_NOTIFICATIONS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.LANGUAGES;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserConsentHistory;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.validators.StudyParticipantValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@Component
public class ParticipantService {

    private static final String PAGE_SIZE_ERROR = "pageSize must be from "+API_MINIMUM_PAGE_SIZE+"-"+API_MAXIMUM_PAGE_SIZE+" records";
    
    private static final List<UserConsentHistory> NO_HISTORY = ImmutableList.of();
    
    private AccountDao accountDao;
    
    private ParticipantOptionsService optionsService;
    
    private SubpopulationService subpopService;
    
    private HealthCodeService healthCodeService;
    
    private ConsentService consentService;
    
    private ExternalIdService externalIdService;
    
    private CacheProvider cacheProvider;
    
    @Autowired
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    
    @Autowired
    final void setParticipantOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
    }
    
    @Autowired
    final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    }
    
    @Autowired
    final void setHealthCodeService(HealthCodeService healthCodeService) {
        this.healthCodeService = healthCodeService;
    }
    
    @Autowired
    final void setUserConsent(ConsentService consentService) {
        this.consentService = consentService;
    }
    
    @Autowired
    final void setExternalIdService(ExternalIdService externalIdService) {
        this.externalIdService = externalIdService;
    }
    
    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    
    public StudyParticipant getParticipant(Study study, String id) {
        checkNotNull(study);
        checkArgument(isNotBlank(id));
        
        StudyParticipant.Builder participant = new StudyParticipant.Builder();
        Account account = getAccountThrowingException(study, id);
        String healthCode = getHealthCode(account);

        List<Subpopulation> subpopulations = subpopService.getSubpopulations(study.getStudyIdentifier());
        for (Subpopulation subpop : subpopulations) {
            if (healthCode != null) {
                // always returns a list, even if empty
                List<UserConsentHistory> history = consentService.getUserConsentHistory(study, subpop.getGuid(), healthCode, id);
                participant.addConsentHistory(subpop.getGuid(), history);
            } else {
                // Create an empty history if there's no health Code.
                participant.addConsentHistory(subpop.getGuid(), NO_HISTORY);
            }
        }
        // Accounts exist that have signatures but no health codes (when created but email is 
        // never verified, for example). Do not want roster generation to fail because of this,
        // so we just skip things that require the healthCode.
        if (healthCode != null) {
            ParticipantOptionsLookup lookup = optionsService.getOptions(healthCode);
            participant.withSharingScope(lookup.getEnum(SHARING_SCOPE, SharingScope.class));
            participant.withNotifyByEmail(lookup.getBoolean(EMAIL_NOTIFICATIONS));
            participant.withExternalId(lookup.getString(EXTERNAL_IDENTIFIER));
            participant.withDataGroups(lookup.getStringSet(DATA_GROUPS));
            participant.withLanguages(lookup.getOrderedStringSet(LANGUAGES));
        }
        participant.withFirstName(account.getFirstName());
        participant.withLastName(account.getLastName());
        participant.withEmail(account.getEmail());
        participant.withStatus(account.getStatus());
        participant.withCreatedOn(account.getCreatedOn());
        participant.withRoles(account.getRoles());
        participant.withId(account.getId());
        
        Map<String,String> attributes = Maps.newHashMap();
        for (String attribute : study.getUserProfileAttributes()) {
            String value = account.getAttribute(attribute);
            attributes.put(attribute, value);
        }
        participant.withAttributes(attributes);
        
        if (study.isHealthCodeExportEnabled()) {
            participant.withHealthCode(healthCode);
        }
        return participant.build();
    }
    
    public PagedResourceList<AccountSummary> getPagedAccountSummaries(Study study, int offsetBy, int pageSize, String emailFilter) {
        checkNotNull(study);
        if (offsetBy < 0) {
            throw new BadRequestException("offsetBy cannot be less than 0");
        }
        // Just set a sane upper limit on this.
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        return accountDao.getPagedAccountSummaries(study, offsetBy, pageSize, emailFilter);
    }
    
    public void signUserOut(Study study, String email) {
        checkNotNull(study);
        checkArgument(isNotBlank(email));
        
        Account account = getAccountThrowingException(study, email);
        cacheProvider.removeSessionByUserId(account.getId());
    }

    /**
     * Create a study participant. A password must be provided, even if it is added on behalf of a 
     * user before triggering a reset password request.  
     */
    public IdentifierHolder createParticipant(Study study, Set<Roles> callerRoles, StudyParticipant participant) {
        return saveParticipant(study, callerRoles, null, participant, true);
    }
    
    public void updateParticipant(Study study, Set<Roles> callerRoles, String id, StudyParticipant participant) {
        saveParticipant(study, callerRoles, id, participant, false);
    }

    public IdentifierHolder saveParticipant(Study study, Set<Roles> callerRoles, String id,
            StudyParticipant participant, boolean isNew) {
        checkNotNull(study);
        checkNotNull(callerRoles);
        checkArgument(isNew || isNotBlank(id));
        checkNotNull(participant);
        
        Validate.entityThrowingException(new StudyParticipantValidator(study, isNew), participant);
        Account account = null;
        String healthCode = null;
        if (isNew) {
            // Don't set it yet. Create the user first, and only assign it if that's successful.
            // Allows us to assure that credentials and ID will be related or not created at all.
            if (isNotBlank(participant.getExternalId())) {
                externalIdService.reserveExternalId(study, participant.getExternalId());    
            }
            
            SignUp signUp = new SignUp(participant.getEmail(), participant.getPassword(), null, null);
            account = accountDao.signUp(study, signUp, study.isEmailVerificationEnabled());
            
            healthCode = getHealthCodeThrowingException(account);
        } else {
            account = getAccountThrowingException(study, id);
            
            healthCode = getHealthCodeThrowingException(account);
            
            // Verify now that the assignment is valid, or throw an exception before any other updates
            addValidatedExternalId(study, participant, healthCode);
        }
        Map<ParticipantOption,String> options = Maps.newHashMap();
        for (ParticipantOption option : ParticipantOption.values()) {
            options.put(option, option.fromParticipant(participant));
        }
        // If we're validating the ID, we do this through the externalIdService, which writes to the participant options
        // table when its appropriate to do so
        if (study.isExternalIdValidationEnabled()) {
            options.remove(EXTERNAL_IDENTIFIER);
        }
        optionsService.setAllOptions(study.getStudyIdentifier(), healthCode, options);
        
        account.setFirstName(participant.getFirstName());
        account.setLastName(participant.getLastName());
        for(String attribute : study.getUserProfileAttributes()) {
            String value = participant.getAttributes().get(attribute);
            account.setAttribute(attribute, value);
        }
        
        // Only admin roles can change status, after participant is created
        if (!isNew && participant.getStatus() != null && isAdmin(callerRoles)) {
            account.setStatus(participant.getStatus());
        }
        updateRoles(callerRoles, participant, account);
        
        accountDao.updateAccount(study, account);
        
        if (isNew && isNotBlank(participant.getExternalId())) {
            externalIdService.assignExternalId(study, participant.getExternalId(), healthCode);
        }
        // Clear the user's session so that any changes are picked up by the app. New accounts don't have this issue.
        if (!isNew) {
            cacheProvider.removeSessionByUserId(account.getId());
        }
        return new IdentifierHolder(account.getId());
    }
    
    private boolean isAdmin(Set<Roles> roles) {
        return !Collections.disjoint(roles, Roles.ADMINISTRATIVE_ROLES);
    }
    
    private boolean callerHasAccessToRole(Set<Roles> callerRoles, Roles targetRole) {
        return !Collections.disjoint(callerRoles, Roles.CAN_BE_EDITED_BY.get(targetRole));
    }

    /**
     * First, user has to have the right to edit role (users can set a role, for example). Then for each role
     * being proposed to be added, we check to see the caller "has access" to that role. So for example, a 
     * developer cannot create an administrator, for security purposes.
     */
    private void updateRoles(Set<Roles> callerRoles, StudyParticipant participant, Account account) {
        // Is this user someone who can edit roles at all? Must have one of the admin roles.
        if (isAdmin(callerRoles)) {
            Set<Roles> newRoleSet = Sets.newHashSet();
            for (Roles role : participant.getRoles()) {
                if (callerHasAccessToRole(callerRoles, role)) {
                    newRoleSet.add(role);
                }
            }
            account.setRoles(newRoleSet);
        }
    }
    
    private void addValidatedExternalId(Study study, StudyParticipant participant, String healthCode) {
        // If not enabled, we'll update the value like any other ParticipantOption
        if (study.isExternalIdValidationEnabled()) {
            ParticipantOptionsLookup lookup = optionsService.getOptions(healthCode);
            String existingExternalId = lookup.getString(EXTERNAL_IDENTIFIER);
            
            if (idsDontExistOrAreNotEqual(existingExternalId, participant.getExternalId())) {
                if (isBlank(existingExternalId) && isNotBlank(participant.getExternalId())) {
                    externalIdService.assignExternalId(study, participant.getExternalId(), healthCode);
                } else {
                    throw new BadRequestException("External ID cannot be changed, removed after assignment, or left unassigned.");
                }
            }
        }
    }
    
    private boolean idsDontExistOrAreNotEqual(String id1, String id2) {
        return (isBlank(id1) || isBlank(id2) || !id1.equals(id2));
    }

    private Account getAccountThrowingException(Study study, String id) {
        Account account = accountDao.getAccount(study, id);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        return account;
    }
    
    private String getHealthCode(Account account) {
        if (account.getHealthId() != null) {
            HealthId healthId = healthCodeService.getMapping(account.getHealthId());
            if (healthId != null && healthId.getCode() != null) {
                return healthId.getCode();
            }
        }
        return null;
    }
    
    private String getHealthCodeThrowingException(Account account) {
        String healthCode = getHealthCode(account);
        if (healthCode == null) {
            throw new BridgeServiceException("Participant cannot be updated (no health code exists for user).");    
        }
        return healthCode;
    }
    
}
