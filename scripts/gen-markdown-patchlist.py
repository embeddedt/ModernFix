#!/usr/bin/python3

import json
import subprocess
from contextlib import redirect_stdout

branch_name = subprocess.check_output(['git', 'branch', '--show-current']).decode("utf-8").strip()

with open('doc/generated/' + branch_name + '-Summary-of-Patches.md', 'w') as output_file:
    with redirect_stdout(output_file):
        with open('common/src/main/resources/assets/modernfix/lang/en_us.json') as lang_json:
            lang_obj = json.loads(lang_json.read())
            option_names = []
            for key, value in lang_obj.items():
                if key.startswith("modernfix.option.mixin."):
                    option_names.append(key.replace("modernfix.option.", ""))
            option_names.sort()
            print()
            for option in option_names:
                option_description = lang_obj.get("modernfix.option." + option)
                option_friendly_name = lang_obj.get("modernfix.option.name." + option)
                print(f"### `{option}`")
                print()
                if option_description is not None:
                    print(option_description)
                    print("")
