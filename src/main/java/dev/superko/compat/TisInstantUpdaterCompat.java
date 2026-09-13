package dev.superko.compat;

import dev.superko.SuperkoMod;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Soft compatibility with Carpet-TIS-Addition's {@code instantBlockUpdaterReintroduced}.
 *
 * <p>The rule replaces {@code Level.neighborUpdater} with the vanilla instant (recursive)
 * updater. The core judgment only hooks {@code Level.setBlock}, which both generations of
 * updater funnel through, so the rule can never bypass it. Update-context tagging is
 * likewise installed on vanilla classes that both updaters use
 * ({@code NeighborUpdater.executeUpdate}/{@code executeShapeUpdate} call sites plus the
 * collecting updater's run loop), so action keys stay meaningful either way. This class
 * only reports the detected rule state for the log.
 */
public final class TisInstantUpdaterCompat {
    private TisInstantUpdaterCompat() {
    }

    public static void logAdvice() {
        try {
            if (!FabricLoader.getInstance().isModLoaded("carpettisaddition")) {
                return;
            }
        } catch (Throwable t) {
            return;
        }
        Boolean ruleOn = readRule();
        if (ruleOn == null) {
            SuperkoMod.LOGGER.info("[Superko] Carpet-TIS-Addition detected. Enabling instantBlockUpdaterReintroduced is recommended: it keeps update chains instant (recursive), and superko is fully compatible with it.");
        } else if (ruleOn) {
            SuperkoMod.LOGGER.info("[Superko] Carpet-TIS-Addition detected with instantBlockUpdaterReintroduced=on; update chains are instant/recursive and fully covered by superko.");
        } else {
            SuperkoMod.LOGGER.info("[Superko] Carpet-TIS-Addition detected with instantBlockUpdaterReintroduced=off; superko covers the vanilla manual-stack chains as-is.");
        }
    }

    private static Boolean readRule() {
        try {
            Class<?> cls = Class.forName(
                    "carpettisaddition.CarpetTISAdditionSettings", false, TisInstantUpdaterCompat.class.getClassLoader());
            Object value = cls.getField("instantBlockUpdaterReintroduced").get(null);
            String s = String.valueOf(value);
            if ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) {
                return Boolean.parseBoolean(s);
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }
}
