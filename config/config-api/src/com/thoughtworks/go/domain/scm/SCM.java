/*************************GO-LICENSE-START*********************************
 * Copyright 2014 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *************************GO-LICENSE-END***********************************/

package com.thoughtworks.go.domain.scm;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.thoughtworks.go.config.*;
import com.thoughtworks.go.config.materials.AbstractMaterialConfig;
import com.thoughtworks.go.config.validation.NameTypeValidator;
import com.thoughtworks.go.domain.ConfigErrors;
import com.thoughtworks.go.domain.config.*;
import com.thoughtworks.go.plugin.access.scm.SCMConfiguration;
import com.thoughtworks.go.plugin.access.scm.SCMConfigurations;
import com.thoughtworks.go.plugin.access.scm.SCMMetadataStore;
import com.thoughtworks.go.plugin.api.config.Property;
import com.thoughtworks.go.util.CachedDigestUtils;
import com.thoughtworks.go.util.ListUtil;
import com.thoughtworks.go.util.StringUtil;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.util.*;

import static com.thoughtworks.go.util.ListUtil.join;
import static java.lang.String.format;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isEmpty;

@ConfigTag("scm")
@ConfigReferenceCollection(collectionName = "scms", idFieldName = "id")
public class SCM implements Serializable, Validatable {
    public static final String NAME = "name";
    public static final String SCM_ID = "scmId";
    public static final String PLUGIN_CONFIGURATION = "pluginConfiguration";
    private ConfigErrors errors = new ConfigErrors();

    @ConfigAttribute(value = "id", allowNull = true)
    private String id;

    @ConfigAttribute(value = "name", allowNull = false)
    private String name;

    @ConfigAttribute(value = "autoUpdate", optional = true)
    private boolean autoUpdate = true;

    @Expose
    @SerializedName("plugin")
    @ConfigSubtag
    private PluginConfiguration pluginConfiguration = new PluginConfiguration();

    @Expose
    @SerializedName("config")
    @ConfigSubtag
    private Configuration configuration = new Configuration();

    public SCM() {
    }

    public String getId() {
        return id;
    }

