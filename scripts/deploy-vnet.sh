#!/bin/bash

function throw_if_empty() {
  local name="$1"
  local value="$2"
  if [ -z "$value" ]; then
    >&2 echo "Parameter '$name' cannot be empty."
    exit -1
  fi
}

tenant_id="72f988bf-86f1-41af-91ab-2d7cd011db47"
region="eastus"
keep_alive_hours="48"

while [[ $# > 0 ]]
do
  key="$1"
  shift
  case $key in
    --app_id|-ai)
      app_id="$1"
      shift
      ;;
    --app_key|-ak)
      app_key="$1"
      shift
      ;;
    --tenant_id|-ti)
      tenant_id="$1"
      shift
      ;;
    --vnet_name|-vn)
      vnet_name="$1"
      shift
      ;;
    --resource_group|-rg)
      resource_group="$1"
      shift
      ;;
    --vnet_prefix|-vp)
      vnet_prefix="$1"
      shift
      ;;
    --subnet_name|-sn)
      subnet_name="$1"
      shift
      ;;
    --subnet_prefix|-sp)
      subnet_prefix="$1"
      shift
      ;;
    --region|-r)
      region="$1"
      shift
      ;;
    *)
      >&2 echo "ERROR: Unknown argument '$key' to script '$0'"
      exit -1
  esac
done

throw_if_empty --app_id $app_id
throw_if_empty --app_key $app_key
throw_if_empty --tenant_id $tenant_id
throw_if_empty --resource_group $resource_group
throw_if_empty --vnet_name $vnet_name
throw_if_empty --vnet_prefix $vnet_prefix
throw_if_empty --subnet_name $subnet_name
throw_if_empty --subnet_prefix $subnet_prefix
throw_if_empty --region $region

>&2 az login --service-principal -u $app_id -p $app_key --tenant $tenant_id
>&2 echo "Creating resource group '$resource_group'..."
>&2 az group create -n $resource_group -l $region --tags "CleanTime=$(date -d "+${keep_alive_hours} hours" +%s)"
>&2 echo "Creating virtual network '$vnet_name'..."
deployment_data=$(az network vnet create -n $vnet_name -g $resource_group --address-prefixes $vnet_prefix --subnet-name $subnet_name --subnet-prefix $subnet_prefix)
>&2 echo "$deployment_data"

provisioning_state=$(echo "$deployment_data" | python -c "import json, sys; data=json.load(sys.stdin);print data['publicIp']['provisioningState']")
if [ "$provisioning_state" != "Succeeded" ]; then
    >&2 echo "Deployment failed."
    exit -1
fi