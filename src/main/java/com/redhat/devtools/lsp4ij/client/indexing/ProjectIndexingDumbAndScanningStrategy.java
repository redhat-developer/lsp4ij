/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.client.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Project indexing strategy which supports tracks of dumb indexing and files scanning.
 *
 * <p>This strategy class uses the
 * "com.intellij.util.indexing.diagnostic.ProjectIndexingActivityHistoryListener" class which is marked as Internal
 * which is forbidden to use it</p>
 *
 * <p>As IntelliJ doesn't provide an API to know where scanning files are started / finished, the only solution is
 * to implement "com.intellij.util.indexing.diagnostic.ProjectIndexingActivityHistoryListener" to track start / end of dumb / files scanning.
 * This class create with Java reflection an instance which implements "com.intellij.util.indexing.diagnostic.ProjectIndexingActivityHistoryListener"
 * and register this instance (not with extension point) with subscribe(ProjectIndexingActivityHistoryListener.TOPIC, instance) method
 * </p>
 *
 * <p>
 *     In case initialization via Java reflection fails, we fallback to tracking only the dumb using a {@link com.intellij.openapi.project.DumbService.DumbModeListener}
 *     with the class {@link ProjectIndexingOnlyDumbStrategy}.
 * </p>
 */
@ApiStatus.Internal
public class ProjectIndexingDumbAndScanningStrategy extends ProjectIndexingStrategyBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectIndexingDumbAndScanningStrategy.class);

    private boolean enabled;

    private Method projectMethod;

    private boolean projectMethodSearched;

    public ProjectIndexingDumbAndScanningStrategy() {
        projectMethodSearched = false;
        initialize();
    }
    
    public static ProjectIndexingDumbAndScanningStrategy getInstance() {
        return ApplicationManager.getApplication().getService(ProjectIndexingDumbAndScanningStrategy.class);
    }

    private void initialize() {
        try {
            // Load ProjectIndexingActivityHistoryListener interface via Reflection
            Class<?> listenerInterface = Class.forName("com.intellij.util.indexing.diagnostic.ProjectIndexingActivityHistoryListener");

            // Create a dynamic proxy with the ProjectIndexingActivityHistoryListener interface
            Object proxyInstance = Proxy.newProxyInstance(
                    listenerInterface.getClassLoader(),
                    new Class<?>[]{listenerInterface},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            try {
                                if ("onStartedScanning".equals(method.getName())) {
                                    onStartedScanning(getProject(args));
                                } else if ("onFinishedScanning".equals(method.getName())) {
                                    onFinishedScanning(getProject(args));
                                } else if ("onStartedDumbIndexing".equals(method.getName())) {
                                    onStartedDumbIndexing(getProject(args));
                                } else if ("onFinishedDumbIndexing".equals(method.getName())) {
                                    onFinishedDumbIndexing(getProject(args));
                                }
                            }
                            catch(Exception e) {
                                // Do nothing
                            }
                            return null;
                        }
                    });

            // Get the proper topic ProjectIndexingActivityHistoryListener.Companion.getTOPIC() method to register the proxyInstance
            Field companionField = listenerInterface.getField("Companion");
            companionField.setAccessible(true);
            Object companionInstance = companionField.get(null);
            Method getTopicMethod = companionInstance.getClass().getMethod("getTOPIC");
            getTopicMethod.setAccessible(true);
            Topic topicInstance = (Topic) getTopicMethod.invoke(companionInstance);

            // Subscribe the proxyInstance with the topicInstance
            //ApplicationManager.getApplication().getMessageBus().connect(ProjectIndexingActivityHistoryListener.Companion.getTOPIC(), proxyInstance)
            ApplicationManager.getApplication().getMessageBus().connect().subscribe(topicInstance, proxyInstance);

            enabled = true;
        } catch (Exception e) {
            LOGGER.error("Error while initializing ProjectIndexingDumbAndScanningStrategy", e);
        }
    }

    private @Nullable Project getProject(Object[] args) {
        /* ProjectIndexingActivityHistory */ Object history = args[0];
        Method projectMethod = getProjectMethod();
        if(projectMethod != null) {
            try {
                return (Project) projectMethod.invoke(history);
            }
            catch (Exception e) {
                LOGGER.error("Error while getting project from ProjectIndexingActivityHistory instance.", e);
            }
        }
        return null;
    }

    Method getProjectMethod() {
        if (projectMethodSearched) {
            return projectMethod;
        }
        return getProjectMethodSync();
    }

    private synchronized Method getProjectMethodSync() {
        if (projectMethodSearched) {
            return projectMethod;
        }
        try {
            Class<?> projectScanningHistoryClass = Class.forName("com.intellij.util.indexing.diagnostic.ProjectIndexingActivityHistory");
            Method projectMethod = projectScanningHistoryClass.getMethod("getProject");
            projectMethod.setAccessible(true);
            this.projectMethod = projectMethod;
            return projectMethod;
        }
        catch(Exception e) {
            LOGGER.error("Error while getting ProjectIndexingActivityHistory#getProject() with Java reflection.", e);
            return null;
        }
        finally {
            projectMethodSearched = true;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
