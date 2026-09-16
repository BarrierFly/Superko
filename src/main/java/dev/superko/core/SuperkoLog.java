package dev.superko.core;

import java.util.function.IntFunction;

/**
 * Version-independent logging facade. The mod wires a sink that writes to the server
 * console (SLF4J) and, for the "broadcast" level, to online operators.
 */
public final class SuperkoLog {
    public static final int LEVEL_NONE = 0;
    public static final int LEVEL_CONSOLE = 1;
    public static final int LEVEL_BROADCAST = 2;
    /** Console-only per-setBlock judgment trace, for diagnosing why a loop is or is not caught. */
    public static final int LEVEL_DEBUG = 3;

    public interface Sink {
        void console(String message);

        void broadcast(String message);
    }

    private static volatile Sink sink = new Sink() {
        @Override
        public void console(String message) {
        }

        @Override
        public void broadcast(String message) {
        }
    };
    private static volatile int logLevel = LEVEL_CONSOLE;
    private static volatile IntFunction<String> contextNamer = SuperkoLog::defaultContextName;

    private SuperkoLog() {
    }

    public static void configure(int level, Sink sink) {
        SuperkoLog.logLevel = level;
        if (sink != null) {
            SuperkoLog.sink = sink;
        }
    }

    public static void setContextNamer(IntFunction<String> namer) {
        if (namer != null) {
            contextNamer = namer;
        }
    }

    public static int logLevel() {
        return logLevel;
    }

    /** A rejected action: shown on the console when logging is on, broadcast at the top level. */
    public static void intervention(String message) {
        int lvl = logLevel;
        if (lvl >= LEVEL_CONSOLE) {
            sink.console(message);
        }
        if (lvl >= LEVEL_BROADCAST) {
            sink.broadcast(message);
        }
    }

    /** Per-setBlock judgment trace; only emitted at the debug level, console only. */
    public static void debug(String message) {
        if (logLevel >= LEVEL_DEBUG) {
            sink.console(message);
        }
    }

    /**
     * Guard for callers that would otherwise build the trace string eagerly: string
     * concatenation is a measurable cost on hot paths like the per-setBlock traces.
     */
    public static boolean isDebug() {
        return logLevel >= LEVEL_DEBUG;
    }

    /** Always shown on the console (cap warnings, etc.). */
    public static void warn(String message) {
        sink.console(message);
    }

    public static String contextName(int ctx) {
        return contextNamer.apply(ctx);
    }

    private static String defaultContextName(int ctx) {
        if (ctx == UpdateContext.SELF) {
            return "self";
        }
        if (ctx == UpdateContext.NEIGHBOR) {
            return "neighbor";
        }
        if (ctx >= UpdateContext.SHAPE_BASE) {
            return "shape[" + (ctx - UpdateContext.SHAPE_BASE) + "]";
        }
        return "ctx" + ctx;
    }
}
