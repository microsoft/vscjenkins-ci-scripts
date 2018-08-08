FROM jenkins/jnlp-slave
MAINTAINER Raphael Yu <xianyu@microsoft.com>

USER root
RUN apt-get update && apt-get install -y --no-install-recommends \
        expect \
        apt-transport-https
RUN echo "deb https://packages.microsoft.com/repos/azure-cli/ stretch main" | tee /etc/apt/sources.list.d/azure-cli.list
RUN curl -L https://packages.microsoft.com/keys/microsoft.asc | apt-key add -
RUN apt-get update && apt-get install azure-cli \
        && rm -rf /var/lib/apt/lists/*
USER jenkins
