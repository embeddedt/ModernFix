#!/bin/bash
set -e

WORK_DIR=`mktemp -d`

# deletes the temp directory
function cleanup {
  cd $HOME
  rm -rf "$WORK_DIR"
}

trap cleanup EXIT

# clone ModernFix repo

echo "downloading temporary modernfix..."
cd $WORK_DIR
git clone https://github.com/embeddedt/ModernFix mfix &>/dev/null
cd mfix

# gather version list
readarray -t all_versions < <(git ls-remote --heads origin | awk '{print $2}' | sed 's:.*/::' | sort -V)

do_release() {
    echo "will now make release for $1"
    git checkout $1 &>/dev/null || git checkout -b $1 &>/dev/null
    echo "we think the current tag is $(git describe --tags --abbrev=0)"
    echo "the current commit head is $(git rev-parse HEAD)"
    read -p "new tag name: " tag_name
    gh release create $tag_name --target $1 --title "$tag_name" --notes ""
}

for version in "${all_versions[@]}"; do
    read -r -p "Make release on ${version} branch? [y/N] " response
    case "$response" in
        [yY][eE][sS]|[yY])
            do_release $version
            ;;
        *)
            ;;
    esac
done
