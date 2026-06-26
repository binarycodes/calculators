#!/usr/bin/env bash
#
# Task runner for the calculators app.
#
# Every task pins JAVA_HOME to a JDK 21 because a bare `mvn` on this machine
# picks JDK 25, under which Lombok silently fails to generate getters/setters
# and the build dies with bogus "cannot find symbol" errors.
#
# Usage: ./run.sh <task>   (run with no task, or `help`, to list tasks)

set -euo pipefail

cd "$(dirname "$0")"

readonly BUNDLE_DIR="src/main/bundles"
readonly STYLES_CSS="src/main/resources/META-INF/resources/styles.css"

# Resolve a JDK 21 from SDKMAN; fall back to whatever JAVA_HOME is already set.
resolve_java_home() {
    local jdk21
    jdk21=$(ls -d "${HOME}"/.sdkman/candidates/java/21* 2>/dev/null | head -1 || true)
    if [[ -n "${jdk21}" ]]; then
        export JAVA_HOME="${jdk21}"
    elif [[ -z "${JAVA_HOME:-}" ]]; then
        echo "No JDK 21 found under ~/.sdkman and JAVA_HOME is unset." >&2
        exit 1
    fi
    echo "Using JAVA_HOME=${JAVA_HOME}"
}

# Bump styles.css mtime. styles.css is only @import statements; editing an
# imported partial (colors.css, grid.css, ...) leaves styles.css unchanged, so
# the browser serves a 304 and keeps the stale partial. Touching the entry file
# busts that cache.
touch_styles() {
    touch "${STYLES_CSS}"
    echo "Touched ${STYLES_CSS}"
}

# Remove the cached frontend bundles. Vaadin's dev server reuses an existing
# bundle and logs "a development mode bundle build is not needed", so a changed
# @CssImport(themeFor=...) is ignored until both bundles are gone. Deleting only
# dev.bundle is not enough — a leftover prod.bundle is still treated as valid.
clear_bundles() {
    rm -rf "${BUNDLE_DIR}/dev.bundle" "${BUNDLE_DIR}/prod.bundle" target/dev-bundle
    echo "Cleared cached frontend bundles"
}

task_compile() {
    resolve_java_home
    mvn -o -q compile
    echo "Compiled. spring-boot-devtools will hot-restart a running app."
}

# Force a clean frontend rebuild after a @CssImport / @JsModule change: drop the
# cached bundles, touch styles.css, then compile so devtools rebuilds the bundle.
task_bundle() {
    resolve_java_home
    clear_bundles
    touch_styles
    mvn -o -q compile
    echo "Bundle cleared and recompiled. Reload the browser to pick up the new bundle."
}

task_styles() {
    touch_styles
}

task_test() {
    resolve_java_home
    # JaCoCo enforces an 80% line-coverage gate on the */service packages.
    mvn -o test
}

task_run() {
    resolve_java_home
    mvn -o spring-boot:run
}

# Full production build. The production bundle build runs the Vaadin Charts
# commercial-license check; -Dvaadin.commercialWithBanner lets it build with a
# watermark when no license is configured.
task_package() {
    resolve_java_home
    mvn clean package -Dvaadin.commercialWithBanner
}

task_clean() {
    resolve_java_home
    clear_bundles
    mvn -o clean
}

usage() {
    cat <<'EOF'
Usage: ./run.sh <task>

Tasks:
  compile    Compile sources (triggers a devtools hot-restart of a running app)
  bundle     Clear cached frontend bundles + touch styles.css + recompile
             (use after changing a @CssImport themeFor / @JsModule)
  styles     Touch styles.css so the browser reloads @imported CSS partials
  test       Run the test suite (enforces the JaCoCo coverage gate)
  run        Start the app with spring-boot:run (dev mode)
  package    Full production build (mvn clean package)
  clean      mvn clean + remove cached bundles
  help       Show this message
EOF
}

main() {
    local task="${1:-help}"
    case "${task}" in
        compile) task_compile ;;
        bundle)  task_bundle ;;
        styles)  task_styles ;;
        test)    task_test ;;
        run)     task_run ;;
        package) task_package ;;
        clean)   task_clean ;;
        help|-h|--help) usage ;;
        *)
            echo "Unknown task: ${task}" >&2
            echo >&2
            usage >&2
            exit 1
            ;;
    esac
}

main "$@"
