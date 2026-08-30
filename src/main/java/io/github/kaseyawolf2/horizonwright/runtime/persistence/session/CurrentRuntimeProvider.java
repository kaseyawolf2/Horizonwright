package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import java.util.Optional;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.task.IHorizonwrightController;

/** Late-bound runtime/controller lookup for commands and dashboards. */
public interface CurrentRuntimeProvider {

    Optional<HorizonwrightRuntime> getCurrentRuntime();

    Optional<IHorizonwrightController> getCurrentController();

    ClientRuntimeSessionDiagnostic getDiagnostic();
}
