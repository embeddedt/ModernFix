#!/usr/bin/python3

import re

with open('Summary-of-Patches.md', 'r') as file:
    data = file.read().rstrip()

matches = re.findall(r"## `(.*?)`((?:(?!\n#).)*)", data, re.DOTALL)

for m in matches:
   option_name = m[0].strip()
   option_desc = m[1].strip().replace("\n", "\\n").replace('"', '\\"')
   print(f"    \"modernfix.option.mixin.{m[0].strip()}\": \"{option_desc}\",")
