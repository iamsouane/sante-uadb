#!/usr/bin/env python3
"""Transforme chaque test JUnit en échec en annotation GitHub Actions (lisible sans ouvrir les journaux)."""
import glob
import xml.etree.ElementTree as ET


def echapper(texte):
    return texte.replace("%", "%25").replace("\r", "").replace("\n", "%0A")


echecs = 0
for rapport in glob.glob("*/target/surefire-reports/TEST-*.xml"):
    for cas in ET.parse(rapport).getroot().iter("testcase"):
        for probleme in list(cas.findall("failure")) + list(cas.findall("error")):
            echecs += 1
            detail = (probleme.get("message") or "") + "\n" + (probleme.text or "")[:1500]
            print(f"::error title={cas.get('classname')}.{cas.get('name')}::{echapper(detail.strip())}")
print(f"{echecs} test(s) en échec")
