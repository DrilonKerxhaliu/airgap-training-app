#!/bin/sh
set -e

echo "=== Airgap Entrypoint Starting ==="
mkdir -p /opt/airgap/data
chown -R airgap:airgap /opt/airgap 2>/dev/null || true
echo "Running as user:"
whoami
id
echo "Listing /opt/airgap:"
ls -la /opt/airgap
echo "=== Starting application ==="
exec su -s /bin/sh airgap -c "java -jar /opt/airgap/app.jar"