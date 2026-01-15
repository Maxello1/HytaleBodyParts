package com.maxello.hytalebodytypes;

import com.hypixel.hytale.server.core.Message;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChatUi {
    private ChatUi() {}

    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    public static Message button(String label, String hexColor, String runCommand, String hoverText) {
        Message m = Message.raw(label);

        // color(String)
        if (hexColor != null && !hexColor.isEmpty()) {
            tryInvoke(m, "color", new Class[]{String.class}, new Object[]{hexColor});
        }

        // hover (try common patterns)
        if (hoverText != null && !hoverText.isEmpty()) {
            Message hoverMsg = Message.raw(hoverText);

            if (!tryInvokeAny(m,
                    new String[]{"hover", "onHover"},
                    new Class<?>[][]{
                            {Message.class},
                            {Message.class}
                    },
                    new Object[][]{
                            {hoverMsg},
                            {hoverMsg}
                    }
            )) {
                // Some APIs use hoverText(String)
                tryInvoke(m, "hoverText", new Class[]{String.class}, new Object[]{hoverText});
            }
        }

        // click run command (auto-detect)
        if (runCommand != null && !runCommand.isEmpty()) {
            boolean ok = attachRunCommandClick(m, runCommand);

            if (!ok) {
                // last-ditch tries by name
                ok = tryInvokeAny(m,
                        new String[]{"runCommand", "clickRunCommand", "onClickRunCommand", "command", "onClick"},
                        new Class<?>[][]{
                                {String.class},
                                {String.class},
                                {String.class},
                                {String.class},
                                {String.class}
                        },
                        new Object[][]{
                                {runCommand},
                                {runCommand},
                                {runCommand},
                                {runCommand},
                                {runCommand}
                        }
                );
            }

            if (!ok) {
                debugLogOnce(m);
            }
        }

        return m;
    }

    public static Message spacer(String s) {
        return Message.raw(s);
    }

    /**
     * Attempts to find a ClickAction enum inside Message (or related) that has RUN_COMMAND,
     * then finds a method on Message that accepts (thatEnum, String) and calls it.
     */
    private static boolean attachRunCommandClick(Message msg, String command) {
        try {
            // 1) Find an enum class that looks like click action and contains RUN_COMMAND
            Class<?> clickEnum = findEnumContaining(Message.class, "RUN_COMMAND", "RUNCOMMAND", "EXECUTE_COMMAND");
            if (clickEnum == null) return false;

            Object runCommandConstant = findEnumConstant(clickEnum, "RUN_COMMAND", "RUNCOMMAND", "EXECUTE_COMMAND");
            if (runCommandConstant == null) return false;

            // 2) Find a method on Message that can take (clickEnum, String)
            Method best = findMethod(msg.getClass(), clickEnum, String.class,
                    "click", "onClick", "setClick", "action", "withClick");

            if (best == null) return false;

            best.invoke(msg, runCommandConstant, command);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Class<?> findEnumContaining(Class<?> root, String... wantedConstNames) {
        // Check inner classes of Message first
        for (Class<?> c : root.getDeclaredClasses()) {
            if (c.isEnum() && hasAnyEnumConstant(c, wantedConstNames)) {
                return c;
            }
        }

        // Also try the same package via known naming patterns (best-effort)
        // If Hytale stores click enums elsewhere, we won't reliably discover without more info.
        return null;
    }

    private static boolean hasAnyEnumConstant(Class<?> enumClass, String... names) {
        try {
            Object[] consts = enumClass.getEnumConstants();
            if (consts == null) return false;
            for (Object o : consts) {
                String n = ((Enum<?>) o).name();
                for (String target : names) {
                    if (n.equalsIgnoreCase(target)) return true;
                }
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object findEnumConstant(Class<?> enumClass, String... names) {
        try {
            Object[] consts = enumClass.getEnumConstants();
            if (consts == null) return null;
            for (Object o : consts) {
                String n = ((Enum<?>) o).name();
                for (String target : names) {
                    if (n.equalsIgnoreCase(target)) return o;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Method findMethod(Class<?> type, Class<?> p0, Class<?> p1, String... nameHints) {
        Method[] methods = type.getMethods();
        Method fallback = null;

        for (Method m : methods) {
            Class<?>[] ps = m.getParameterTypes();
            if (ps.length == 2 && ps[0].isAssignableFrom(p0) && ps[1] == p1) {
                String n = m.getName().toLowerCase();
                for (String hint : nameHints) {
                    if (n.contains(hint.toLowerCase())) return m;
                }
                fallback = m;
            }
        }
        return fallback;
    }

    private static boolean tryInvokeAny(Object target, String[] names, Class<?>[][] sigs, Object[][] args) {
        for (int i = 0; i < names.length; i++) {
            if (tryInvoke(target, names[i], sigs[i], args[i])) return true;
        }
        return false;
    }

    private static boolean tryInvoke(Object target, String method, Class<?>[] sig, Object[] args) {
        try {
            Method m = target.getClass().getMethod(method, sig);
            m.invoke(target, args);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void debugLogOnce(Message msg) {
        if (!LOGGED.compareAndSet(false, true)) return;

        try {
            Method[] ms = msg.getClass().getMethods();
            System.out.println("[HytaleBodyTypes] Could not attach clickable chat actions. Methods on Message:");
            Arrays.stream(ms)
                    .filter(m -> {
                        String n = m.getName().toLowerCase();
                        return n.contains("click") || n.contains("hover") || n.contains("command") || n.contains("action");
                    })
                    .forEach(m -> System.out.println("  - " + m.getName() + Arrays.toString(m.getParameterTypes())));
        } catch (Throwable ignored) {
            // ignore
        }
    }
}
