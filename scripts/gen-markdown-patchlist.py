#!/usr/bin/python3

import json, os, subprocess, sys
# to import other scripts in same folder
sys.path.append(os.path.dirname(os.path.realpath(__file__)))

from contextlib import redirect_stdout
from modernfixlib import get_valid_mixin_options

branch_name = subprocess.check_output(['git', 'branch', '--show-current']).decode("utf-8").strip()

with open('doc/generated/' + branch_name + '-Summary-of-Patches.md', 'w') as output_file:
    all_current_mixin_options = get_valid_mixin_options()
    options_missing_descriptions = set()
    with redirect_stdout(output_file):
        with open('common/src/main/resources/assets/modernfix/lang/en_us.json') as lang_json:
            lang_obj = json.loads(lang_json.read())
            option_names = set()
            for key, value in lang_obj.items():
                if key.startswith("modernfix.option.mixin."):
                    option_names.add(key.replace("modernfix.option.", ""))
            option_names_sorted = list(option_names)
            option_names_sorted.sort()
            print()
            for option in option_names_sorted:
                if option not in all_current_mixin_options:
                    continue
                option_description = lang_obj.get("modernfix.option." + option)
                option_friendly_name = lang_obj.get("modernfix.option.name." + option)
                print(f"### `{option}`")
                print()
                if option_description is not None:
                    print(option_description)
                    print("")
                else:
                    options_missing_descriptions.add(option)
            options_missing_descriptions.update(all_current_mixin_options.difference(option_names))

    # sort the list of missing descriptions and print them out if there are any
    missing_descriptions_list = list(options_missing_descriptions)
    missing_descriptions_list.sort()
    num_missing = len(missing_descriptions_list)
    if num_missing > 0:
        print(f"Missing {num_missing} descriptions:")
        for option in missing_descriptions_list:
            print(f"  - {option}")