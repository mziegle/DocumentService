#!/usr/bin/env bash

docker build --rm -t document_service:build .

docker create --name document_service_build document_service:build

rm -rf run/target

docker cp document_service_build:/build/target run
docker cp document_service_build:/build/src/main/resources/bill.pdf run

docker build --rm -t mziegle1/document_service:run run

docker rm -f document_service_build

# Remove intermediate containers which have appeared during the build process
docker rmi $(docker images -f "dangling=true" -q)

docker run \
    -p 40002:40002 \
    -e POLICY_SERVICE_PORT=40001 \
    -e DOCUMENT_SERVICE_PORT=40002 \
    -e POLICY_SERVICE_HOST=docker.for.mac.localhost \
    -i -t --security-opt=seccomp:unconfined \
    --rm mziegle1/document_service:run