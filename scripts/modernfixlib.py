
import os
import re

def get_valid_mixin_options():
    all_mixin_options = set()
    # gather all mixins in mixin folders
    for platform in [ "common", "fabric", "forge" ]:
        base_path = f"{platform}/src/main/java/org/embeddedt/modernfix/{platform}/mixin"
        for root, dirs, files in os.walk(base_path):
            for file in files:
                if file.endswith(".java"):
                    mixin_name = root.replace(base_path, "").replace(os.path.sep, ".")
                    if mixin_name.startswith("."):
                        mixin_name = mixin_name[1:]
                    all_mixin_options.add("mixin." + mixin_name)
    # gather any mixin strings referenced in ModernFixEarlyConfig
    with open('common/src/main/java/org/embeddedt/modernfix/core/config/ModernFixEarlyConfig.java') as config_java:
        config_str = config_java.read()
        for option in re.findall(r"\"(mixin(?:\.[a-z_]+)+)\"", config_str):
            all_mixin_options.add(option)
    return all_mixin_options