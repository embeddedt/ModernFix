#!/bin/bash
set -e

WORK_DIR=`mktemp -d`

# deletes the temp directory
function cleanup {
  cd $HOME
  rm -rf "$WORK_DIR"
}

trap cleanup EXIT
trap "exit" INT

# clone ModernFix repo

echo "downloading temporary modernfix..."
cd $WORK_DIR
git clone https://github.com/embeddedt/ModernFix mfix &>/dev/null
cd mfix

# gather version list
readarray -t all_versions < <(git ls-remote --heads origin | awk '{print $2}' | sed 's:.*/::' | sort -V)
echo "found versions: ${all_versions[@]}"

# checkout base version
git checkout -b propagations/${all_versions[0]} origin/${all_versions[0]} &>/dev/null

our_version=${all_versions[0]}
restore_version=$our_version

for version in "${all_versions[@]}"; do
    if ! { echo "$version"; echo "$our_version"; } | sort --version-sort --check &>/dev/null; then
        echo -n "merging $our_version into ${version}... "
        git checkout -b propagations/$version origin/$version &>/dev/null
        if ! git merge --no-commit propagations/$our_version &>/dev/null; then
            merge_failed=yes
            echo "failed, this merge must be done manually using the provided shell"
        else
            merge_failed=no
            echo -n "done"
        fi
        if [ "x$merge_failed" == "xyes" ]; then
            git status
            bash
            echo -e $'Press any key to commit or Ctrl+C to completely abort...\n'
            read -rs -n1
        fi
        if (git add . && git commit -m "Merge $our_version into $version" &>/dev/null); then
            git push -u origin propagations/$version:$version &>/dev/null
        else
            echo -e "\b\b\b\bnothing to merge"
        fi
        our_version=$version
    fi
done
