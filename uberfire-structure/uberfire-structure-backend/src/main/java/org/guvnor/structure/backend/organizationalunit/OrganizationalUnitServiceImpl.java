/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.guvnor.structure.backend.organizationalunit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.guvnor.structure.backend.backcompat.BackwardCompatibleUtil;
import org.guvnor.structure.config.SystemRepositoryChangedEvent;
import org.guvnor.structure.organizationalunit.NewOrganizationalUnitEvent;
import org.guvnor.structure.organizationalunit.OrganizationalUnit;
import org.guvnor.structure.organizationalunit.OrganizationalUnitService;
import org.guvnor.structure.organizationalunit.RemoveOrganizationalUnitEvent;
import org.guvnor.structure.organizationalunit.RepoAddedToOrganizationalUnitEvent;
import org.guvnor.structure.organizationalunit.RepoRemovedFromOrganizationalUnitEvent;
import org.guvnor.structure.organizationalunit.UpdatedOrganizationalUnitEvent;
import org.guvnor.structure.repositories.Repository;
import org.guvnor.structure.repositories.RepositoryEnvironmentUpdatedEvent;
import org.guvnor.structure.repositories.RepositoryService;
import org.guvnor.structure.server.config.ConfigGroup;
import org.guvnor.structure.server.config.ConfigItem;
import org.guvnor.structure.server.config.ConfigType;
import org.guvnor.structure.server.config.ConfigurationFactory;
import org.guvnor.structure.server.config.ConfigurationService;
import org.guvnor.structure.server.organizationalunit.OrganizationalUnitFactory;
import org.jboss.errai.bus.server.annotations.Service;
import org.uberfire.rpc.SessionInfo;
import org.uberfire.security.authz.AuthorizationManager;
import org.uberfire.spaces.Space;
import org.uberfire.spaces.SpacesAPI;

@Service
@ApplicationScoped
public class OrganizationalUnitServiceImpl implements OrganizationalUnitService {

    private ConfigurationService configurationService;

    private ConfigurationFactory configurationFactory;

    private OrganizationalUnitFactory organizationalUnitFactory;

    private BackwardCompatibleUtil backward;

    private Event<NewOrganizationalUnitEvent> newOrganizationalUnitEvent;

    private Event<RemoveOrganizationalUnitEvent> removeOrganizationalUnitEvent;

    private Event<RepoAddedToOrganizationalUnitEvent> repoAddedToOrgUnitEvent;

    private Event<RepoRemovedFromOrganizationalUnitEvent> repoRemovedFromOrgUnitEvent;

    private Event<UpdatedOrganizationalUnitEvent> updatedOrganizationalUnitEvent;

    private AuthorizationManager authorizationManager;

    private SessionInfo sessionInfo;

    Map<String, OrganizationalUnit> registeredOrganizationalUnits = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    private SpacesAPI spaces;

    private RepositoryService repositoryService;

    public OrganizationalUnitServiceImpl() {
    }

    @Inject
    public OrganizationalUnitServiceImpl(final ConfigurationService configurationService,
                                         final ConfigurationFactory configurationFactory,
                                         final OrganizationalUnitFactory organizationalUnitFactory,
                                         final RepositoryService repositoryService,
                                         final BackwardCompatibleUtil backward,
                                         final Event<NewOrganizationalUnitEvent> newOrganizationalUnitEvent,
                                         final Event<RemoveOrganizationalUnitEvent> removeOrganizationalUnitEvent,
                                         final Event<RepoAddedToOrganizationalUnitEvent> repoAddedToOrgUnitEvent,
                                         final Event<RepoRemovedFromOrganizationalUnitEvent> repoRemovedFromOrgUnitEvent,
                                         final Event<UpdatedOrganizationalUnitEvent> updatedOrganizationalUnitEvent,
                                         final AuthorizationManager authorizationManager,
                                         final SpacesAPI spaces,
                                         final SessionInfo sessionInfo) {
        this.configurationService = configurationService;
        this.configurationFactory = configurationFactory;
        this.organizationalUnitFactory = organizationalUnitFactory;
        this.repositoryService = repositoryService;
        this.backward = backward;
        this.newOrganizationalUnitEvent = newOrganizationalUnitEvent;
        this.removeOrganizationalUnitEvent = removeOrganizationalUnitEvent;
        this.repoAddedToOrgUnitEvent = repoAddedToOrgUnitEvent;
        this.repoRemovedFromOrgUnitEvent = repoRemovedFromOrgUnitEvent;
        this.updatedOrganizationalUnitEvent = updatedOrganizationalUnitEvent;
        this.authorizationManager = authorizationManager;
        this.spaces = spaces;
        this.sessionInfo = sessionInfo;
    }

