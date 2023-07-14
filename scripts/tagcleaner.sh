#!/bin/bash

# if current tag is 5.2.3+1.16.5, strip all other 5.2.3+* tags
current_tag=$(git describe --tags --abbrev=0)
current_tag_prefix=$(echo $current_tag | sed 's/+.*/+/g')

git tag | while read -r other_tag; do
  if [ "x$other_tag" != "x$current_tag" ] ; then
    if [[ $other_tag == ${current_tag_prefix}* ]]; then
      git tag -d $other_tag
    fi
  fi
done