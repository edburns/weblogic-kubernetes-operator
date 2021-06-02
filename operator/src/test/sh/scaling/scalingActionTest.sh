#!/usr/bin/env bash
# Copyright (c) 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

TEST_OPERATOR_ROOT=/tmp/test/weblogic-operator
setUp() {
  DONT_USE_JQ="true"
}

skip_if_jq_not_installed() {
  DONT_USE_JQ=
  if [ -x "$(command -v jq)" ]; then
    return
  fi
  startSkipping
}

oneTimeTearDown() {

  # Cleanup cmds-$$.py generated by scalingAction.sh
  rm -f cmds-$$.py

  # Cleanup scalingAction.log
  rm -f scalingAction.log
}

##### get_domain_api_version tests #####

test_get_domain_api_version() {
  CURL_FILE="apis1.json"

  result=$(get_domain_api_version)

  assertEquals "Did not return expected api version" 'v8' "${result}"  
}

test_get_domain_api_version_jq() {
  skip_if_jq_not_installed

  CURL_FILE="apis1.json"

  result=$(get_domain_api_version)

  assertEquals "Did not return expected api version" 'v8' "${result}"  
}

test_get_domain_api_version_without_weblogic_group() {
  CURL_FILE="apis2.json"

  result=$(get_domain_api_version)

  assertEquals "should have empty api version" '' "${result}"  
}

test_get_domain_api_version_without_weblogic_group_jq() {
  skip_if_jq_not_installed

  CURL_FILE="apis2.json"

  result=$(get_domain_api_version)

  assertEquals "should have empty api version" '' "${result}"  
}

##### get_operator_internal_rest_port tests #####

test_get_operator_internal_rest_port() {
  CURL_FILE="operator_status1.json"

  result=$(get_operator_internal_rest_port)

  assertEquals "Did not return expected rest port" '8082' "${result}"
}

test_get_operator_internal_rest_port_jq() {
  skip_if_jq_not_installed

  CURL_FILE="operator_status1.json"

  result=$(get_operator_internal_rest_port)

  assertEquals "Did not return expected rest port" '8082' "${result}"
}

test_get_operator_internal_rest_port_operator_notfound() {
  CURL_FILE="operator_404.json"

  result=$(get_operator_internal_rest_port)

  assertEquals "Did not return expected rest port" '' "${result}"
}

test_get_operator_internal_rest_port_operator_notfound_jq() {
  skip_if_jq_not_installed

  CURL_FILE="operator_404.json"

  result=$(get_operator_internal_rest_port)

  assertEquals "Did not return expected rest port" '' "${result}"
}

##### is_defined_in_clusters tests #####

test_is_defined_in_clusters() {

  DOMAIN_FILE="${testdir}/cluster1.json"

  wls_cluster_name='cluster-1'

  domain_json=`command cat ${DOMAIN_FILE}`

  result=$(is_defined_in_clusters "${domain_json}")

  assertEquals 'True' "${result}"
}

test_is_defined_in_clusters_jq() {
  skip_if_jq_not_installed

  DOMAIN_FILE="${testdir}/cluster1.json"

  wls_cluster_name='cluster-1'

  domain_json=`command cat ${DOMAIN_FILE}`

  result=$(is_defined_in_clusters "${domain_json}")

  assertEquals 'True' "${result}"
}

test_is_defined_in_clusters_2clusters() {

  DOMAIN_FILE="${testdir}/2clusters.json"

  domain_json=`command cat ${DOMAIN_FILE}`

  wls_cluster_name='cluster-1'
  result1=$(is_defined_in_clusters "${domain_json}")

  wls_cluster_name='cluster-2'
  result2=$(is_defined_in_clusters "${domain_json}")

  assertEquals 'True' "${result1}"
  assertEquals 'True' "${result2}"
}

test_is_defined_in_clusters_2clusters_jq() {
  skip_if_jq_not_installed

  DOMAIN_FILE="${testdir}/2clusters.json"

  domain_json=`command cat ${DOMAIN_FILE}`

  wls_cluster_name='cluster-1'
  result1=$(is_defined_in_clusters "${domain_json}")

  wls_cluster_name='cluster-2'
  result2=$(is_defined_in_clusters "${domain_json}")

  assertEquals 'True' "${result1}"
  assertEquals 'True' "${result2}"
}

