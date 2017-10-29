FROM jenkinsci/jnlp-slave
MAINTAINER Raphael Yu <xianyu@microsoft.com>

USER root
RUN apt-get update && apt-get install -y --no-install-recommends \
        expect \
        apt-transport-https
RUN echo "deb https://packages.microsoft.com/repos/azure-cli/ wheezy main" | tee /etc/apt/sources.list.d/azure-cli.list
RUN apt-key adv --keyserver packages.microsoft.com --recv-keys 52E16F86FEE04B979B07E28DB02C46DF417A0893
RUN apt-get update && apt-get install azure-cli \
        && rm -rf /var/lib/apt/lists/*
# Fix the missing libssl 1.0.0
RUN wget "http://security.debian.org/debian-security/pool/updates/main/o/openssl/libssl1.0.0_1.0.1t-1+deb7u2_amd64.deb" \
        && dpkg -i libssl1.0.0_1.0.1t-1+deb7u2_amd64.deb\
        && rm libssl1.0.0_1.0.1t-1+deb7u2_amd64.deb
USER jenkins
