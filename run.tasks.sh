# Tasks Whichday has that the shared runner does not. Sourced by run.sh after its
# helpers, so these call setup and run_mvn the way its own tasks do.

# Matches the WHICHDAY_DATA_DIR default in application.properties.
readonly DATA_DIR="data"

# Throw away the local database. The app creates an empty one on the next start.
#
# Deliberately not part of `clean`: a build task that quietly deleted your polls
# would be docs/issues/0001 with a different trigger. It needs neither the JDK nor
# mvn, which is why it is the one task that calls no setup.
task_resetdb() {
    rm -rf "${DATA_DIR}"
    echo "Removed ${DATA_DIR}/"
}

project_usage() {
    cat <<'USAGE'
  resetdb    Delete the local database (the app creates an empty one next start)
USAGE
}