test_is_defined_in_clusters_no_matching() {

  DOMAIN_FILE="${testdir}/cluster1.json"

  wls_cluster_name='no-such-cluster'

  domain_json=`command cat ${DOMAIN_FILE}`

  result=$(is_defined_in_clusters "${domain_json}")

  assertEquals 'False' "${result}"
}

test_is_defined_in_clusters_no_matching_jq() {
  skip_if_jq_not_installed

  DOMAIN_FILE="${testdir}/cluster1.json"

  wls_cluster_name='no-such-cluster'

  domain_json=`command cat ${DOMAIN_FILE}`

  result=$(is_defined_in_clusters "${domain_json}")

  assertEquals 'False' "${result}"
}

##### get_num_ms_in_cluster tests #####

test_get_num_ms_in_cluster() {

  DOMAIN_FILE="${testdir}/cluster1.json"

  wls_cluster_name='cluster-1'

  domain_json=`command cat ${DOMAIN_FILE}`

  result=$(get_num_ms_in_cluster "${domain_json}")

  assertEquals '1' "${result}"
}

test_get_num_ms_in_cluster_jq() {
  skip_if_jq_not_installed

  DOMAIN_FILE="${testdir}/cluster1.json"

  wls_cluster_name='cluster-1'

  domain_json=`command cat ${DOMAIN_FILE}`

  result=$(get_num_ms_in_cluster "${domain_json}")

  assertEquals '1' "${result}"
}

test_get_num_ms_in_cluster_2clusters() {

  DOMAIN_FILE="${testdir}/2clusters.json"

  domain_json=`command cat ${DOMAIN_FILE}`

  wls_cluster_name='cluster-1'
  result=$(get_num_ms_in_cluster "${domain_json}")
  assertEquals '1' "${result}"

  wls_cluster_name='cluster-2'
  result=$(get_num_ms_in_cluster "${domain_json}")
  assertEquals '2' "${result}"
}

test_get_num_ms_in_cluster_2clusters_jq() {
  skip_if_jq_not_installed

  DOMAIN_FILE="${testdir}/2clusters.json"

  domain_json=`command cat ${DOMAIN_FILE}`

  wls_cluster_name='cluster-1'
  result=$(get_num_ms_in_cluster "${domain_json}")
  assertEquals '1' "${result}"

  wls_cluster_name='cluster-2'
  result=$(get_num_ms_in_cluster "${domain_json}")
  assertEquals '2' "${result}"
}

test_get_num_ms_in_cluster_no_replics() {

  DOMAIN_FILE="${testdir}/cluster_noreplicas.json"

  wls_cluster_name='cluster-1'

  domain_json=`command cat ${DOMAIN_FILE}`

  result=$(get_num_ms_in_cluster "${domain_json}")

  assertEquals '0' "${result}"
}

test_get_num_ms_in_cluster_no_replicas_jq() {
  skip_if_jq_not_installed

  DOMAIN_FILE="${testdir}/cluster_noreplicas.json"

  wls_cluster_name='cluster-1'

  domain_json=`command cat ${DOMAIN_FILE}`

  result=$(get_num_ms_in_cluster "${domain_json}")

  assertEquals '0' "${result}"
}

test_get_num_ms_in_cluster_no_matching() {

  DOMAIN_FILE="${testdir}/cluster1.json"

  wls_cluster_name='no-such-cluster'

  domain_json=`command cat ${DOMAIN_FILE}`

  result=$(get_num_ms_in_cluster "${domain_json}")

  assertEquals '0' "${result}"
}

test_get_num_ms_in_cluster_no_matching_jq() {
  skip_if_jq_not_installed

  DOMAIN_FILE="${testdir}/cluster1.json"

  wls_cluster_name='no-such-cluster'

  domain_json=`command cat ${DOMAIN_FILE}`

  result=$(get_num_ms_in_cluster "${domain_json}")

  assertEquals '0' "${result}"
}

##### get_num_ms_domain_scope tests #####

test_get_num_ms_domain_scope() {
  
  DOMAIN_FILE="${testdir}/cluster1.json"
  
  wls_cluster_name='cluster-1'
  
  domain_json=`command cat ${DOMAIN_FILE}`
  
  result=$(get_num_ms_domain_scope "${domain_json}")
  
  assertEquals '2' "${result}"
}

test_get_num_ms_domain_scope_jq() {
  skip_if_jq_not_installed

  DOMAIN_FILE="${testdir}/cluster1.json"

  wls_cluster_name='cluster-1'

  domain_json=`command cat ${DOMAIN_FILE}`

  result=$(get_num_ms_domain_scope "${domain_json}")

  assertEquals '2' "${result}"
}

