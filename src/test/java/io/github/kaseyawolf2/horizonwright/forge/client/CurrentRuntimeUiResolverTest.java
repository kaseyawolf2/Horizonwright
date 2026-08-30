package io.github.kaseyawolf2.horizonwright.forge.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.command.ICommandSender;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.IChatComponent;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.task.IHorizonwrightController;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleEnvironment;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.ClientRuntimeSessionDiagnostic;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.ClientRuntimeSessionManager;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.CurrentRuntimeProvider;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.HorizonwrightRuntimeSessionFactory;

public class CurrentRuntimeUiResolverTest {

    @Test
    public void unboundProviderReturnsItsTypedDiagnosticWithoutARuntime() {
        ClientRuntimeSessionManager manager = unboundManager();
        try {
            CurrentRuntimeUiResolver.Resolution resolution = CurrentRuntimeUiResolver.resolve(manager);

            assertFalse(resolution.isAvailable());
            assertEquals("UNBOUND: No profile is selected", resolution.getDiagnostic());
        } finally {
            manager.close();
        }
    }

    @Test
    public void runtimeLookupFailureStillReturnsTheTypedSessionDiagnostic() {
        final ClientRuntimeSessionManager manager = unboundManager();
        CurrentRuntimeProvider failingProvider = new CurrentRuntimeProvider() {

            @Override
            public Optional<HorizonwrightRuntime> getCurrentRuntime() {
                throw new IllegalStateException("lookup exploded");
            }

            @Override
            public Optional<IHorizonwrightController> getCurrentController() {
                return Optional.empty();
            }

            @Override
            public ClientRuntimeSessionDiagnostic getDiagnostic() {
                return manager.getDiagnostic();
            }
        };
        try {
            CurrentRuntimeUiResolver.Resolution resolution = CurrentRuntimeUiResolver.resolve(failingProvider);

            assertFalse(resolution.isAvailable());
            assertTrue(
                resolution.getDiagnostic()
                    .startsWith("UNBOUND: No profile is selected"));
            assertTrue(
                resolution.getDiagnostic()
                    .contains("failed safely"));
        } finally {
            manager.close();
        }
    }

    @Test
    public void availableRuntimeIsResolvedAtLookupTime() {
        final ClientRuntimeSessionManager manager = unboundManager();
        final HorizonwrightRuntime runtime = HorizonwrightRuntime.createSession();
        CurrentRuntimeProvider activeProvider = new CurrentRuntimeProvider() {

            @Override
            public Optional<HorizonwrightRuntime> getCurrentRuntime() {
                return Optional.of(runtime);
            }

            @Override
            public Optional<IHorizonwrightController> getCurrentController() {
                return Optional.of(runtime.getController());
            }

            @Override
            public ClientRuntimeSessionDiagnostic getDiagnostic() {
                return manager.getDiagnostic();
            }
        };
        try {
            CurrentRuntimeUiResolver.Resolution resolution = CurrentRuntimeUiResolver.resolve(activeProvider);

            assertTrue(resolution.isAvailable());
            assertSame(runtime, resolution.getRuntime());
        } finally {
            runtime.close();
            manager.close();
        }
    }

    @Test
    public void commandRechecksProviderAndUsesSafeEmptyTaskCompletionsWhenUnavailable() {
        final ClientRuntimeSessionManager manager = unboundManager();
        final HorizonwrightRuntime runtime = HorizonwrightRuntime.createSession();
        final boolean[] available = { true };
        CurrentRuntimeProvider switchingProvider = new CurrentRuntimeProvider() {

            @Override
            public Optional<HorizonwrightRuntime> getCurrentRuntime() {
                return available[0] ? Optional.of(runtime) : Optional.<HorizonwrightRuntime>empty();
            }

            @Override
            public Optional<IHorizonwrightController> getCurrentController() {
                return available[0] ? Optional.of(runtime.getController()) : Optional.<IHorizonwrightController>empty();
            }

            @Override
            public ClientRuntimeSessionDiagnostic getDiagnostic() {
                return manager.getDiagnostic();
            }
        };
        List<String> messages = new ArrayList<>();
        ICommandSender sender = recordingSender(messages);
        HorizonwrightClientCommand command = new HorizonwrightClientCommand(switchingProvider);
        try {
            command.processCommand(sender, new String[] { "status" });
            assertTrue(
                messages.get(0),
                messages.get(0)
                    .contains("Horizonwright:"));

            available[0] = false;
            command.processCommand(sender, new String[] { "status" });
            assertTrue(
                messages.get(1),
                messages.get(1)
                    .contains("UNBOUND: No profile is selected"));
            assertTrue(
                command.addTabCompletionOptions(sender, new String[] { "" })
                    .contains("profile"));
            assertTrue(
                command.addTabCompletionOptions(sender, new String[] { "resume", "" })
                    .isEmpty());
        } finally {
            runtime.close();
            manager.close();
        }
    }

    @Test
    public void resumeChoiceRunsTheExactTaskCommandAndExplainsItOnHover() {
        HorizonwrightRuntime runtime = HorizonwrightRuntime.createSession();
        try {
            TaskSnapshot submitted = runtime.submitGoTo(0, 20, 70, -4, 1);
            TaskSnapshot candidate = runtime.pauseTask(
                submitted.getSpec()
                    .getId());

            IChatComponent choice = HorizonwrightClientCommand.clickableResumeChoice(candidate);

            assertEquals(
                ClickEvent.Action.RUN_COMMAND,
                choice.getChatStyle()
                    .getChatClickEvent()
                    .getAction());
            assertEquals(
                "/hw resume " + candidate.getSpec()
                    .getId(),
                choice.getChatStyle()
                    .getChatClickEvent()
                    .getValue());
            assertEquals(
                HoverEvent.Action.SHOW_TEXT,
                choice.getChatStyle()
                    .getChatHoverEvent()
                    .getAction());
            assertTrue(
                choice.getChatStyle()
                    .getChatHoverEvent()
                    .getValue()
                    .getUnformattedText()
                    .contains(
                        candidate.getSpec()
                            .getId()));
        } finally {
            runtime.close();
        }
    }

    private static ICommandSender recordingSender(final List<String> messages) {
        return (ICommandSender) Proxy.newProxyInstance(
            CurrentRuntimeUiResolverTest.class.getClassLoader(),
            new Class<?>[] { ICommandSender.class },
            (proxy, method, arguments) -> {
                if ("addChatMessage".equals(method.getName())) {
                    messages.add(((IChatComponent) arguments[0]).getUnformattedText());
                }
                Class<?> returnType = method.getReturnType();
                if (returnType == Boolean.TYPE) {
                    return false;
                }
                if (returnType == Integer.TYPE) {
                    return 0;
                }
                return null;
            });
    }

    private static ClientRuntimeSessionManager unboundManager() {
        return new ClientRuntimeSessionManager(
            new HorizonwrightRuntimeSessionFactory(
                connection -> ScheduleEnvironment.disconnected(),
                connection -> null),
            identity -> null,
            () -> 0L);
    }
}
