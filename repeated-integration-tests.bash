#!/bin/bash

set -o pipefail

failures=0
numeric='^[0-9]+$'
if [[ "$1" =~ $numeric ]]; then
    runs="$1"
else
    runs=1
fi

POD_NAME="$(xpath -q -e 'project/properties/cryostat.itest.podName/text()' pom.xml)"
CONTAINER_NAME="$(xpath -q -e 'project/properties/cryostat.itest.containerName/text()' pom.xml)"

function cleanup() {
    if podman pod exists "${POD_NAME}"; then
        mvn exec:exec@destroy-pod
    fi
}
trap cleanup EXIT
cleanup

STARTFLAGS=(
    "exec:exec@create-pod"
    "exec:exec@start-jfr-datasource"
    "exec:exec@start-grafana"
    "exec:exec@start-cryostat"
    "exec:exec@wait-for-cryostat"
    "exec:exec@wait-for-jfr-datasource"
    "exec:exec@wait-for-grafana"
    "failsafe:integration-test"
    "failsafe:verify"
)

STOPFLAGS=(
    "exec:exec@destroy-pod"
)

if command -v ansi2txt >/dev/null; then
    STARTFLAGS+=("-Dstyle.color=always")
    STOPFLAGS+=("-Dstyle.color=always")
    PIPECLEANER=ansi2txt
else
    PIPECLEANER=cat
fi

DIR="$(dirname "$(readlink -f "$0")")"

runcount=0
while [ "${runcount}" -lt "${runs}" ]; do
    timestamp="$(date -Iminutes)"
    client_logfile="$DIR/target/${POD_NAME}-${timestamp}.client.log"
    server_logfile="$DIR/target/${POD_NAME}-${timestamp}.server.log"
    mvn "${STARTFLAGS[@]}" |& tee -a >($PIPECLEANER >> "${client_logfile}")
    if [ "$?" -ne 0 ]; then
        failures=$((failures+1))
    fi
    runcount=$((runcount+1))
    podman pod logs -c "${CONTAINER_NAME}" "${POD_NAME}" &>> "${server_logfile}"
    mvn "${STOPFLAGS[@]}" |& tee -a >($PIPECLEANER >> "${client_logfile}")
done

echo
echo "########################"
echo "Test runs completed"
echo "Failures: ${failures}"
echo "Runs: ${runcount}/${runs}"

exit ${failures}
