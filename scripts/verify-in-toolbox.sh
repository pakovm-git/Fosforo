#!/usr/bin/env bash
set -euo pipefail
project_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
toolbox run --container "${BFI_TOOLBOX_CONTAINER:-android-bfi}" bash -c '
    set -euo pipefail
    if [[ -f "$HOME/Android/bfi-env.sh" ]]; then
        source "$HOME/Android/bfi-env.sh"
    fi
    cd "$1"
    ./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug
' bash "$project_root"
