#!/bin/sh
set-e
mkdir-p /opt/airgap/data
chown-R airgap:airgap /opt/airgap
exec su-s /bin/sh airgap -c"java -jar /opt/airgap/app.jar"