    @PostConstruct
    public void loadOrganizationalUnits() {
        Collection<ConfigGroup> groups = configurationService.getConfiguration(ConfigType.SPACE);
        if (groups != null) {
            for (ConfigGroup groupConfig : groups) {
                // Make sure existing Organizational Units are correctly initialized with a default group id.
                String ouName = groupConfig.getName();
                String defaultGroupId = groupConfig.getConfigItemValue("defaultGroupId");
                if (defaultGroupId == null || defaultGroupId.trim().isEmpty()) {
                    groupConfig.setConfigItem(configurationFactory.newConfigItem("defaultGroupId",
                                                                                 getSanitizedDefaultGroupId(ouName)));
                    configurationService.updateConfiguration(groupConfig);
                }

                OrganizationalUnit ou = organizationalUnitFactory.newOrganizationalUnit(groupConfig);
                registeredOrganizationalUnits.put(ou.getName(),
                                                  ou);
            }
        }
    }

    @Override
    public OrganizationalUnit getOrganizationalUnit(final String name) {
        return registeredOrganizationalUnits.get(name);
    }

    @Override
    public Collection<OrganizationalUnit> getAllOrganizationalUnits() {
        return new ArrayList<>(registeredOrganizationalUnits.values());
    }

    @Override
    public Collection<Space> getAllUserSpaces() {
        return registeredOrganizationalUnits
                .values()
                .stream()
                .map(ou -> spaces.getSpace(ou.getName()))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<OrganizationalUnit> getOrganizationalUnits() {
        ArrayList result = new ArrayList<>();
        for (OrganizationalUnit ou : registeredOrganizationalUnits.values()) {
            if (authorizationManager.authorize(ou,
                                               sessionInfo.getIdentity())) {
                result.add(ou);
            }
        }
        return result;
    }

    @Override
    public OrganizationalUnit createOrganizationalUnit(final String name,
                                                       final String owner,
                                                       final String defaultGroupId) {

        return createOrganizationalUnit(name,
                                        owner,
                                        defaultGroupId,
                                        new ArrayList<>());
    }

    @Override
    public OrganizationalUnit createOrganizationalUnit(final String name,
                                                       final String owner,
                                                       final String defaultGroupId,
                                                       final Collection<Repository> repositories) {

        return createOrganizationalUnit(name,
                                        owner,
                                        defaultGroupId,
                                        repositories,
                                        new ArrayList<>());
    }

    @Override
    public OrganizationalUnit createOrganizationalUnit(final String name,
                                                       final String owner,
                                                       final String defaultGroupId,
                                                       final Collection<Repository> repositories,
                                                       final Collection<String> contributors) {
        if (registeredOrganizationalUnits.containsKey(name)) {
            return null;
        }

        OrganizationalUnit newOrganizationalUnit = null;

        try {
            configurationService.startBatch();
            final ConfigGroup groupConfig = configurationFactory.newConfigGroup(ConfigType.SPACE,
                                                                                name,
                                                                                "");
            groupConfig.addConfigItem(configurationFactory.newConfigItem("owner",
                                                                         owner));
            String _defaultGroupId = defaultGroupId == null || defaultGroupId.trim().isEmpty() ? getSanitizedDefaultGroupId(name) : defaultGroupId;
            groupConfig.addConfigItem(configurationFactory.newConfigItem("defaultGroupId",
                                                                         _defaultGroupId));
            groupConfig.addConfigItem(configurationFactory.newConfigItem("repositories",
                                                                         getRepositoryAliases(repositories)));
            groupConfig.addConfigItem(configurationFactory.newConfigItem("security:groups",
                                                                         new ArrayList<String>()));
            groupConfig.addConfigItem(configurationFactory.newConfigItem("contributors",
                                                                         contributors));
            configurationService.addConfiguration(groupConfig);

            newOrganizationalUnit = organizationalUnitFactory.newOrganizationalUnit(groupConfig);
            registeredOrganizationalUnits.put(newOrganizationalUnit.getName(),
                                              newOrganizationalUnit);

            return newOrganizationalUnit;
        } finally {
            configurationService.endBatch();
            if (newOrganizationalUnit != null) {
                newOrganizationalUnitEvent.fire(new NewOrganizationalUnitEvent(newOrganizationalUnit,
                                                                               getUserInfo(sessionInfo)));
            }
        }
    }

    private List<String> getRepositoryAliases(final Collection<Repository> repositories) {
        final List<String> repositoryList = new ArrayList<>();
        for (Repository repo : repositories) {
            repositoryList.add(repo.getAlias());
        }
        return repositoryList;
    }

    @Override
    public OrganizationalUnit updateOrganizationalUnit(final String name,
                                                       final String owner,
                                                       final String defaultGroupId) {
        return updateOrganizationalUnit(name,
                                        owner,
                                        defaultGroupId,
                                        null);
    }

    @Override
    public OrganizationalUnit updateOrganizationalUnit(String name,
                                                       String owner,
                                                       String defaultGroupId,
                                                       Collection<String> contributors) {
        final ConfigGroup thisGroupConfig = findGroupConfig(name);

        if (thisGroupConfig != null) {
            OrganizationalUnit updatedOrganizationalUnit = null;
            try {
                configurationService.startBatch();
                thisGroupConfig.setConfigItem(configurationFactory.newConfigItem("owner",
                                                                                 owner));
                // As per loadOrganizationalUnits(), all Organizational Units should have the default group id value set
                String _defaultGroupId = defaultGroupId == null || defaultGroupId.trim().isEmpty() ?
                        thisGroupConfig.getConfigItemValue("defaultGroupId") : defaultGroupId;
                thisGroupConfig.setConfigItem(configurationFactory.newConfigItem("defaultGroupId",
                                                                                 _defaultGroupId));

                if (contributors != null) {
                    thisGroupConfig.setConfigItem(configurationFactory.newConfigItem("contributors",
                                                                                     contributors));
                }

                configurationService.updateConfiguration(thisGroupConfig);

                updatedOrganizationalUnit = organizationalUnitFactory.newOrganizationalUnit(thisGroupConfig);
                registeredOrganizationalUnits.put(updatedOrganizationalUnit.getName(),
                                                  updatedOrganizationalUnit);

                return updatedOrganizationalUnit;
            } finally {
                configurationService.endBatch();
                if (updatedOrganizationalUnit != null) {
                    updatedOrganizationalUnitEvent.fire(new UpdatedOrganizationalUnitEvent(updatedOrganizationalUnit,
                                                                                           getUserInfo(sessionInfo)));
                }
            }
        } else {
            throw new IllegalArgumentException("OrganizationalUnit " + name + " not found");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void addRepository(final OrganizationalUnit organizationalUnit,
                              final Repository repository) {
        final ConfigGroup thisGroupConfig = findGroupConfig(organizationalUnit.getName());

        if (thisGroupConfig != null) {
            try {
                configurationService.startBatch();
                ConfigItem<List> repositories = thisGroupConfig.getConfigItem("repositories");
                repositories.getValue().add(repository.getAlias());

                configurationService.updateConfiguration(thisGroupConfig);

                final OrganizationalUnit updatedOrganizationalUnit = organizationalUnitFactory.newOrganizationalUnit(thisGroupConfig);
                registeredOrganizationalUnits.put(updatedOrganizationalUnit.getName(),
                                                  updatedOrganizationalUnit);
            } finally {
                configurationService.endBatch();
                repoAddedToOrgUnitEvent.fire(new RepoAddedToOrganizationalUnitEvent(organizationalUnit,
                                                                                    repository,
                                                                                    getUserInfo(sessionInfo)));
            }
        } else {
            throw new IllegalArgumentException("OrganizationalUnit " + organizationalUnit.getName() + " not found");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void removeRepository(final OrganizationalUnit organizationalUnit,
                                 final Repository repository) {
        final ConfigGroup thisGroupConfig = findGroupConfig(organizationalUnit.getName());

        if (thisGroupConfig != null) {
            try {
                configurationService.startBatch();
                final ConfigItem<List> repositories = thisGroupConfig.getConfigItem("repositories");
                repositories.getValue().remove(repository.getAlias());

                configurationService.updateConfiguration(thisGroupConfig);

                final OrganizationalUnit updatedOrganizationalUnit = organizationalUnitFactory.newOrganizationalUnit(thisGroupConfig);
                registeredOrganizationalUnits.put(updatedOrganizationalUnit.getName(),
                                                  updatedOrganizationalUnit);
            } finally {
                configurationService.endBatch();
                repoRemovedFromOrgUnitEvent.fire(new RepoRemovedFromOrganizationalUnitEvent(organizationalUnit,
                                                                                            repository,
                                                                                            getUserInfo(sessionInfo)));
            }
        } else {
            throw new IllegalArgumentException("OrganizationalUnit " + organizationalUnit.getName() + " not found");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void addGroup(final OrganizationalUnit organizationalUnit,
                         final String group) {
        final ConfigGroup thisGroupConfig = findGroupConfig(organizationalUnit.getName());

        if (thisGroupConfig != null) {
            OrganizationalUnit updatedOrganizationalUnit = null;
            try {
                configurationService.startBatch();
                final ConfigItem<List> groups = backward.compat(thisGroupConfig).getConfigItem("security:groups");
                groups.getValue().add(group);

                configurationService.updateConfiguration(thisGroupConfig);

                updatedOrganizationalUnit = organizationalUnitFactory.newOrganizationalUnit(thisGroupConfig);
                registeredOrganizationalUnits.put(updatedOrganizationalUnit.getName(),
                                                  updatedOrganizationalUnit);
            } finally {
                configurationService.endBatch();
                if (updatedOrganizationalUnit != null) {
                    updatedOrganizationalUnitEvent.fire(new UpdatedOrganizationalUnitEvent(updatedOrganizationalUnit,
                                                                                           getUserInfo(sessionInfo)));
                }
            }
        } else {
            throw new IllegalArgumentException("OrganizationalUnit " + organizationalUnit.getName() + " not found");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void removeGroup(final OrganizationalUnit organizationalUnit,
                            final String group) {
        final ConfigGroup thisGroupConfig = findGroupConfig(organizationalUnit.getName());

        if (thisGroupConfig != null) {
            OrganizationalUnit updatedOrganizationalUnit = null;
            try {
                configurationService.startBatch();
                final ConfigItem<List> groups = backward.compat(thisGroupConfig).getConfigItem("security:groups");
                groups.getValue().remove(group);

                configurationService.updateConfiguration(thisGroupConfig);

                updatedOrganizationalUnit = organizationalUnitFactory.newOrganizationalUnit(thisGroupConfig);
                registeredOrganizationalUnits.put(updatedOrganizationalUnit.getName(),
                                                  updatedOrganizationalUnit);
            } finally {
                configurationService.endBatch();
                if (updatedOrganizationalUnit != null) {
                    updatedOrganizationalUnitEvent.fire(new UpdatedOrganizationalUnitEvent(updatedOrganizationalUnit,
                                                                                           getUserInfo(sessionInfo)));
                }
            }
        } else {
            throw new IllegalArgumentException("OrganizationalUnit " + organizationalUnit.getName() + " not found");
        }
    }

    protected ConfigGroup findGroupConfig(final String name) {
        final Collection<ConfigGroup> groups = configurationService.getConfiguration(ConfigType.SPACE);
        if (groups != null) {
            for (ConfigGroup groupConfig : groups) {
                if (groupConfig.getName().equals(name)) {
                    return groupConfig;
                }
            }
        }
        return null;
    }

    @Override
    public void removeOrganizationalUnit(String groupName) {
        final ConfigGroup thisGroupConfig = findGroupConfig(groupName);

        if (thisGroupConfig != null) {
            OrganizationalUnit removedOu = null;
            try {
                configurationService.startBatch();
                final OrganizationalUnit originalOu = getOrganizationalUnit(groupName);
                repositoryService.removeRepositories(originalOu.getSpace(), originalOu.getRepositories().stream().map(repo -> repo.getAlias()).collect(Collectors.toSet()));
                configurationService.removeConfiguration(thisGroupConfig);
                removedOu = registeredOrganizationalUnits.remove(groupName);
            } finally {
                configurationService.endBatch();
                if (removedOu != null) {
                    removeOrganizationalUnitEvent.fire(new RemoveOrganizationalUnitEvent(removedOu,
                                                                                         getUserInfo(sessionInfo)));
                }
            }
        }
    }

    @Override
    public OrganizationalUnit getParentOrganizationalUnit(final Repository repository) {
        for (final OrganizationalUnit organizationalUnit : registeredOrganizationalUnits.values()) {
            if (organizationalUnit.getRepositories() != null) {
                for (final Repository ouRepository : organizationalUnit.getRepositories()) {
                    if (ouRepository.getAlias().equals(repository.getAlias())) {
                        return organizationalUnit;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public List<OrganizationalUnit> getOrganizationalUnits(Repository repository) {
        final ArrayList<OrganizationalUnit> result = new ArrayList<>();

        for (final OrganizationalUnit organizationalUnit : registeredOrganizationalUnits.values()) {
            if (organizationalUnit.getRepositories() != null) {
                for (final Repository ouRepository : organizationalUnit.getRepositories()) {
                    if (ouRepository.getAlias().equals(repository.getAlias())) {
                        result.add(organizationalUnit);
                    }
                }
            }
        }

        return Collections.unmodifiableList(result);
    }

    @Override
    public String getSanitizedDefaultGroupId(final String proposedGroupId) {
        //Only [A-Za-z0-9_\-.] are valid so strip everything else out
        return proposedGroupId != null ? proposedGroupId.replaceAll("[^A-Za-z0-9_\\-.]",
                                                                    "") : proposedGroupId;
    }

    @Override
    public Boolean isValidGroupId(final String proposedGroupId) {
        if (proposedGroupId != null && !proposedGroupId.trim().isEmpty()) {
            if (proposedGroupId.length() == getSanitizedDefaultGroupId(proposedGroupId).length()) {
                return true;
            }
        }
        return false;
    }

    public void updateRegisteredOUCache(@Observes @org.guvnor.structure.backend.config.Repository SystemRepositoryChangedEvent changedEvent) {
        registeredOrganizationalUnits.clear();
        loadOrganizationalUnits();
    }

    // refresh org unit in case repository changed otherwise it will have outdated information
    public void updateRegisteredOUonRepoChange(@Observes RepositoryEnvironmentUpdatedEvent changedEvent) {
        registeredOrganizationalUnits.clear();
        loadOrganizationalUnits();
    }

    protected String getUserInfo(SessionInfo sessionInfo) {

        try {
            return sessionInfo.getIdentity().getIdentifier();
        } catch (final Exception e) {
            return "system";
        }
    }
}
