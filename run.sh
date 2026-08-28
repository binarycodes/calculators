#!/usr/bin/env bash
#
# Task runner for a Vaadin and Spring Boot project, built with Maven.
#
# Every task pins JAVA_HOME to the JDK the project is built for, because a bare
# `mvn` picks whatever the machine defaults to — and under a newer JDK than Lombok
# supports it silently generates no getters, so the build dies with bogus
# "cannot find symbol" errors.
#
# Everything specific to one project is in run.conf beside it; nothing in this file
# names a project, so projects share it as it stands. A task this runner does not
# have goes in run.tasks.sh, for the same reason.
#
# Usage: ./run.sh <task> (or `help` to list tasks).

set -euo pipefail

cd "$(dirname "$0")"

readonly PROJECT_CONFIG_FILE="run.conf"

# Tasks a project has that this runner does not: a file of task_<name> functions,
# sourced below the helpers so they can call setup and run_mvn like the tasks here
# do. Keeping them out of this file is what lets a project take a newer runner
# without a merge. It may also define project_usage, printed under the task list.
readonly PROJECT_TASKS_FILE="run.tasks.sh"

if [[ ! -f "${PROJECT_CONFIG_FILE}" ]]; then
    echo "No ${PROJECT_CONFIG_FILE} found" >&2
    exit 1
fi
source "./${PROJECT_CONFIG_FILE}"

# A project has to answer these two; nothing sensible can be assumed for either.
for setting in PROJECT_NAME JAVA_VERSION; do
    if [[ -z "${!setting:-}" ]]; then
        echo "${PROJECT_CONFIG_FILE} sets no ${setting}" >&2
        exit 1
    fi
done
unset setting

# Everything else falls back to the layout a Vaadin project has by default, so a
# configuration only names what it decides. See run.conf for what each one is.
: "${ENV_DIR:=environment/dev}"
: "${CONTAINER_REQUIRED:=true}"
: "${CONTAINER_PREFIX:=${PROJECT_NAME,,}-dev}"
: "${BUNDLE_DIR:=src/main/bundles}"
: "${STYLES_CSS:=src/main/resources/META-INF/resources/styles.css}"
: "${IT_PROFILE:=}"
: "${COMMIT_PROPERTY:=}"

readonly PROJECT_NAME JAVA_VERSION ENV_DIR CONTAINER_REQUIRED CONTAINER_PREFIX
readonly BUNDLE_DIR STYLES_CSS IT_PROFILE COMMIT_PROPERTY

readonly DEV_COMPOSE_FILE="${ENV_DIR}/compose.yaml"
readonly QUADLET_DIR="${ENV_DIR}/quadlet"
readonly QUADLET_INSTALL_DIR="${XDG_CONFIG_HOME:-${HOME}/.config}/containers/systemd"

# Filled in by setup, and the only mvn any task runs.
MAVEN=""

# Resolve the configured JDK from SDKMAN; fall back to whatever JAVA_HOME is set.
resolve_java_home() {
    local jdk
    jdk=$(ls -d "${HOME}"/.sdkman/candidates/java/"${JAVA_VERSION}"* 2>/dev/null | head -1 || true)
    if [[ -n "${jdk}" ]]; then
        export JAVA_HOME="${jdk}"
    elif [[ -z "${JAVA_HOME:-}" ]]; then
        echo "No JDK ${JAVA_VERSION} found under ~/.sdkman and JAVA_HOME is unset." >&2
        exit 1
    fi
    echo "Using JAVA_HOME=${JAVA_HOME}"
}

# Which runtime this machine has, or nothing at all. docker first: where both are
# installed, docker is the one whose context and compose plugin this script drives,
# and podman is usually there as its backend.
container_runtime() {
    if command -v docker >/dev/null 2>&1; then
        echo "docker"
    elif command -v podman >/dev/null 2>&1; then
        echo "podman"
    fi
}

require_container_runtime() {
    local runtime
    runtime=$(container_runtime)
    if [[ -z "${runtime}" ]]; then
        echo "No container runtime found. Either docker or podman is required." >&2
        exit 1
    fi
    echo "${runtime}"
}

