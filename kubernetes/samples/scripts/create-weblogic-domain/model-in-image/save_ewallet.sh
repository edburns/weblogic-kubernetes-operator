#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# Usage: save_ewallet.sh
#
#

# TBD 
#   - refactor - and move wallet to a dedicated secret
#   - advance script to have both save and restore options (allow specifying both)
#   - for save option,

set -eu
SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
echo "@@ Info: Running '$(basename "$0")'."

DOMAIN_UID=${DOMAIN_UID:-sample-domain1}
DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-${DOMAIN_UID}-ns}

kubectl -n ${DOMAIN_NAMESPACE} get configmap ${DOMAIN_UID}-weblogic-domain-introspect-cm \
      -o jsonpath='{.data.ewallet\.p12}' > ewallet.p12

$SCRIPTDIR/create_secret.sh -s ${DOMAIN_UID}-opss-walletfile-secret \
  -fk WalletFile ewallet.p12