    //used in erb as it cannot access id attribute as it treats 'id' as keyword
    public String getSCMId() {
        return getId();
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isAutoUpdate() {
        return autoUpdate;
    }

    public void setAutoUpdate(boolean autoUpdate) {
        this.autoUpdate = autoUpdate;
    }

    public PluginConfiguration getPluginConfiguration() {
        return pluginConfiguration;
    }

    public void setPluginConfiguration(PluginConfiguration pluginConfiguration) {
        this.pluginConfiguration = pluginConfiguration;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SCM that = (SCM) o;

        if (id != null ? !id.equals(that.id) : that.id != null) {
            return false;
        }
        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (pluginConfiguration != null ? !pluginConfiguration.equals(that.pluginConfiguration) : that.pluginConfiguration != null) {
            return false;
        }
        if (configuration != null ? !configuration.equals(that.configuration) : that.configuration != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (pluginConfiguration != null ? pluginConfiguration.hashCode() : 0);
        result = 31 * result + (configuration != null ? configuration.hashCode() : 0);
        return result;
    }

    @Override
    public void validate(ValidationContext validationContext) {
        if (isBlank(name)) {
            errors().add(NAME, "Please provide name");
        } else if (!new NameTypeValidator().isNameValid(name)) {
            errors().add(NAME, NameTypeValidator.errorMessage("SCM", name));
        }
        configuration.validateUniqueness(String.format("SCM '%s'", name));
    }

    @Override
    public ConfigErrors errors() {
        return errors;
    }

    @Override
    public void addError(String fieldName, String message) {
        errors.add(fieldName, message);
    }

    public String getConfigForDisplay() {
        String pluginId = getPluginId();
        SCMMetadataStore metadataStore = SCMMetadataStore.getInstance();
        List<ConfigurationProperty> propertiesToBeUsedForDisplay = ConfigurationDisplayUtil.getConfigurationPropertiesToBeUsedForDisplay(metadataStore, pluginId, configuration);

        String prefix = metadataStore.hasPlugin(pluginId) ? "" : "WARNING! Plugin missing for ";
        return prefix + "SCM: " + configuration.forDisplay(propertiesToBeUsedForDisplay);
    }

    private String getPluginId() {
        return pluginConfiguration.getId();
    }

    @PostConstruct
    public void applyPluginMetadata() {
        String pluginId = getPluginId();
        for (ConfigurationProperty configurationProperty : configuration) {
            SCMMetadataStore scmMetadataStore = SCMMetadataStore.getInstance();
            if (scmMetadataStore.getConfigurationMetadata(pluginId) != null) {
                boolean isSecureProperty = scmMetadataStore.hasOption(pluginId, configurationProperty.getConfigurationKey().getName(), SCMConfiguration.SECURE);
                configurationProperty.handleSecureValueConfiguration(isSecureProperty);
            }
        }
    }

    public void setConfigAttributes(Object attributes) {
        Map attributesMap = (Map) attributes;
        if (attributesMap.containsKey(NAME)) {
            name = ((String) attributesMap.get(NAME));
        }
        if (attributesMap.containsKey(SCM_ID)) {
            id = ((String) attributesMap.get(SCM_ID));
        }
        if (attributesMap.containsKey(PLUGIN_CONFIGURATION)) {
            pluginConfiguration.setConfigAttributes(attributesMap.get(PLUGIN_CONFIGURATION));
        }
        if (attributesMap.containsKey(Configuration.CONFIGURATION)) {
            configuration.clear();
            configuration.setConfigAttributes(attributesMap.get(Configuration.CONFIGURATION), getSecureKeyInfoProvider());
        }
    }

    private SecureKeyInfoProvider getSecureKeyInfoProvider() {
        final SCMMetadataStore scmMetadataStore = SCMMetadataStore.getInstance();
        final SCMConfigurations metadata = scmMetadataStore.getConfigurationMetadata(getPluginId());
        if (metadata == null) {
            return null;
        }
        return new SecureKeyInfoProvider() {
            @Override
            public boolean isSecure(String key) {
                SCMConfiguration configuration = metadata.get(key);
                return configuration.getOption(SCMConfiguration.SECURE);
            }
        };
    }

    public String getFingerprint() {
        List<String> list = new ArrayList<String>();
        list.add(format("%s=%s", "plugin-id", getPluginId()));
        handleSCMProperties(list);
        String fingerprint = join(list, AbstractMaterialConfig.FINGERPRINT_DELIMITER);
        // CAREFUL! the hash algorithm has to be same as the one used in 47_create_new_materials.sql
        return CachedDigestUtils.sha256Hex(fingerprint);
    }

    private void handleSCMProperties(List<String> list) {
        SCMConfigurations metadata = SCMMetadataStore.getInstance().getConfigurationMetadata(getPluginId());
        for (ConfigurationProperty configurationProperty : configuration) {
            handleProperty(list, metadata, configurationProperty);
        }
    }

    private void handleProperty(List<String> list, SCMConfigurations metadata, ConfigurationProperty configurationProperty) {
        SCMConfiguration scmConfiguration = null;

        if (metadata != null) {
            scmConfiguration = metadata.get(configurationProperty.getConfigurationKey().getName());
        }

        if (scmConfiguration == null || scmConfiguration.getOption(SCMConfiguration.PART_OF_IDENTITY)) {
            list.add(configurationProperty.forFingerprint());
        }
    }

    public void addConfigurationErrorFor(String key, String message) {
        configuration.addErrorFor(key, message);
    }

    public boolean isNew() {
        return isEmpty(id);
    }

    public void clearEmptyConfigurations() {
        configuration.clearEmptyConfigurations();
    }

    @PostConstruct
    public void ensureIdExists() {
        if (StringUtil.isBlank(getId())) {
            setId(UUID.randomUUID().toString());
        }
    }
}
