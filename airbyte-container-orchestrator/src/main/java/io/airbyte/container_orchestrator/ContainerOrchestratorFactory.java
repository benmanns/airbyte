/*
 * Copyright (c) 2022 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.container_orchestrator;

import io.airbyte.commons.features.EnvVariableFeatureFlags;
import io.airbyte.commons.features.FeatureFlags;
import io.airbyte.commons.protocol.AirbyteMessageSerDeProvider;
import io.airbyte.commons.protocol.AirbyteMessageVersionedMigratorFactory;
import io.airbyte.commons.temporal.sync.OrchestratorConstants;
import io.airbyte.config.EnvConfigs;
import io.airbyte.workers.WorkerConfigs;
import io.airbyte.workers.process.AsyncOrchestratorPodProcess;
import io.airbyte.workers.process.DockerProcessFactory;
import io.airbyte.workers.process.KubePortManagerSingleton;
import io.airbyte.workers.process.KubeProcessFactory;
import io.airbyte.workers.process.ProcessFactory;
import io.airbyte.workers.sync.DbtLauncherWorker;
import io.airbyte.workers.sync.NormalizationLauncherWorker;
import io.airbyte.workers.sync.ReplicationLauncherWorker;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.Environment;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

@Factory
class ContainerOrchestratorFactory {

  @Singleton
  FeatureFlags featureFlags() {
    return new EnvVariableFeatureFlags();
  }

  @Singleton
  EnvConfigs envConfigs(@Named("envs") final Map<String, String> env) {
    return new EnvConfigs(env);
  }

  @Singleton
  WorkerConfigs workerConfigs(final EnvConfigs envConfigs) {
    return new WorkerConfigs(envConfigs);
  }

  @Singleton
  @Requires(notEnv = Environment.KUBERNETES)
  ProcessFactory dockerProcessFactory(final WorkerConfigs workerConfigs, final EnvConfigs configs) {
    return new DockerProcessFactory(
        workerConfigs,
        configs.getWorkspaceRoot(),//Path.of(workspaceRoot),
        configs.getWorkspaceDockerMount(), //workspaceDockerMount,
        configs.getLocalDockerMount(), //localDockerMount,
        configs.getDockerNetwork()//dockerNetwork
    );
  }

  @Singleton
  @Requires(env = Environment.KUBERNETES)
  ProcessFactory kubeProcessFactory(
      final WorkerConfigs workerConfigs,
      final EnvConfigs configs,
      @Value("${micronaut.server.port}") final int serverPort
  ) throws UnknownHostException {
    final var localIp = InetAddress.getLocalHost().getHostAddress();
    final var kubeHeartbeatUrl = localIp + ":" + serverPort;

    // this needs to have two ports for the source and two ports for the destination (all four must be
    // exposed)
    KubePortManagerSingleton.init(OrchestratorConstants.PORTS);

    return new KubeProcessFactory(
        workerConfigs,
        configs.getJobKubeNamespace(),
        new DefaultKubernetesClient(),
        kubeHeartbeatUrl,
        false
    );
  }

  @Singleton
  JobOrchestrator<?> jobOrchestrator(@Named("application") final String application, final EnvConfigs envConfigs, final ProcessFactory processFactory,
      final FeatureFlags featureFlags, final WorkerConfigs workerConfigs, final AirbyteMessageSerDeProvider serdeProvider,
      final AirbyteMessageVersionedMigratorFactory migratorFactory
  ) {
    return switch (application) {
      case ReplicationLauncherWorker.REPLICATION ->
          new ReplicationJobOrchestrator(envConfigs, processFactory, featureFlags, serdeProvider, migratorFactory);
      case NormalizationLauncherWorker.NORMALIZATION -> new NormalizationJobOrchestrator(envConfigs, processFactory);
      case DbtLauncherWorker.DBT -> new DbtJobOrchestrator(envConfigs, workerConfigs, processFactory);
      case AsyncOrchestratorPodProcess.NO_OP -> new NoOpOrchestrator();
      default -> throw new IllegalStateException("Could not find job orchestrator for application: " + application);
    };
  }

}
