/*
 * Copyright (c) 2022 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.container_orchestrator;

import io.airbyte.commons.temporal.TemporalUtils;
import io.airbyte.commons.temporal.sync.OrchestratorConstants;
import io.airbyte.config.EnvConfigs;
import io.airbyte.config.helpers.LogClientSingleton;
import io.airbyte.persistence.job.models.JobRunConfig;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class LoggingEventListener implements ApplicationEventListener<ServerStartupEvent> {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final Map<String, String> envs;
  private final EnvConfigs configs;
  private final JobRunConfig jobRunConfig;

  LoggingEventListener(@Named("envs") final Map<String, String> envs, final EnvConfigs configs, final JobRunConfig jobRunConfig) {
    this.envs = envs;
    this.configs = configs;
    this.jobRunConfig = jobRunConfig;
  }

  @Override
  public void onApplicationEvent(final ServerStartupEvent event) {
    log.info("started logging");
    OrchestratorConstants.ENV_VARS_TO_TRANSFER.stream()
        .filter(envs::containsKey)
        .forEach(envVar -> System.setProperty(envVar, envs.get(envVar)));

    // make sure the new configuration is picked up
    final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    ctx.reconfigure();

    LogClientSingleton.getInstance().setJobMdc(
        configs.getWorkerEnvironment(),
        configs.getLogConfigs(),
        TemporalUtils.getJobRoot(configs.getWorkspaceRoot(), jobRunConfig.getJobId(), jobRunConfig.getAttemptId())
    );
  }

}
