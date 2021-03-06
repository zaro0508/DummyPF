package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.cache.ViewCache.ViewCacheKey;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.ExternalIdService;
import org.sagebionetworks.bridge.services.ParticipantService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Supplier;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import play.mvc.Result;

@Controller
public class UserProfileController extends BaseController {
    
    private static final String FIRST_NAME_FIELD = "firstName";
    private static final String LAST_NAME_FIELD = "lastName";
    private static final String EMAIL_FIELD = "email";
    private static final String USERNAME_FIELD = "username";
    private static final String TYPE_FIELD = "type";
    private static final String TYPE_VALUE = "UserProfile";
    private static final Set<Roles> NO_ROLES = Collections.emptySet();
    private static final Set<String> DATA_GROUPS_SET = Sets.newHashSet("dataGroups");

    private ParticipantService participantService;
    
    private ConsentService consentService;
    
    private ExternalIdService externalIdService;
    
    private ViewCache viewCache;

    @Autowired
    public final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    @Autowired
    public final void setViewCache(ViewCache viewCache) {
        this.viewCache = viewCache;
    }
    @Autowired
    public final void setExternalIdService(ExternalIdService externalIdService) {
        this.externalIdService = externalIdService;
    }
    @Autowired
    public final void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }

    public Result getUserProfile() throws Exception {
        final UserSession session = getAuthenticatedSession();
        final Study study = studyService.getStudy(session.getStudyIdentifier());
        final String userId = session.getUser().getId();
        
        ViewCacheKey<ObjectNode> cacheKey = viewCache.getCacheKey(ObjectNode.class, userId, study.getIdentifier());
        String json = viewCache.getView(cacheKey, new Supplier<ObjectNode>() {
            @Override public ObjectNode get() {
                StudyParticipant participant = participantService.getParticipant(study, NO_ROLES, userId);
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                node.put(FIRST_NAME_FIELD, participant.getFirstName());
                node.put(LAST_NAME_FIELD, participant.getLastName());
                node.put(EMAIL_FIELD, participant.getEmail());
                node.put(USERNAME_FIELD, participant.getEmail());
                node.put(TYPE_FIELD, TYPE_VALUE);
                for (Map.Entry<String,String> entry : participant.getAttributes().entrySet()) {
                    node.put(entry.getKey(), entry.getValue());    
                }
                return node;
            }
        });
        return ok(json).as(BridgeConstants.JSON_MIME_TYPE);
    }

    public Result updateUserProfile() throws Exception {
        UserSession session = getAuthenticatedSession();
        Study study = studyService.getStudy(session.getStudyIdentifier());
        String userId = session.getUser().getId();
        
        JsonNode node = requestToJSON(request());
        Map<String,String> attributes = Maps.newHashMap();
        for (String attrKey : study.getUserProfileAttributes()) {
            if (node.has(attrKey)) {
                attributes.put(attrKey, node.get(attrKey).asText());
            }
        }
        
        StudyParticipant participant = participantService.getParticipant(study, NO_ROLES, userId);
        
        StudyParticipant updated = new StudyParticipant.Builder().copyOf(participant)
                .withFirstName(JsonUtils.asText(node, "firstName"))
                .withLastName(JsonUtils.asText(node, "lastName"))
                .withAttributes(attributes).build();
        participantService.updateParticipant(study, NO_ROLES, userId, updated);
        
        updateSessionUser(session, session.getUser());
        
        ViewCacheKey<ObjectNode> cacheKey = viewCache.getCacheKey(ObjectNode.class, userId, study.getIdentifier());
        viewCache.removeView(cacheKey);
        
        return okResult("Profile updated.");
    }
    
    public Result createExternalIdentifier() throws Exception {
        UserSession session = getAuthenticatedSession();
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        ExternalIdentifier externalId = parseJson(request(), ExternalIdentifier.class);

        externalIdService.assignExternalId(study, externalId.getIdentifier(), session.getUser().getHealthCode());
        
        return okResult("External identifier added to user profile.");
    }
    
    public Result getDataGroups() throws Exception {
        UserSession session = getAuthenticatedSession();
        
        Set<String> dataGroups = optionsService.getOptions(
                session.getUser().getHealthCode()).getStringSet(DATA_GROUPS);
        
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        dataGroups.stream().forEach(array::add);

        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.set("dataGroups", array);
        node.put(TYPE_FIELD, "DataGroups");

        return ok(node).as(BridgeConstants.JSON_MIME_TYPE);
    }
    
    public Result updateDataGroups() throws Exception {
        UserSession session = getAuthenticatedSession();
        Study study = studyService.getStudy(session.getStudyIdentifier());
        User user = session.getUser();
        String userId = user.getId();
        
        StudyParticipant dataGroups = parseJson(request(), StudyParticipant.class);
        
        StudyParticipant participant = participantService.getParticipant(study, NO_ROLES, userId);
        
        StudyParticipant updated = new StudyParticipant.Builder().copyOf(participant)
                .copyFieldsOf(dataGroups, DATA_GROUPS_SET).build();
        
        participantService.updateParticipant(study, NO_ROLES, userId, updated);
        user.setDataGroups(updated.getDataGroups());
        
        CriteriaContext context = getCriteriaContext(session);
        Map<SubpopulationGuid,ConsentStatus> statuses = consentService.getConsentStatuses(context);
        user.setConsentStatuses(statuses);
                
        updateSessionUser(session, user);
        return okResult("Data groups updated.");
    }
    
}
