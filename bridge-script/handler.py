#!/usr/bin/env python
import os
import sys
import re
import json
import requests

class Member:
    def __init__(self, name, ip, role, tags):
        self.name = name
        self.ip   = ip
        self.role = role
        self.tags = tags

    def to_json(self):
        return json.dumps({"name": self.name, "address": self.ip, "role": self.role, "tags": self.tags})


if "SERF_EVENT" not in os.environ:
    sys.exit("Serf event not defined")

event = os.environ['SERF_EVENT']
server_address = os.environ.get("SERVER_BIND", "localhost")
server_port = int(os.environ.get("SERVER_PORT", "8888"))

for line in sys.stdin:
    fields = re.split('[\t ]', line.strip())

    node_name = fields[0]
    node_ip = fields[1]
    node_role = fields[2]
    tags_str = fields[3]

    # parse tags
    tags = {}
    dc = None
    for tag_str in tags_str.split(","):
        tag_name, tag_value = tag_str.split("=")
        if tag_name == "dc":
            dc = tag_value
        tags[tag_name] = tag_value

    member = Member(node_name, node_ip, node_role, tags)
    base_url = "http://%s:%d/dc/%s/" % (server_address, server_port, dc)

    r = None
    headers = {"content-type": "application/json"}
    if event == "member-join":
        base_url += "members"
        r = requests.post(base_url, headers = headers, data = member.to_json())

    elif event == "member-fail":
        base_url += "failures"
        r = requests.post(base_url, headers = headers, data = member.to_json())

    else:
        base_url += "members/%s" % node_name
        if event == "member-update":
            r = requests.put(base_url, headers = headers, data = member.to_json())

        elif event == "member-leave":
            r = requests.delete(base_url)

        elif event == "member-reap":
            r = requests.delete(base_url, params = {"reap": "true"})

        else:
            print "Ignoring event"

    #if r is not None:
    #    print "Response: " + str(r.status_code) + ", " + r.text