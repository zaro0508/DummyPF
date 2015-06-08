package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeUtils.checkNewEntity;

import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.DirectoryDao;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.EmailTemplate.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsentForm;
import org.sagebionetworks.bridge.models.studies.StudyConsentView;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.validators.StudyValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("studyService")
public class StudyServiceImpl implements StudyService {

    private UploadCertificateService uploadCertService;
    private StudyDao studyDao;
    private DirectoryDao directoryDao;
    private DistributedLockDao lockDao;
    private StudyValidator validator;
    private CacheProvider cacheProvider;
    private StudyConsentService studyConsentService;

    private StudyConsentForm defaultConsentDocument;
    private String defaultEmailVerificationTemplate;
    private String defaultEmailVerificationTemplateSubject;
    private String defaultResetPasswordTemplate;
    private String defaultResetPasswordTemplateSubject;
    
    @Value("classpath:study-defaults/consent.xhtml")
    void setDefaultConsentDocument(org.springframework.core.io.Resource resource) {
        this.defaultConsentDocument = new StudyConsentForm(BridgeUtils.toStringQuietly(resource));
    }
    @Value("classpath:study-defaults/email-verification.txt")
    void setDefaultEmailVerificationTemplate(org.springframework.core.io.Resource resource) {
        this.defaultEmailVerificationTemplate = BridgeUtils.toStringQuietly(resource);
    }
    @Value("classpath:study-defaults/email-verification-subject.txt")
    void setDefaultEmailVerificationTemplateSubject(org.springframework.core.io.Resource resource) {
        this.defaultEmailVerificationTemplateSubject = BridgeUtils.toStringQuietly(resource);
    }
    @Value("classpath:study-defaults/reset-password.txt")
    void setDefaultPasswordTemplate(org.springframework.core.io.Resource resource) {
        this.defaultResetPasswordTemplate = BridgeUtils.toStringQuietly(resource);
    }
    @Value("classpath:study-defaults/reset-password-subject.txt")
    void setDefaultPasswordTemplateSubject(org.springframework.core.io.Resource resource) {
        this.defaultResetPasswordTemplateSubject = BridgeUtils.toStringQuietly(resource);
    }
    @Resource(name="uploadCertificateService")
    void setUploadCertificateService(UploadCertificateService uploadCertService) {
        this.uploadCertService = uploadCertService;
    }
    @Autowired
    void setDistributedLockDao(DistributedLockDao lockDao) {
        this.lockDao = lockDao;
    }
    @Autowired
    void setValidator(StudyValidator validator) {
        this.validator = validator;
    }
    @Autowired
    void setStudyDao(StudyDao studyDao) {
        this.studyDao = studyDao;
    }
    @Autowired
    void setDirectoryDao(DirectoryDao directoryDao) {
        this.directoryDao = directoryDao;
    }
    @Autowired
    void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    @Autowired
    void setStudyConsentService(StudyConsentService studyConsentService) {
        this.studyConsentService = studyConsentService;
    }
    
    @Override
    public Study getStudy(String identifier) {
        checkArgument(isNotBlank(identifier), Validate.CANNOT_BE_BLANK, "identifier");
        
        Study study = cacheProvider.getStudy(identifier);
        if (study == null) {
            study = studyDao.getStudy(identifier);
            cacheProvider.setStudy(study);
        }
        return study;
    }
    @Override
    public Study getStudy(StudyIdentifier studyId) {
        checkNotNull(studyId, Validate.CANNOT_BE_NULL, "studyIdentifier");
        
        return getStudy(studyId.getIdentifier());
    }
    @Override
    public List<Study> getStudies() {
        return studyDao.getStudies();
    }
    @Override
    public Study createStudy(Study study) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkNewEntity(study, study.getVersion(), "Study has a version value; it may already exist");

        setDefaultsIfAbsent(study);
        sanitizeHTML(study);
        Validate.entityThrowingException(validator, study);

