#!/usr/bin/env python

import json
import os.path
import sys

hcl_file = sys.argv[1]

with open(hcl_file, 'r') as hcl:
    with open(os.path.splitext(hcl_file)[0] + ".json", 'w') as jsn:
        data = {'policy': hcl.read()}
        json.dump(data, jsn)
