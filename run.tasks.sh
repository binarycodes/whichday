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

# The README opens with a GIF of the create flow, and the flow is what makes it stale.
readonly GIF_RECORDER="tools/record-readme-gif.py"

# Re-record that GIF against a running deployment.
#
# Calls no setup: it needs neither the JDK nor mvn, only a Chromium and ImageMagick,
# which the script itself checks for and names if they are missing.
#
# The URL is an argument rather than a default because there is nothing sensible to
# default it to — the share screen puts the voting link on screen, so a recording of
# localhost would show the README's readers a link they cannot follow. It calls a real
# poll on whatever it is pointed at.
task_readmegif() {
    if [[ $# -eq 0 ]]; then
        echo "Usage: ./run.sh readmegif <url> [output.gif]" >&2
        echo "  e.g. ./run.sh readmegif https://whichday.example.org/" >&2
        exit 1
    fi
    python3 "${GIF_RECORDER}" "$@"
}

project_usage() {
    cat <<'USAGE'
  resetdb    Delete the local database (the app creates an empty one next start)
  readmegif  Re-record the README's GIF against a deployment (takes its URL)
USAGE
}
