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

last_released_version=""
do_release() {
    echo "will now make release for $1"
    git checkout $1 &>/dev/null || git checkout -b $1 &>/dev/null
    current_tag=$(git describe --tags --abbrev=0)
    echo "we think the current tag is $current_tag"
    echo "the current commit head is $(git rev-parse HEAD)"
    old_version_specifier=$(echo $current_tag | awk -F+ '{print $2}')
    read -e -p "new tag name (${old_version_specifier}): " -i "${last_released_version}" tag_name
    if [[ $tag_name != *"+"* ]]; then
        tag_name=${tag_name}+${old_version_specifier}
    fi
    last_released_version=$(echo $tag_name | awk -F+ '{print $1}')
    git tag -a $tag_name -m "$tag_name"
    git push --tags
    gh release create $tag_name --target $1 --title "$tag_name" --notes ""
    # now delete local tag to prevent messing up the detected tag for the next version
    git tag -d $tag_name &>/dev/null
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
