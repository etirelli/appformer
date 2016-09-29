/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.uberfire.ext.preferences.backend;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.uberfire.commons.services.cdi.Startup;
import org.uberfire.commons.services.cdi.StartupType;
import org.uberfire.ext.preferences.shared.bean.BasePreferenceBean;
import org.uberfire.ext.preferences.shared.bean.Preference;

@Startup
@ApplicationScoped
public class PreferenceBeanDefaultValueRecorder {

    private Instance<Preference> preferences;

    public PreferenceBeanDefaultValueRecorder() {
    }

    @Inject
    public PreferenceBeanDefaultValueRecorder( final Instance<Preference> preferences ) {
        this.preferences = preferences;
    }

    @PostConstruct
    public void initializePreferenceValues() {
        getPreferences().forEach( preference -> {
            ( (BasePreferenceBean) preference ).saveDefaultValue();
        } );
    }

    Iterable<Preference> getPreferences() {
        return preferences;
    }
}
