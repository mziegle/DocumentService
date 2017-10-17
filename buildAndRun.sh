#!/usr/bin/env bash

docker build --rm -t document_service:build .

docker create --name document_service_build document_service:build

rm -rf run/target

docker cp document_service_build:/build/target run
docker cp document_service_build:/build/src/main/resources/bill.pdf run

docker build --rm -t document_service:run run

docker rm -f document_service_build

docker run \
    -p 50033:50033 \
    -e POLICY_SERVICE_PORT=50032 \
    -e DOCUMENT_SERVICE_PORT=50033 \
    -e POLICY_SERVICE_HOST=docker.for.mac.localhost \
    -i -t --security-opt=seccomp:unconfined \
    --rm document_service:run