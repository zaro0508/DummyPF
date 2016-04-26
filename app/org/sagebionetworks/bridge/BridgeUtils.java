package org.sagebionetworks.bridge;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.springframework.util.StringUtils.commaDelimitedListToSet;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.accounts.UserConsentHistory;

import org.springframework.core.annotation.AnnotationUtils;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.stormpath.sdk.group.Group;
import com.stormpath.sdk.group.GroupList;

public class BridgeUtils {
    
    public static final Joiner COMMA_SPACE_JOINER = Joiner.on(", ");

    public static final Joiner COMMA_JOINER = Joiner.on(",");
    
    /**
     * A simple means of providing template variables in template strings, in the format <code>${variableName}</code>.
     * This value will be replaced with the value of the variable name. The variable name/value pairs are passed to the
     * method as a map. Variables that are not found in the map will be left in the string as is. This includes
     * variables that are resolved by Stormpath when resolving templates for the emails sent by that system.
     * 
     * @see https://sagebionetworks.jira.com/wiki/display/BRIDGE/EmailTemplate
     * 
     * @param template
     * @param values
     * @return
     */
    public static String resolveTemplate(String template, Map<String,String> values) {
        checkNotNull(template);
        checkNotNull(values);
        
        for (Map.Entry<String,String> entry : values.entrySet()) {
            if (entry.getValue() != null) {
                String var = "${"+entry.getKey()+"}";
                template = template.replace(var, entry.getValue());
            }
        }
        return template;
    }
    
    public static String generateGuid() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Searches for a @BridgeTypeName annotation on this or any parent class in the class hierarchy, returning 
     * that value as the type name. If none exists, defaults to the simple class name. 
     * @param clazz
     * @return
     */
    public static String getTypeName(Class<?> clazz) {
        BridgeTypeName att = AnnotationUtils.findAnnotation(clazz,BridgeTypeName.class);
        if (att != null) {
            return att.value();
        }
        return clazz.getSimpleName();
    }
    
    /**
     * All batch methods in Dynamo return a list of failures rather than 
     * throwing an exception. We should have an exception specifically for 
     * these so the caller gets a list of items back, but for now, convert 
     * to a generic exception;
     * @param failures
     */
    public static void ifFailuresThrowException(List<FailedBatch> failures) {
        if (!failures.isEmpty()) {
            List<String> messages = Lists.newArrayList();
            for (FailedBatch failure : failures) {
                String message = failure.getException().getMessage();
                messages.add(message);
                String ids = Joiner.on("; ").join(failure.getUnprocessedItems().keySet());
                messages.add(ids);
            }
            throw new BridgeServiceException(Joiner.on(", ").join(messages));
        }
    }
    
    public static boolean isEmpty(Collection<?> coll) {
        return (coll == null || coll.isEmpty());
    }
    
    public static <S,T> Map<S,T> asMap(List<T> list, Function<T,S> function) {
        Map<S,T> map = Maps.newHashMap();
        if (list != null && function != null) {
            for (T item : list) {
                map.put(function.apply(item), item);
            }
        }
        return map;
    }
    
    public static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch(NumberFormatException e) {
            throw new RuntimeException("'" + value + "' is not a valid integer");
        }
    }
    
    public static void checkNewEntity(BridgeEntity entity, String field, String message) {
        if (StringUtils.isNotBlank(field)) {
            throw new EntityAlreadyExistsException(entity, message);
        }
    }
    
    public static void checkNewEntity(BridgeEntity entity, Long field, String message) {
        if (field != null) {
            throw new EntityAlreadyExistsException(entity, message);
        }
    }

    public static Set<Roles> convertRolesQuietly(GroupList groups) {
        Set<Roles> roleSet = new HashSet<>();
        if (groups != null) {
            for (Group group : groups) {
                roleSet.add(Roles.valueOf(group.getName().toUpperCase()));
            }
        }
        return roleSet;
    }
    
    public static Set<String> commaListToOrderedSet(String commaList) {
        if (commaList != null) {
            // This implementation must return a LinkedHashSet. This is a set
            // with ordered keys, in the order they were in the string, as some
            // set serializations depend on the order of the keys (languages).
            return commaDelimitedListToSet(commaList).stream()
                    .map(string -> string.trim())
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        // Cannot make this immutable without losing the concrete type we rely 
        // upon to ensure they keys are in the order they are inserted.
        return new LinkedHashSet<String>();
    }
    
    public static String setToCommaList(Set<String> set) {
        if (set != null) {
            // User LinkedHashSet because some supplied sets will have ordered keys 
            // and we want to preserve that order while processing the set. 
            Set<String> result = set.stream()
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return (result.isEmpty()) ? null : COMMA_JOINER.join(result);
        }
        return null;
    }
    
    /**
     * Wraps a set in an immutable set, or returns an empty immutable set if null.
     * @param set
     * @return
     */
    public @Nonnull static <T> ImmutableSet<T> nullSafeImmutableSet(Set<T> set) {
        return (set == null) ? ImmutableSet.of() : ImmutableSet.copyOf(set.stream()
                .filter(element -> element != null).collect(Collectors.toSet()));
    }
    
    public @Nonnull static <T> ImmutableList<T> nullSafeImmutableList(List<T> list) {
        return (list == null) ? ImmutableList.of() : ImmutableList.copyOf(list.stream()
                .filter(element -> element != null).collect(Collectors.toSet()));
    }
    
    public @Nonnull static <S,T> ImmutableMap<S,T> nullSafeImmutableMap(Map<S,T> map) {
        ImmutableMap.Builder<S, T> builder = new ImmutableMap.Builder<>();
        if (map != null) {
            for (S key : map.keySet()) {
                if (map.get(key) != null) {
                    builder.put(key, map.get(key));
                }
            }
        }
        return builder.build();
    }
    
    public static String getIdFromStormpathHref(String href) {
        if (href != null) {
            if (!href.contains(BridgeConstants.STORMPATH_ACCOUNT_BASE_HREF)) {
                throw new IllegalArgumentException("Invalid Stormpath URL: " + href);
            }
            return href.substring(BridgeConstants.STORMPATH_ACCOUNT_BASE_HREF.length());
        }
        return null;
    }

}