test_get_num_ms_domain_scope_no_replicas() {

  DOMAIN_FILE="${testdir}/cluster_noreplicas.json"

  wls_cluster_name='cluster-1'

  domain_json=`command cat ${DOMAIN_FILE}`

  result=$(get_num_ms_domain_scope "${domain_json}")

  assertEquals '0' "${result}"
}

test_get_num_ms_domain_scope_no_replicas_jq() {
  skip_if_jq_not_installed

  DOMAIN_FILE="${testdir}/cluster_noreplicas.json"

  wls_cluster_name='cluster-1'

  domain_json=`command cat ${DOMAIN_FILE}`

  result=$(get_num_ms_domain_scope "${domain_json}")

  assertEquals '0' "${result}"
}

##### get_replica_count tests #####

test_get_replica_count_from_cluster() {

  DOMAIN_FILE="${testdir}/cluster1.json"

  wls_cluster_name='cluster-1'

  domain_json=`command cat ${DOMAIN_FILE}`

  result=$(get_replica_count 'True' "${domain_json}")

  assertEquals '1' "${result}"
}

test_get_replica_count_from_cluster_jq() {
  skip_if_jq_not_installed

  DOMAIN_FILE="${testdir}/cluster1.json"

  wls_cluster_name='cluster-1'

  domain_json=`command cat ${DOMAIN_FILE}`

  result=$(get_replica_count 'True' "${domain_json}")

  assertEquals '1' "${result}"
}

test_get_replica_count_from_cluster_2clusters() {

  DOMAIN_FILE="${testdir}/2clusters.json"

  domain_json=`command cat ${DOMAIN_FILE}`

  wls_cluster_name='cluster-1'
  result=$(get_replica_count 'True' "${domain_json}")
  assertEquals '1' "${result}"

  wls_cluster_name='cluster-2'
  result=$(get_replica_count 'True' "${domain_json}")
  assertEquals '2' "${result}"
}

test_get_replica_count_from_cluster_2clusters_jq() {
  skip_if_jq_not_installed

  DOMAIN_FILE="${testdir}/2clusters.json"

  domain_json=`command cat ${DOMAIN_FILE}`

  wls_cluster_name='cluster-1'
  result=$(get_replica_count 'True' "${domain_json}")
  assertEquals '1' "${result}"

  wls_cluster_name='cluster-2'
  result=$(get_replica_count 'True' "${domain_json}")
  assertEquals '2' "${result}"
}

test_get_replica_count_from_domain() {

  DOMAIN_FILE="${testdir}/cluster1.json"

  wls_cluster_name='cluster-1'

  domain_json=`command cat ${DOMAIN_FILE}`

  result=$(get_replica_count 'False' "${domain_json}")

  assertEquals '2' "${result}"
}

test_get_replica_count_from_domain_jq() {
  skip_if_jq_not_installed

  DOMAIN_FILE="${testdir}/cluster1.json"

  wls_cluster_name='cluster-1'

  domain_json=`command cat ${DOMAIN_FILE}`

  result=$(get_replica_count 'False' "${domain_json}")

  assertEquals '2' "${result}"
}

test_get_replica_count_set_to_minReplicas() {

  DOMAIN_FILE="${testdir}/cluster_min3.json"

  wls_cluster_name='cluster-1'

  domain_json=`command cat ${DOMAIN_FILE}`

  result=$(get_replica_count 'False' "${domain_json}")

  assertEquals '3' "${result}"
}

test_get_replica_count_set_to_minReplicas_jq() {
  skip_if_jq_not_installed

  DOMAIN_FILE="${testdir}/cluster_min3.json"

  wls_cluster_name='cluster-1'

  domain_json=`command cat ${DOMAIN_FILE}`

  result=$(get_replica_count 'False' "${domain_json}")

  assertEquals '3' "${result}"
}

######################### Mocks for the tests ###############

cat() {
  case "$*" in
    "/var/run/secrets/kubernetes.io/serviceaccount/token")
      echo "sometoken"
      ;;
  *)
    command cat $*
  esac
}

curl() {
  cat ${testdir}/${CURL_FILE}
}

testdir="${0%/*}"

# shellcheck source=scripts/scaling/scalingAction.sh
. ${SCRIPTPATH}/scalingAction.sh --no_op

echo "Run tests"

# shellcheck source=target/classes/shunit/shunit2
. ${SHUNIT2_PATH}