# Testcontainers finds the daemon through DOCKER_HOST or /var/run/docker.sock and
# looks nowhere else. Neither CLI advertises itself there: docker keeps its endpoint
# in a "context", so on Colima and Rancher Desktop the CLI works while Testcontainers
# reports "Could not find a valid Docker environment", and rootless podman puts its
# socket under /run/user/<uid>. So ask whichever CLI this machine has where its
# socket is, and hand that to Testcontainers.
#
# The override is for Ryuk, the cleanup sidecar, which mounts the socket from inside
# its own container and so needs a path rather than an endpoint. The two runtimes
# disagree on which path: a VM-backed docker sees /var/run/docker.sock however the
# host spells it, while rootless podman runs no VM and needs the real path.
#
# Starting the socket is deliberately left to whoever runs this — the task says what
# to run and stops rather than enabling a system service behind their back.
resolve_container_runtime() {
    if [[ -n "${DOCKER_HOST:-}" ]]; then
        return
    fi
    if [[ -S /var/run/docker.sock ]]; then
        return
    fi

    local runtime endpoint="" override=""
    runtime=$(require_container_runtime)
    case "${runtime}" in
        docker)
            endpoint=$(docker context inspect --format '{{.Endpoints.docker.Host}}' 2>/dev/null || true)
            override="/var/run/docker.sock"
            ;;
        podman)
            local socket
            socket=$(podman info --format '{{.Host.RemoteSocket.Path}}' 2>/dev/null || true)
            if [[ -n "${socket}" ]]; then
                endpoint="unix://${socket}"
                override="${socket}"
            fi
            ;;
    esac

    if [[ -z "${endpoint}" ]]; then
        echo "${runtime} is installed but would not say where its socket is." >&2
        echo "Set DOCKER_HOST by hand, or check that ${runtime} itself works." >&2
        exit 1
    fi

    # podman reports its socket path whether or not anything is listening on it, and
    # a docker context outlives the daemon it points at, so neither answer proves the
    # runtime is up. Say which command starts it instead of failing deep inside a test.
    if [[ "${endpoint}" == unix://* && ! -S "${endpoint#unix://}" ]]; then
        echo "${runtime} reports its socket at ${endpoint#unix://}, but nothing is listening." >&2
        if [[ "${runtime}" == "podman" ]]; then
            echo "Start it with: systemctl --user start podman.socket" >&2
        else
            echo "Start the docker daemon, then retry." >&2
        fi
        exit 1
    fi

    export DOCKER_HOST="${endpoint}"
    export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="${TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE:-${override}}"
    echo "Using ${runtime} at DOCKER_HOST=${DOCKER_HOST}"
}

# The mvn to run. A bare `mvn` is on the PATH of a login shell only, because SDKMAN
# installs it under ~/.sdkman and the profile is what puts it there — so anything
# starting this script without one, the Claude Code preview pane included, dies with
# "mvn: command not found". Resolve it the way JAVA_HOME is resolved, rather than
# depending on how this script happened to be started.
resolve_maven() {
    if command -v mvn >/dev/null 2>&1; then
        MAVEN="mvn"
        echo "Using MAVEN=${MAVEN} (from the PATH)"
        return
    fi
    local candidate
    candidate=$(ls -d "${HOME}"/.sdkman/candidates/maven/current/bin/mvn \
                      "${HOME}"/.sdkman/candidates/maven/*/bin/mvn 2>/dev/null | head -1 || true)
    if [[ -z "${candidate}" ]]; then
        echo "No mvn on the PATH and none under ~/.sdkman." >&2
        echo "Install Maven, or start this from a shell where SDKMAN is initialised." >&2
        exit 1
    fi
    MAVEN="${candidate}"
    echo "Using MAVEN=${MAVEN}"
}

# What every task needs before it can do anything: the pinned JDK, the mvn that goes
# with it, and the container runtime. Resolved together, in one place, so that a task
# body is the work it is named after and nothing else.
#
# The runtime is not only the test suite's concern, which is why a project that needs
# one resolves it here rather than in the tasks that obviously want it: the application
# itself will not start without the services it talks to, and `package` runs the tests
# on the way through. A machine missing any of it is told so before the work starts
# rather than partway into it.
#
# No exceptions among the tasks either, not even the ones that never reach for a
# container: touching a stylesheet on a machine that cannot run the application
# accomplishes nothing, and failing at the first task is what says so. CONTAINER_REQUIRED
# is the one switch, and it is the project's answer rather than the task's.
setup() {
    resolve_java_home
    resolve_maven
    if [[ "${CONTAINER_REQUIRED}" == "true" ]]; then
        resolve_container_runtime
    fi
}

# Every build carries the deployed commit SHA when the project asks for one, so an
# artefact can be traced back to what produced it. Resolved from the working tree and
# passed to every mvn invocation. A non-repo checkout is then a hard error by design:
# an unidentifiable build should never be produced.
run_mvn() {
    local -a properties=()
    if [[ -n "${COMMIT_PROPERTY}" ]]; then
        local commit
        if ! commit=$(git rev-parse HEAD 2>/dev/null); then
            echo "Cannot resolve the git commit (not a git repository?)." >&2
            echo "${COMMIT_PROPERTY} is mandatory; refusing to build." >&2
            exit 1
        fi
        properties+=("-D${COMMIT_PROPERTY}=${commit}")
    fi
    "${MAVEN}" "$@" "${properties[@]}"
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
    setup
    run_mvn -q compile
    echo "Compiled. spring-boot-devtools will hot-restart a running app."
}

# Force a clean frontend rebuild after a @CssImport / @JsModule change: drop the
# cached bundles, touch styles.css, then compile so devtools rebuilds the bundle.
task_bundle() {
    setup
    clear_bundles
    touch_styles
    run_mvn -q compile
    echo "Bundle cleared and recompiled. Reload the browser to pick up the new bundle."
}

task_styles() {
    setup
    touch_styles
    run_mvn -q process-resources
    echo "Stylesheets processed. Reload the browser."
}

# Docker renamed compose from a script to a subcommand and both are still in the
# wild, so find whichever this machine has rather than guessing. Only the docker
# shapes: podman drives the stack through quadlets instead.
compose() {
    if docker compose version >/dev/null 2>&1; then
        docker compose -f "${DEV_COMPOSE_FILE}" "$@"
    elif command -v docker-compose >/dev/null 2>&1; then
        docker-compose -f "${DEV_COMPOSE_FILE}" "$@"
    else
        echo "No Compose found. Install the Docker Compose plugin (docker compose)" >&2
        echo "or the standalone docker-compose, then retry." >&2
        exit 1
    fi
}

# The units of the development stack, derived from what is in the quadlet directory
# so that adding a service there needs no change here — the same property the compose
# file has.
quadlet_services() {
    local unit
    for unit in "${QUADLET_DIR}"/*.container.in; do
        echo "$(basename "${unit}" .container.in).service"
    done
}

# Copy the units into the directory systemd's generator reads, substituting the one
# thing a quadlet cannot state for itself: where this checkout lives, which the two
# files mounted into Keycloak need as an absolute path.
install_quadlets() {
    mkdir -p "${QUADLET_INSTALL_DIR}"
    local unit
    for unit in "${QUADLET_DIR}"/*.in; do
        sed "s|@ENV_DIR@|${PWD}/${ENV_DIR}|g" \
            "${unit}" > "${QUADLET_INSTALL_DIR}/$(basename "${unit}" .in)"
    done
    systemctl --user daemon-reload
    echo "Installed quadlet units into ${QUADLET_INSTALL_DIR}"
}

# podman drives the stack through quadlets rather than compose: systemd starts the
# units, orders them, and — because every container declares Notify=healthy — reports
# one as started only once its healthcheck passes. That is what `up --wait` and
# `depends_on: service_healthy` bought under compose, so nothing is lost by not
# having a compose file in the loop.
quadlet_env() {
    local action="$1"
    local -a services
    mapfile -t services < <(quadlet_services)

    case "${action}" in
        up)
            install_quadlets
            systemctl --user start "${services[@]}"
            echo "Development stack is up. ${PROJECT_NAME} needs no configuration to reach it."
            echo "It is started, not enabled: add 'systemctl --user enable' yourself to"
            echo "get it back after a reboot."
            ;;
        down)
            systemctl --user stop "${services[@]}"
            echo "Development stack stopped; its data is kept."
            ;;
        logs)
            local -a follow=()
            local service
            for service in "${services[@]}"; do
                follow+=(--unit "${service}")
            done
            journalctl --user --follow "${follow[@]}"
            ;;
        reset)
            systemctl --user stop "${services[@]}"
            # Every volume of the stack rather than a named one, for the same reason
            # the services are read from a directory: this should not need editing
            # when the stack gains storage.
            podman volume ls --filter name="${CONTAINER_PREFIX}" --quiet \
                | xargs --no-run-if-empty podman volume rm --force >/dev/null
            echo "Development stack stopped and its data thrown away."
            ;;
    esac
}

compose_env() {
    local action="$1"
    case "${action}" in
        # --wait rather than plain -d: every service declares a healthcheck, and the
        # app starting before them is what turns a slow container into a confusing
        # connection or discovery error at startup.
        up)    compose up -d --wait && echo "Development stack is up. ${PROJECT_NAME} needs no configuration to reach it." ;;
        down)  compose stop && echo "Development stack stopped; its data is kept." ;;
        logs)  compose logs -f ;;
        reset) compose down -v && echo "Development stack stopped and its data thrown away." ;;
    esac
}

# The whole development stack in one task, because an application that needs a
# database, a browser and an identity provider needs all of them.
#
# Which containers that means is never this function's decision — compose reads the
# stack's compose.yaml, quadlets come from its quadlet directory — so adding a
# service needs no change here.
task_env() {
    setup

    if [[ "${CONTAINER_REQUIRED}" != "true" ]]; then
        echo "CONTAINER_REQUIRED is ${CONTAINER_REQUIRED}: this project is configured to need" >&2
        echo "no containers, so there is no development stack for this task to bring up." >&2
        exit 1
    fi

    local action="${1:-up}"
    case "${action}" in
        up|down|logs|reset) ;;
        *)
            echo "Unknown env action: ${action} (expected up, down, logs or reset)" >&2
            exit 1
            ;;
    esac

    local runtime
    runtime=$(require_container_runtime)
    if [[ "${runtime}" == "podman" ]]; then
        quadlet_env "${action}"
    else
        compose_env "${action}"
    fi
}

# Warm the local Maven repository ahead of a build, so a later task needs no
# network. dependency:resolve would walk the dependency tree alone and leave the
# build plugins out; go-offline takes both.
#
# It cannot be exhaustive: surefire picks its provider (surefire-junit-platform)
# off the test classpath as it runs, and no resolution goal sees that in advance.
# The first `test` downloads it. That is a slow first run, not a broken one.
task_deps() {
    setup
    run_mvn --batch-mode --no-transfer-progress dependency:go-offline
}

task_test() {
    setup
    # The pom's JaCoCo gate runs with these, and fails the task under its threshold.
    run_mvn test "$@"
}

task_run() {
    setup
    run_mvn spring-boot:run
}

# Same as `run`, but named for the Claude Code preview pane, which launches the
# app through this task (see .claude/launch.json) so the preview goes through
# run.sh — the pinned JDK and the bundle gotchas — like every other task, instead
# of invoking mvn directly.
task_preview() {
    task_run
}

# Unit tests plus the Playwright integration tests, against a production build.
#
# The IT profile is what puts the app in production mode; without it the ITs open a
# dev-mode page and find an empty screen. The bundles are cleared first because a
# dev.bundle left behind by `./run.sh run` makes the frontend build decide "a
# production mode bundle build is not needed" and the app then serves a bundle
# that fails to boot — the same trap `clean` exists for, and `mvn clean` alone
# does not clear it since the bundles live under src/.
task_verify() {
    setup
    clear_bundles
    local -a profile=()
    if [[ -n "${IT_PROFILE}" ]]; then
        profile+=("-P${IT_PROFILE}")
    fi
    run_mvn clean verify "${profile[@]}" "$@"
}

# Full production build.
task_package() {
    setup
    run_mvn clean package "$@"
}

task_clean() {
    setup
    clear_bundles
    "${MAVEN}" clean
}

# Describes only what this runner does, never what a project's build is configured to
# do with it: shared text that claims a coverage gate or a browser test is wrong for
# the first project that has neither, and a listed task that then refuses is worse
# than one that was never offered.
usage() {
    echo "Usage: ./run.sh <task>"
    echo
    echo "Tasks:"
    if [[ "${CONTAINER_REQUIRED}" == "true" ]]; then
        cat <<'EOF'
  env [act]  The whole development stack — quadlets under podman, compose under
             docker: up (default), down, logs, reset (throws the data away)
EOF
    fi
    cat <<'EOF'
  deps       Pre-download dependencies and build plugins, so a later task
             needs no network
  compile    Compile sources (triggers a devtools hot-restart of a running app)
  bundle     Clear cached frontend bundles + touch styles.css + recompile
             (use after changing a @CssImport themeFor / @JsModule)
  styles     Touch styles.css so the browser reloads @imported CSS partials
  test       Run the unit tests (mvn test)
  verify     Unit tests + integration tests (mvn clean verify)
  run        Start the app with spring-boot:run (dev mode)
  preview    Alias of run, launched by the Claude Code preview pane
  package    Full production build (mvn clean package)
  clean      mvn clean + remove cached bundles
  help       Show this message
EOF
    if declare -F project_usage >/dev/null; then
        project_usage
    fi
}

main() {
    local task="${1:-help}"
    case "${task}" in
        compile) task_compile ;;
        bundle)  task_bundle ;;
        styles)  task_styles ;;
        env)     task_env "${2:-up}" ;;
        deps)    task_deps ;;
        test)    task_test "${@:2}" ;;
        verify)  task_verify "${@:2}" ;;
        run)     task_run ;;
        preview) task_preview ;;
        package) task_package "${@:2}" ;;
        clean)   task_clean ;;
        help|-h|--help) usage ;;
        *)
            # Anything the project's own task file defines, dispatched by name. Only
            # a declared function matches, so a mistyped task is still a mistyped
            # task rather than a silent no-op.
            if declare -F "task_${task}" >/dev/null; then
                "task_${task}" "${@:2}"
            else
                echo "Unknown task: ${task}" >&2
                echo >&2
                usage >&2
                exit 1
            fi
            ;;
    esac
}

if [[ -n "${PROJECT_TASKS_FILE}" && -f "${PROJECT_TASKS_FILE}" ]]; then
    source "./${PROJECT_TASKS_FILE}"
fi

main "$@"
