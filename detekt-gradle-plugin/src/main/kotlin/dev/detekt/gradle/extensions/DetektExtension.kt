package dev.detekt.gradle.extensions

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

@Suppress("ComplexInterface")
interface DetektExtension {
    /**
     * The version of the detekt CLI to use for analysis.
     *
     * Default: the version of the applied detekt plugin.
     */
    val toolVersion: Property<String>

    /**
     * If set to `true`, analysis failures (i.e. findings at or above the configured [failOnSeverity]) will not
     * cause the build to fail.
     *
     * Default: `false`. Gradle property: `detekt.ignoreFailures`.
     */
    val ignoreFailures: Property<Boolean>

    /**
     * The minimum severity that will cause the build to fail. Findings at this level or above will be treated as
     * errors. Only takes effect when [ignoreFailures] is `false`.
     *
     * Default: [FailOnSeverity.Error]. Gradle property: `detekt.failOnSeverity`
     * (accepts `Error`, `Warning`, `Info`, or `Never`).
     */
    val failOnSeverity: Property<FailOnSeverity>

    /**
     * The directory where reports (HTML, XML, SARIF, etc.) are written.
     *
     * Default: `${reporting.baseDir}/detekt`.
     */
    val reportsDir: DirectoryProperty

    /**
     * The source files to analyze. Note: when the Kotlin Gradle plugin is applied, per-compilation tasks override
     * this with the compilation's actual source set.
     *
     * Default: `src/main/java`, `src/test/java`, `src/main/kotlin`, `src/test/kotlin`.
     */
    val source: ConfigurableFileCollection

    /**
     * Path to a detekt baseline XML file. Findings listed in the baseline are suppressed.
     *
     * Default: `detekt-baseline.xml` in the project directory.
     */
    val baseline: RegularFileProperty

    /**
     * The base path used to relativize file paths in reports.
     *
     * Default: the root project directory.
     */
    val basePath: DirectoryProperty

    /**
     * Whether to enable the detekt compiler plugin for analysis during compilation. This flag is ignored unless the
     * detekt compiler plugin (`io.github.detekt.gradle.compiler-plugin`) is actually applied to the project.
     *
     * Default: `true`. Gradle property: `detekt.enableCompilerPlugin`.
     */
    val enableCompilerPlugin: Property<Boolean>

    /**
     * Configuration files containing rule settings. These are merged on top of the default config  (if
     * [buildUponDefaultConfig] is `true`) or used as the sole config otherwise.
     *
     * Default: `config/detekt/detekt.yml` in the root project directory (if it exists).
     */
    val config: ConfigurableFileCollection

    /**
     * Enables debug output during analysis, including logging of each rule invocation.
     *
     * Default: `false`. Gradle property: `detekt.debug`.
     */
    val debug: Property<Boolean>

    /**
     * Runs detekt rules in parallel. Can speed up analysis on multi-core machines.
     *
     * Default: `false`. Gradle property: `detekt.parallel`.
     */
    val parallel: Property<Boolean>

    /**
     * Activates all available rules, including those disabled by default.
     *
     * Default: `false`. Gradle property: `detekt.allRules`.
     */
    val allRules: Property<Boolean>

    /**
     * Layers your custom [config] on top of detekt's default configuration rather than replacing it. This means any
     * rules not explicitly configured will retain their default settings.
     *
     * Default: `false`. Gradle property: `detekt.buildUponDefaultConfig`.
     */
    val buildUponDefaultConfig: Property<Boolean>

    /**
     * Disables all default rule sets, so only rules from custom rule set providers (added via the `detektPlugins`
     * configuration) are active. Default: `false`.
     *
     * Default: `false`. Gradle property: `detekt.disableDefaultRuleSets`.
     */
    val disableDefaultRuleSets: Property<Boolean>

    /**
     * Enables auto-correction of detected issues where supported.
     *
     * Default: `false`. Gradle property: `detekt.autoCorrect`.
     */
    val autoCorrect: Property<Boolean>

    /**
     * List of Android build variants for which no detekt task should be created. This is a combination of build
     * types and flavors, such as `fooDebug` or `barRelease`.
     */
    val ignoredVariants: ListProperty<String>

    /**
     * List of Android build types for which no detekt task should be created.
     */
    val ignoredBuildTypes: ListProperty<String>

    /**
     * List of Android build flavors for which no detekt task should be created.
     */
    val ignoredFlavors: ListProperty<String>
}