        String id = study.getIdentifier();
        String lockId = null;
        try {
            lockId = lockDao.acquireLock(Study.class, id);

            if (studyDao.doesIdentifierExist(study.getIdentifier())) {
                throw new EntityAlreadyExistsException(study);
            }
            
            // The system is broken if the study does not have a consent. Create a default consent so the study is usable.
            StudyConsentView view = studyConsentService.addConsent(study.getStudyIdentifier(), defaultConsentDocument);
            studyConsentService.activateConsent(study.getStudyIdentifier(), view.getCreatedOn());
            
            study.setActive(true);
            study.setResearcherRole(study.getIdentifier() + "_researcher");

            String directory = directoryDao.createDirectoryForStudy(study);
            study.setStormpathHref(directory);
            uploadCertService.createCmsKeyPair(study.getIdentifier());
            study = studyDao.createStudy(study);
            
            cacheProvider.setStudy(study);
        } finally {
            lockDao.releaseLock(Study.class, id, lockId);
        }
        return study;
    }
    @Override
    public Study updateStudy(Study study) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        
        setDefaultsIfAbsent(study);
        sanitizeHTML(study);
        Validate.entityThrowingException(validator, study);

        // These cannot be set through the API and will be null here, so they are set on update
        Study originalStudy = studyDao.getStudy(study.getIdentifier());
        study.setStormpathHref(originalStudy.getStormpathHref());
        study.setResearcherRole(originalStudy.getResearcherRole());
        study.setActive(originalStudy.isActive());

        // When the version is out of sync in the cache, then an exception is thrown and the study 
        // is not updated in the cache. At least we can delete the study before this, so the next 
        // time it should succeed. Have not figured out why they get out of sync.
        cacheProvider.removeStudy(study.getIdentifier());
        
        // Only update the directory if a relevant aspect of the study has changed.
        if (studyDirectoryHasChanged(originalStudy, study)) {
            directoryDao.updateDirectoryForStudy(study);
        }
        Study updatedStudy = studyDao.updateStudy(study);
        
        cacheProvider.setStudy(updatedStudy);
        
        return updatedStudy;
    }
    @Override
    public void deleteStudy(String identifier) {
        checkArgument(isNotBlank(identifier), Validate.CANNOT_BE_BLANK, "identifier");

        String lockId = null;
        try {
            lockId = lockDao.acquireLock(Study.class, identifier);
            directoryDao.deleteDirectoryForStudy(identifier);
            studyDao.deleteStudy(identifier);
            cacheProvider.removeStudy(identifier);
        } finally {
            lockDao.releaseLock(Study.class, identifier, lockId);
        }
    }
    
    /**
     * Has an aspect of the study changed that must be mirrored to the Stormpath directory?
     * @param originalStudy
     * @param study
     * @return true if the password policy or email templates have changed
     */
    private boolean studyDirectoryHasChanged(Study originalStudy, Study study) {
        return (!study.getPasswordPolicy().equals(originalStudy.getPasswordPolicy()) || 
                !study.getVerifyEmailTemplate().equals(originalStudy.getVerifyEmailTemplate()) || 
                !study.getResetPasswordTemplate().equals(originalStudy.getResetPasswordTemplate()));
    }
    
    /**
     * When certain aspects of as study are excluded on a save, they revert to defaults. 
     * @param study
     */
    private void setDefaultsIfAbsent(Study study) {
        if (study.getPasswordPolicy() == null) {
            study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        }
        study.setVerifyEmailTemplate(fillOutTemplate(study.getVerifyEmailTemplate(),
            defaultEmailVerificationTemplateSubject, defaultEmailVerificationTemplate));
        study.setResetPasswordTemplate(fillOutTemplate(study.getResetPasswordTemplate(),
            defaultResetPasswordTemplateSubject, defaultResetPasswordTemplate));
    }

    /**
     * Partially resolve variables in the templates before saving on Stormpath. If templates are not provided, 
     * then the default templates are used, and we assume these are text only, otherwise this method needs to 
     * change the mime type it sets.
     * @param template
     * @param defaultSubject
     * @param defaultBody
     * @return
     */
    private EmailTemplate fillOutTemplate(EmailTemplate template, String defaultSubject, String defaultBody) {
        if (template == null) {
            template = new EmailTemplate(defaultSubject, defaultBody, MimeType.HTML);
        }
        if (StringUtils.isBlank(template.getSubject())) {
            template = new EmailTemplate(defaultSubject, template.getBody(), template.getMimeType());
        }
        if (StringUtils.isBlank(template.getBody())) {
            template = new EmailTemplate(template.getSubject(), defaultBody, template.getMimeType());
        }
        return template;
    }
    
    /**
     * Email templates can contain HTML. Ensure the subjects have no markup and that the markup in 
     * the body portion is safe for display in web-based email clients and a researcher UI. We clean 
     * this up before validation in case only unacceptable content was in the template. 
     * @param study
     */
    private void sanitizeHTML(Study study) {
        EmailTemplate template = study.getVerifyEmailTemplate();
        study.setVerifyEmailTemplate(sanitizeEmailTemplate(template));
        
        template = study.getResetPasswordTemplate();
        study.setResetPasswordTemplate(sanitizeEmailTemplate(template));
    }
    
    private EmailTemplate sanitizeEmailTemplate(EmailTemplate template) {
        String subject = Jsoup.clean(template.getSubject(), Whitelist.none());
        String body = null;
        if (template.getMimeType() == MimeType.TEXT) {
            body = Jsoup.clean(template.getBody(), Whitelist.none());
        } else {
            body = Jsoup.clean(template.getBody(), Whitelist.basicWithImages());
        }
        return new EmailTemplate(subject, body, template.getMimeType());
    }
}
