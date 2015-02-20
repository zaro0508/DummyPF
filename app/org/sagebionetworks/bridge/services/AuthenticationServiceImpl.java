package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.dao.ParticipantOptionsDao.Option;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.StudyLimitExceededException;
import org.sagebionetworks.bridge.models.Email;
import org.sagebionetworks.bridge.models.EmailVerification;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.PasswordReset;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.validators.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.Validator;

public class AuthenticationServiceImpl implements AuthenticationService {

    private final Logger logger = LoggerFactory.getLogger(AuthenticationServiceImpl.class);
    
    private DistributedLockDao lockDao;
    private CacheProvider cacheProvider;
    private BridgeConfig config;
    private ConsentService consentService;
    private ParticipantOptionsService optionsService;
    private AccountDao accountDao;
    private HealthCodeService healthCodeService;
    private Validator signInValidator;
    private Validator signUpValidator;
    private Validator passwordResetValidator;

    public void setDistributedLockDao(DistributedLockDao lockDao) {
        this.lockDao = lockDao;
    }

    public void setCacheProvider(CacheProvider cache) {
        this.cacheProvider = cache;
    }

    public void setBridgeConfig(BridgeConfig config) {
        this.config = config;
    }

    public void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }

    public void setOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
    }
    
    public void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    public void setHealthCodeService(HealthCodeService healthCodeService) {
        this.healthCodeService = healthCodeService;
    }
    
    public void setSignInValidator(Validator validator) {
        this.signInValidator = validator;
    }

    public void setSignUpValidator(Validator validator) {
        this.signUpValidator = validator;
    }
    
    public void setPasswordResetValidator(Validator validator) {
        this.passwordResetValidator = validator;
    }

    @Override
    public UserSession getSession(String sessionToken) {
        if (sessionToken == null) {
            return null;
        }
        return cacheProvider.getUserSession(sessionToken);
    }

    @Override
    public UserSession signIn(Study study, SignIn signIn) throws ConsentRequiredException, EntityNotFoundException {
        checkNotNull(study, "Study cannot be null");
        checkNotNull(signIn, "Sign in cannot be null");

        Validate.entityThrowingException(signInValidator, signIn);

        Account account = accountDao.authenticate(study, signIn);
        UserSession session = getSessionFromAccount(study, account);
        cacheProvider.setUserSession(session.getSessionToken(), session);

        if (!session.getUser().doesConsent()) {
            throw new ConsentRequiredException(session);
        }

        return session;
    }

    @Override
    public void signOut(String sessionToken) {
        if (sessionToken != null) {
            cacheProvider.removeSession(sessionToken);
        }
    }

    @Override
    public void signUp(SignUp signUp, Study study, boolean sendEmail) {
        checkNotNull(study, "Study cannot be null");
        checkNotNull(signUp, "Sign up cannot be null");
        checkNotNull(signUp.getEmail(), "Sign up email cannot be null");

        String lockId = null;
        try {
            lockId = lockDao.acquireLock(SignUp.class, signUp.getEmail());

            Validate.entityThrowingException(signUpValidator, signUp);
            if (consentService.isStudyAtEnrollmentLimit(study)) {
                throw new StudyLimitExceededException(study);
            }
            accountDao.signUp(study, signUp, sendEmail);

        } finally {
            lockDao.releaseLock(SignUp.class, signUp.getEmail(), lockId);
        }
    }

    @Override
    public UserSession verifyEmail(Study study, EmailVerification verification) throws ConsentRequiredException {
        checkNotNull(verification, "Verification object cannot be null");
        checkNotNull(verification.getSptoken(), "Email verification token is required");

        Account account = accountDao.verifyEmail(study, verification);
        UserSession session = getSessionFromAccount(study, account);
        cacheProvider.setUserSession(session.getSessionToken(), session);

        if (!session.getUser().doesConsent()) {
            throw new ConsentRequiredException(session);
        }
        return session;
    }
    
    @Override
    public void resendEmailVerification(Email email) {
        checkNotNull(email, "Email object cannnot be null");
        checkNotNull(email.getEmail(), "Email is required");
        
        accountDao.resendEmailVerificationToken(email);
    }

    @Override
    public void requestResetPassword(Email email) throws BridgeServiceException {
        checkNotNull(email, "Email object cannot cannot be null");
        checkArgument(StringUtils.isNotBlank(email.getEmail()), "Email is required");

        accountDao.requestResetPassword(email);
    }

    @Override
    public void resetPassword(PasswordReset passwordReset) throws BridgeServiceException {
        checkNotNull(passwordReset, "Password reset object required");

        Validate.entityThrowingException(passwordResetValidator, passwordReset);
        
        accountDao.resetPassword(passwordReset);
    }

    private UserSession getSessionFromAccount(Study study, Account account) {
        final UserSession session = new UserSession();
        session.setAuthenticated(true);
        session.setEnvironment(config.getEnvironment().name().toLowerCase());
        session.setSessionToken(BridgeUtils.generateGuid());
        session.setStudyIdentifier(study.getStudyIdentifier());

        final User user = new User(account);
        user.setStudyKey(study.getIdentifier());

        final String healthCode = getHealthCode(study, account);
        if (healthCode != null) {
            user.setHealthCode(healthCode);
            user.setDataSharing(optionsService.getBooleanOption(healthCode, Option.DATA_SHARING));
        }
        user.setSignedMostRecentConsent(consentService.hasUserSignedMostRecentConsent(study, user));
        user.setConsent(consentService.hasUserConsentedToResearch(study, user));
        
        // And now for some exceptions...

        // All administrators and all researchers are assumed to consent when using any API.
        // This is needed so they can sign in without facing a 412 exception.
        if (user.isInRole(BridgeConstants.ADMIN_GROUP) || user.isInRole(study.getResearcherRole())) {
            user.setConsent(true);
        }
        // And then we set *any* account that has the admin email to be consented as well
        String adminUser = BridgeConfigFactory.getConfig().getProperty("admin.email");
        if (adminUser != null && adminUser.equals(account.getEmail())) {
            user.setConsent(true);
        }

        session.setUser(user);
        return session;
    }

    /**
     * Any user who authenticates has a health ID/code generated and assigned. It happens at authentication 
     * because some users are automatically marked as consented, which means we have these users accessing 
     * all the APIs that expect users to have health codes, which unknown consequences if they don't. We 
     * do not have to do it at sign up or when the user actually consents (interestingly enough). 
     * @param study
     * @param account
     * @return
     */
    private String getHealthCode(Study study, Account account) {
        HealthId healthId = healthCodeService.getMapping(account);
        if (healthId == null) {
            healthId = healthCodeService.createMapping(study);
            account.setHealthId(healthId.getId());
            accountDao.updateAccount(study, account);
            logger.debug("Health ID/code pair created for " + account.getEmail() + " in study " + study.getName());
        }
        return healthId.getCode();
    }
}
