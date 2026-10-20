package ua.battlesim.app;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the dependency rules of the project architecture (section 2):
 *
 * <pre>
 * Layer 4  bs-app                    bs-tools
 * Layer 3  bs-ui  bs-gfx-world  bs-gfx-units  bs-gfx-fx  ->  bs-gfx-core
 * Layer 2  bs-session  bs-engine  bs-combat  bs-movement  bs-weather  bs-scenario
 * Layer 1  bs-terrain  bs-features  bs-nav
 * Layer 0  bs-math  bs-api (contracts, no dependencies)
 * </pre>
 *
 * Dependencies point downward only; modules of the same layer never depend on
 * each other — they talk through the bs-api contracts and the layer-4 roots
 * (bs-app, bs-tools) inject the implementations.
 */
final class LayeringTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("ua.battlesim");

    private static final String API = "Layer 0 - bs-api";
    private static final String MATH = "Layer 0 - bs-math";
    private static final String TERRAIN = "Layer 1 - bs-terrain";
    private static final String FEATURES = "Layer 1 - bs-features";
    private static final String NAV = "Layer 1 - bs-nav";
    private static final String WEATHER = "Layer 2 - bs-weather";
    private static final String COMBAT = "Layer 2 - bs-combat";
    private static final String MOVEMENT = "Layer 2 - bs-movement";
    private static final String SCENARIO = "Layer 2 - bs-scenario";
    private static final String ENGINE = "Layer 2 - bs-engine";
    private static final String SESSION = "Layer 2 - bs-session";
    private static final String GFX_CORE = "Layer 3 - bs-gfx-core";
    private static final String GFX_WORLD = "Layer 3 - bs-gfx-world";
    private static final String GFX_UNITS = "Layer 3 - bs-gfx-units";
    private static final String GFX_FX = "Layer 3 - bs-gfx-fx";
    private static final String UI = "Layer 3 - bs-ui";
    private static final String TOOLS = "Layer 4 - bs-tools";
    private static final String APP = "Layer 4 - bs-app";

    private static final ArchRule LAYERING = Architectures.layeredArchitecture()
            .consideringOnlyDependenciesInAnyPackage("ua.battlesim..")
            .layer(API).definedBy("ua.battlesim.api..")
            .layer(MATH).definedBy("ua.battlesim.math..")
            .layer(TERRAIN).definedBy("ua.battlesim.terrain..")
            .layer(FEATURES).definedBy("ua.battlesim.features..")
            .layer(NAV).definedBy("ua.battlesim.nav..")
            .layer(WEATHER).definedBy("ua.battlesim.weather..")
            .layer(COMBAT).definedBy("ua.battlesim.combat..")
            .layer(MOVEMENT).definedBy("ua.battlesim.movement..")
            .layer(SCENARIO).definedBy("ua.battlesim.scenario..")
            .layer(ENGINE).definedBy("ua.battlesim.engine..")
            .layer(SESSION).definedBy("ua.battlesim.session..")
            .layer(GFX_CORE).definedBy("ua.battlesim.gfx.core..")
            .layer(GFX_WORLD).definedBy("ua.battlesim.gfx.world..")
            .layer(GFX_UNITS).definedBy("ua.battlesim.gfx.units..")
            .layer(GFX_FX).definedBy("ua.battlesim.gfx.fx..")
            .layer(UI).definedBy("ua.battlesim.ui..")
            // bs-tools is a sibling layer-4 root; it is not on this classpath,
            // so its layer is optional (the dedicated noClasses test covers it).
            .optionalLayer(TOOLS).definedBy("ua.battlesim.tools..")
            .layer(APP).definedBy("ua.battlesim.app..")
            .whereLayer(MATH).mayOnlyAccessLayers(API)
            .whereLayer(TERRAIN).mayOnlyAccessLayers(API, MATH)
            .whereLayer(FEATURES).mayOnlyAccessLayers(API, MATH)
            .whereLayer(NAV).mayOnlyAccessLayers(API, MATH)
            .whereLayer(WEATHER).mayOnlyAccessLayers(API, MATH)
            .whereLayer(COMBAT).mayOnlyAccessLayers(API)
            .whereLayer(MOVEMENT).mayOnlyAccessLayers(API)
            .whereLayer(SCENARIO).mayOnlyAccessLayers(API, MATH)
            .whereLayer(ENGINE).mayOnlyAccessLayers(API)
            .whereLayer(SESSION).mayOnlyAccessLayers(API)
            .whereLayer(GFX_CORE).mayOnlyAccessLayers(API)
            .whereLayer(GFX_WORLD).mayOnlyAccessLayers(API, GFX_CORE)
            .whereLayer(GFX_UNITS).mayOnlyAccessLayers(API, GFX_CORE)
            .whereLayer(GFX_FX).mayOnlyAccessLayers(API, GFX_CORE)
            .whereLayer(UI).mayOnlyAccessLayers(API, GFX_CORE)
            .whereLayer(TOOLS).mayOnlyAccessLayers(
                    API, MATH,
                    TERRAIN, FEATURES, NAV,
                    WEATHER, COMBAT, MOVEMENT, SCENARIO, ENGINE, SESSION)
            .whereLayer(APP).mayOnlyAccessLayers(
                    API, MATH,
                    TERRAIN, FEATURES, NAV,
                    WEATHER, COMBAT, MOVEMENT, SCENARIO, ENGINE, SESSION,
                    GFX_CORE, GFX_WORLD, GFX_UNITS, GFX_FX, UI)
            .because("section 2: dependencies point downward only; same-layer "
                    + "modules never depend on each other and talk through bs-api");

    @Test
    @DisplayName("all modules respect the layer dependency rules")
    void allModulesRespectTheLayerDependencyRules() {
        LAYERING.check(CLASSES);
    }

    @Test
    @DisplayName("bs-app (layer 4) never depends on bs-tools (same layer)")
    void appNeverDependsOnTools() {
        noClasses().that().resideInAPackage("ua.battlesim.app..")
                .should().dependOnClassesThat().resideInAPackage("ua.battlesim.tools..")
                .because("layer-4 composition roots are siblings, not dependents")
                .check(CLASSES);
    }

    @Test
    @DisplayName("bs-api depends on no other ua.battlesim module")
    void apiDependsOnNoOtherModule() {
        noClasses().that().resideInAPackage("ua.battlesim.api..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "ua.battlesim.math..", "ua.battlesim.terrain..",
                        "ua.battlesim.features..", "ua.battlesim.nav..",
                        "ua.battlesim.weather..", "ua.battlesim.combat..",
                        "ua.battlesim.movement..", "ua.battlesim.scenario..",
                        "ua.battlesim.engine..", "ua.battlesim.session..",
                        "ua.battlesim.gfx..", "ua.battlesim.ui..",
                        "ua.battlesim.tools..", "ua.battlesim.app..")
                .because("layer 0 must have no module dependencies at all")
                .check(CLASSES);
    }
}